"""Launch isolated H2 service processes and verify gateway business flows. No real SMS/payment calls."""
from pathlib import Path
import os,subprocess,socket,time,json,uuid,urllib.request,urllib.error,shutil

ROOT=Path(__file__).resolve().parents[1]
def main():
    java=Path(os.environ.get('JAVA_HOME',''))/'bin'/('java.exe' if os.name=='nt' else 'java')
    if not java.is_file():raise SystemExit('Set JAVA_HOME to JDK 21+')
    runtime=ROOT/'.runtime'/('smoke-'+uuid.uuid4().hex[:8]);runtime.mkdir(parents=True)
    names=['gateway','member-service','identity-service','catalog-service','trade-service']
    ports={};reservations=[]
    for name in names:
        sock=socket.socket();sock.bind(('127.0.0.1',0));ports[name]=sock.getsockname()[1];reservations.append(sock)
    for sock in reservations:sock.close()
    processes=[];logs=[]
    env=os.environ.copy()
    env.update({'VIRIDAC_SMS_ENABLED':'false','VIRIDAC_AMAP_ENABLED':'false','VIRIDAC_AI_ENABLED':'false',
        'VIRIDAC_SANDBOX_PAYMENT_ENABLED':'false','VIRIDAC_ALLOW_DEV_DEPOSIT':'false','VIRIDAC_SANDBOX_CONFIRM_ENABLED':'false',
        'VIRIDAC_ORDER_TIMEOUT_TASK_ENABLED':'false','VIRIDAC_DB_PASSWORD':'','DB_PASSWORD':'',
        'IDENTITY_URL':f"http://127.0.0.1:{ports['identity-service']}",
        'CATALOG_URL':f"http://127.0.0.1:{ports['catalog-service']}",
        'TRADE_URL':f"http://127.0.0.1:{ports['trade-service']}",
        'MEMBER_URL':f"http://127.0.0.1:{ports['member-service']}",
        'FRONTEND_PUBLIC_ORIGIN':'http://127.0.0.1:4206'})
    def call(path,method='GET',body=None,token=None,key=None,expected=200,port=None):
        headers={'Accept':'application/json'}
        if body is not None:headers['Content-Type']='application/json'
        if token:headers['Authorization']='Bearer '+token
        if key:headers['Idempotency-Key']=key
        request=urllib.request.Request(f"http://127.0.0.1:{port or ports['gateway']}"+path,
            data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
        try:response=urllib.request.urlopen(request,timeout=6)
        except urllib.error.HTTPError as error:response=error
        data=response.read().decode()
        assert response.status==expected,(path,response.status,data[:500])
        return json.loads(data) if data else None
    try:
        for name in names:
            module=ROOT/('gateway' if name=='gateway' else 'services/'+name)
            jars=[j for j in (module/'target').glob('*.jar') if not j.name.endswith('.original')]
            if len(jars)!=1:raise RuntimeError('Run backend/run.ps1 -Package first: '+name)
            log=open(runtime/(name+'.log'),'w',encoding='utf-8');logs.append(log)
            args=[str(java),'-Xmx256m','-jar',str(jars[0]),'--spring.profiles.active=local',f'--server.port={ports[name]}']
            if name!='gateway':args+=['--spring.datasource.url=jdbc:h2:mem:smoke;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1']
            process=subprocess.Popen(args,cwd=runtime,env=env,stdout=log,stderr=subprocess.STDOUT,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
            processes.append(process)
        for name,process in zip(names,processes):
            deadline=time.monotonic()+90
            while True:
                if process.poll() is not None:raise RuntimeError(name+' exited; see '+str(runtime/(name+'.log')))
                try:call('/actuator/health/readiness',port=ports[name]);break
                except (OSError,AssertionError):
                    if time.monotonic()>deadline:raise RuntimeError(name+' did not become ready; see '+str(runtime))
                    time.sleep(.5)
            print('READY '+name,flush=True)
        assert call('/api/health')['service']=='gateway'
        assert call('/api/lectus-local/config')['data']['scanOrigin']=='http://127.0.0.1:4206'
        call('/api/cart',expected=401)
        session=call('/api/auth/register','POST',{'email':uuid.uuid4().hex+'@example.test','password':'Smoke-safe-123!','displayName':'Smoke'},expected=201)
        token=session['token'];assert call('/api/auth/me',token=token)['id']==session['user']['id']
        products=call('/api/products');sku=products['items'][0]['skus'][0]['id']
        address=call('/api/legacy/addresses','POST',{'recipient':'测试用户','phone':'13800000000','region':'浙江省 杭州市','detail':'测试路100号','isDefault':True},token,expected=201)
        call('/api/cart/items','PUT',{'skuId':sku,'quantity':1,'checked':True},token)
        orderBody={'addressId':address['id'],'items':[{'skuId':sku,'quantity':1}],'fromCart':True}
        order=call('/api/orders','POST',orderBody,token,'smoke-key-123456',201)
        assert call('/api/orders','POST',orderBody,token,'smoke-key-123456',201)['id']==order['id']
        assert call('/api/orders/'+order['id']+'/cancel','POST',token=token)['status']=='CANCELLED'
        assert call('/api/orders/'+order['id']+'/cancel','POST',token=token)['status']=='CANCELLED'
        call('/api/auth/logout','POST',token=token,expected=204)
        call('/api/cart',token=token,expected=401)
        meta=call('/api/catalog/meta');assert meta['total']==0
        member=call('/api/member/register','POST',{'username':'smoke_'+uuid.uuid4().hex[:10],'password':'Smoke-safe-123!','nickname':'测试会员'})
        assert member['success'];mt=member['data']['token']
        assert call('/api/member/me',token=mt)['success']
        assert call('/api/addresses',token=mt)['success']
        assert call('/api/lectus-demo/orders',token=mt)['data']==[]
        assert call('/api/member-platform/member/me',token=mt)['success']
        call('/api/unknown',expected=404)
        # Auth outages fail closed in the trade service.
        processes[2].terminate();processes[2].wait(timeout=15)
        call('/api/cart',token='a'*43,expected=503)
        print('PASS: five-process gateway/auth/trade/catalog/member smoke; identity outage returns 503',flush=True)
        print('Logs: '+str(runtime),flush=True)
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                process.terminate()
                try:process.wait(timeout=15)
                except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
        for log in logs:log.close()
if __name__=='__main__':main()

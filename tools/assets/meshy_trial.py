"""Single-colorway Meshy trial. Default plan is offline; never binds catalog models.

Official contract/pricing checked at docs.meshy.cn/api on 2026-10-18.
Only submit creates a task; it requires an explicit credit ceiling and upload flag.
POST requests are never retried: an uncertain response must be reconciled in Meshy.
"""
import argparse
import base64
from datetime import datetime, timezone
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlparse
from urllib.request import Request, HTTPRedirectHandler, build_opener

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE = ROOT / '.local/work/3d-preview/source-views/ASICS-KAYANO14-1203A537-301'
DEFAULT_PLAN = ROOT / '.local/work/3d-preview/meshy-trial/plan.json'
KEY_FILE = ROOT / '.runtime/meshy.json'
BASES = {'cn':'https://api.meshy.cn', 'global':'https://api.meshy.ai'}
ENDPOINT = '/openapi/v1/multi-image-to-3d'


class TrialError(Exception):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def now():
    return datetime.now(timezone.utc).isoformat()


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + '.tmp')
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
    os.replace(temp, path)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def estimate(options):
    # Fixed model and options, not the changing `latest` alias.
    if options.get('ai_model') != 'meshy-7.1':
        raise TrialError('This trial only estimates the documented meshy-7.1 model.')
    if options.get('texture_resolution') != '4k' or options.get('should_texture') is not True:
        raise TrialError('Trial pricing is validated only for 4K textured output.')
    geometry = options.get('geometry_resolution')
    if geometry not in ('standard', '2k'):
        raise TrialError('Unsupported geometry resolution.')
    expected = {'ai_model','geometry_resolution','should_texture','texture_resolution',
        'should_remesh','image_enhancement','target_formats','auto_size'}
    if set(options) != expected or options['target_formats'] != ['glb']:
        raise TrialError('Unexpected options: create a new supported plan.')
    if options['should_remesh'] or options['auto_size'] or options['image_enhancement']:
        raise TrialError('This trial requires original geometry, original images and no size inference.')
    return 30 + (5 if geometry == '2k' else 0)


def create_plan(source, output, geometry):
    requirements = read_json(ROOT / '.local/work/catalog-import/shoe-model-requirements.json')
    product = next(p for p in requirements if p['productId'] == 'SPU-234E824E28E2')
    color = next(c for c in product['colorways'] if c['colorwayId'] == 'CLR-774E8ADF3AC0')
    source = Path(source).resolve()
    provenance = read_json(source / 'source-manifest.json')
    # 02 and 04 are two-shoe compositions. Use only the four single-shoe views.
    images = []
    for number, role in [(1,'primary side'),(3,'opposite side'),(5,'upper'),(6,'sole')]:
        path = source / f'view-{number:02d}.jpg'
        entry = next(v for v in provenance['views'] if v['position'] == number)
        if entry['status'] != 'ok' or not path.is_file() or path.stat().st_size > 20*1024*1024:
            raise TrialError('A source image is missing, unverified, or exceeds the API limit.')
        images.append({'path':str(path),'role':role,'sha256':digest(path),'source':entry['source']})
    options = {'ai_model':'meshy-7.1','geometry_resolution':geometry,'should_texture':True,
        'texture_resolution':'4k','should_remesh':False,'image_enhancement':False,
        'target_formats':['glb'],'auto_size':False}
    plan = {'schema':1,'provider':'meshy','productId':product['productId'],
        'colorwayId':color['colorwayId'],'name':product['name'],'colorway':color['name'],
        'images':images,'options':options,'estimatedCredits':estimate(options),
        'pricingSource':'https://docs.meshy.cn/api/pricing','pricingChecked':'2026-10-18',
        'reviewed':False,'publishable':False,'notice':'Approximation; size, fit and hidden geometry unverified. No AR try-on.'}
    if Path(output).exists():
        raise TrialError('Plan already exists; use a different --output to preserve this trial.')
    write_json(output, plan)
    return plan


def validate_plan(plan):
    if plan.get('schema') != 1 or plan.get('provider') != 'meshy':
        raise TrialError('Unsupported trial plan.')
    if not re.fullmatch(r'SPU-[A-F0-9]{12}',plan.get('productId','')) or not re.fullmatch(r'CLR-[A-F0-9]{12}',plan.get('colorwayId','')):
        raise TrialError('Missing exact product/colorway mapping.')
    if not 1 <= len(plan.get('images',[])) <= 4:
        raise TrialError('Meshy requires one to four photos.')
    cost = estimate(plan['options'])
    if plan.get('estimatedCredits') != cost:
        raise TrialError('Plan cost changed; regenerate the plan.')
    for image in plan['images']:
        path = Path(image['path'])
        if path.suffix.lower() not in ('.jpg','.jpeg','.png','.webp') or not path.is_file():
            raise TrialError('Source image missing or unsupported.')
        if path.stat().st_size > 20*1024*1024 or digest(path) != image['sha256']:
            raise TrialError('Source bytes changed; regenerate and review the plan.')
    return cost


def load_key():
    key = os.environ.get('MESHY_API_KEY')
    if not key and KEY_FILE.is_file():
        key = read_json(KEY_FILE).get('api_key')
    if not isinstance(key,str) or not key.startswith('msy_') or any(c.isspace() for c in key):
        raise TrialError('No valid local Meshy key. Run: python tools/assets/meshy_trial.py configure')
    return key


class Meshy:
    def __init__(self, key, region='cn'):
        self.key, self.base = key, BASES[region]
        self.opener = build_opener(NoRedirect())

    def request(self, method, path, payload=None):
        body = json.dumps(payload).encode() if payload is not None else None
        req = Request(self.base+path,data=body,method=method,headers={
            'Authorization':'Bearer '+self.key,'Content-Type':'application/json',
            'Accept':'application/json','User-Agent':'Lectus-local-trial/1'})
        try:
            with self.opener.open(req,timeout=45) as response:
                raw = response.read(2*1024*1024+1)
            if len(raw)>2*1024*1024:
                raise TrialError('API response exceeded local limit.')
            return json.loads(raw)
        except HTTPError as error:
            # Never print raw bodies/headers: they can contain signed URLs or keys.
            hints={401:'Check API key and selected region.',402:'Insufficient API credits; no automatic recharge.',
                429:'Rate limited; creation will not be retried automatically.'}
            raise TrialError(f'Meshy HTTP {error.code}. '+hints.get(error.code,'Inspect the task in the provider console.')) from None
        except (URLError,TimeoutError,OSError,ValueError):
            raise TrialError('Meshy response unavailable. Creation will not be retried; inspect the provider console.') from None


def submit(plan, directory, api, max_credits, allow_upload):
    cost = validate_plan(plan)
    if not allow_upload:
        raise TrialError('Submission needs --allow-upload to send the four named photos to Meshy.')
    if max_credits < cost:
        raise TrialError(f'Plan needs {cost} credits, exceeding the supplied ceiling.')
    balance = api.request('GET','/openapi/v1/balance')
    available = balance.get('balance')
    if not isinstance(available,(int,float)) or available < cost:
        raise TrialError('Insufficient or unverified API balance; no task was created.')
    directory = Path(directory)
    directory.mkdir(parents=True,exist_ok=True)
    job_path = directory / 'job.json'
    # Atomic, persistent marker: never automatically submit an identical job twice.
    try:
        with (directory / 'submission.lock').open('x',encoding='utf-8') as lock:
            lock.write(now())
    except FileExistsError:
        raise TrialError('This trial was already submitted or its result is uncertain. Inspect job.json / Meshy tasks.') from None
    job = {'provider':'meshy','productId':plan['productId'],'colorwayId':plan['colorwayId'],
        'createdAt':now(),'region':'cn' if api.base==BASES['cn'] else 'global',
        'estimatedCredits':cost,'creditCeiling':max_credits,'balanceBefore':available,
        'status':'SUBMITTING','taskId':None,'reviewed':False,'publishable':False}
    write_json(job_path,job)
    payload = dict(plan['options'])
    mimes={'.jpg':'image/jpeg','.jpeg':'image/jpeg','.png':'image/png','.webp':'image/webp'}
    payload['image_urls']=['data:'+mimes[Path(i['path']).suffix.lower()]+';base64,'+
        base64.b64encode(Path(i['path']).read_bytes()).decode('ascii') for i in plan['images']]
    try:
        result = api.request('POST',ENDPOINT,payload)
        task_id = result.get('result')
        if not isinstance(task_id,str) or not 1 <= len(task_id) <= 200:
            raise TrialError('Creation response has no valid task ID; reconcile in Meshy before another submission.')
        job.update(taskId=task_id,status='PENDING')
        write_json(job_path,job)
    except BaseException:
        job['status']='SUBMISSION_UNCERTAIN'
        write_json(job_path,job)
        raise
    return job


def glb_info(path):
    raw = Path(path).read_bytes()
    if len(raw)<20 or struct.unpack_from('<4sII',raw) != (b'glTF',2,len(raw)):
        raise TrialError('Downloaded output is not a complete GLB 2.0 file.')
    size,kind = struct.unpack_from('<II',raw,12)
    if kind != 0x4E4F534A or size > len(raw)-20:
        raise TrialError('Downloaded GLB has invalid JSON chunk.')
    doc=json.loads(raw[20:20+size].decode().rstrip(' \0'))
    if not doc.get('meshes'):
        raise TrialError('Downloaded GLB has no geometry.')
    triangles=0
    for mesh in doc['meshes']:
        for primitive in mesh['primitives']:
            accessor=primitive.get('indices',primitive.get('attributes',{}).get('POSITION'))
            if accessor is not None and primitive.get('mode',4)==4:
                triangles+=doc['accessors'][accessor]['count']//3
    return {'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest(),'triangles':triangles,
        'needsMobileOptimization':len(raw)>25*1024*1024 or triangles>200000}


def download_glb(url, target):
    parsed=urlparse(url)
    if parsed.scheme != 'https' or parsed.username or parsed.password or parsed.port not in (None,443) or not parsed.hostname or not any(parsed.hostname.endswith('.'+domain) for domain in ('meshy.cn','meshy.ai')):
        raise TrialError('Unknown asset host; inspect the official output URL before downloading.')
    temp=Path(target).with_suffix('.glb.part')
    try:
        # No API authorization on CDN requests, no redirects to arbitrary hosts.
        with build_opener(NoRedirect()).open(Request(url,headers={'User-Agent':'Lectus-local-trial/1'}),timeout=60) as response, temp.open('wb') as file:
            total=0
            while chunk:=response.read(1024*1024):
                total+=len(chunk)
                if total>128*1024*1024:
                    raise TrialError('Output exceeds the 128 MiB trial download limit.')
                file.write(chunk)
        details=glb_info(temp)
        os.replace(temp,target)
        return details
    except (HTTPError,URLError,TimeoutError,OSError):
        raise TrialError('Asset download failed. Re-run status --download to refresh the signed URL; no new generation needed.') from None
    finally:
        if temp.exists():
            temp.unlink()


def status(directory, api, download=False):
    directory=Path(directory);job=read_json(directory/'job.json')
    if not job.get('taskId'):
        raise TrialError('No saved task ID. Reconcile an uncertain submission in Meshy; do not create another task automatically.')
    region='cn' if api.base==BASES['cn'] else 'global'
    if job['region'] != region:
        raise TrialError('Region differs from the original submission.')
    task=api.request('GET',ENDPOINT+'/'+quote(job['taskId'],safe=''))
    if task.get('status') not in ('PENDING','IN_PROGRESS','SUCCEEDED','FAILED','CANCELED'):
        raise TrialError('Unknown task status; preserve the existing job and inspect in Meshy.')
    job.update(status=task['status'],progress=task.get('progress'),updatedAt=now(),
        consumedCredits=task.get('consumed_credits'),precedingTasks=task.get('preceding_tasks'))
    write_json(directory/'job.json',job)
    if download and job['status']=='SUCCEEDED':
        url=task.get('model_urls',{}).get('glb')
        if not url:
            raise TrialError('Completed task has no GLB output.')
        path=directory/'preview.glb'
        details=download_glb(url,path)
        generation={**job,**details,'file':path.name,'origin':'MESHY_AI_PREVIEW',
            'reviewed':False,'publishable':False,'reviewStatus':'PENDING_VISUAL_AND_LICENSE_REVIEW',
            'notice':'AI approximation, not verified fit/scale. No catalog binding or AR try-on.'}
        write_json(directory/'generation.json',generation)
        job['download']=details
    return job


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--region',choices=['cn','global'],default='cn')
    commands=parser.add_subparsers(dest='command',required=True)
    commands.add_parser('configure');commands.add_parser('balance')
    plan=commands.add_parser('plan');plan.add_argument('--source',type=Path,default=DEFAULT_SOURCE)
    plan.add_argument('--output',type=Path,default=DEFAULT_PLAN)
    plan.add_argument('--geometry',choices=['standard','2k'],default='standard')
    send=commands.add_parser('submit');send.add_argument('--plan',type=Path,default=DEFAULT_PLAN)
    send.add_argument('--max-credits',type=int,required=True);send.add_argument('--allow-upload',action='store_true')
    check=commands.add_parser('status');check.add_argument('--plan',type=Path,default=DEFAULT_PLAN)
    check.add_argument('--download',action='store_true')
    args=parser.parse_args()
    if args.command=='configure':
        key=getpass.getpass('Paste Meshy API key (hidden): ').strip()
        if not key.startswith('msy_') or any(c.isspace() for c in key):
            raise TrialError('Invalid Meshy key format; nothing saved.')
        write_json(KEY_FILE,{'api_key':key});os.chmod(KEY_FILE,0o600)
        print('Key stored only in ignored .runtime/meshy.json. No network request made.');return
    if args.command=='plan':
        result=create_plan(args.source,args.output,args.geometry)
        print(json.dumps({'mode':'OFFLINE_PLAN','plan':str(args.output),'product':result['name'],
            'colorway':result['colorway'],'images':len(result['images']),
            'estimatedCredits':result['estimatedCredits'],'submitted':False},ensure_ascii=False,indent=2));return
    api=Meshy(load_key(),args.region)
    if args.command=='balance':
        value=api.request('GET','/openapi/v1/balance')
        result={k:value.get(k) for k in ['balance','monthly_credits','permanent_credits','monthly_resets_at']}
    elif args.command=='submit':
        result=submit(read_json(args.plan),args.plan.parent,api,args.max_credits,args.allow_upload)
    else:
        result=status(args.plan.parent,api,args.download)
    print(json.dumps(result,ensure_ascii=False,indent=2))


if __name__=='__main__':
    try:
        main()
    except (TrialError,FileNotFoundError,KeyError,json.JSONDecodeError) as error:
        print('Trial stopped: '+str(error),file=sys.stderr)
        sys.exit(1)

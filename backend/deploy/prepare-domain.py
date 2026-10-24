"""Run on the target host before first Lectus domain release. Never imports old member data."""
from pathlib import Path
import json
import os
import secrets
import subprocess

ROOT = Path('/opt/lectus-saas')
DOMAIN = 'www.lectusglobal.space'
def run(command, **kwargs):
 return subprocess.run(command, check=True, universal_newlines=True, **kwargs)
def sql(query, database='postgres'):
 return run(['runuser','-u','postgres','--','psql','-v','ON_ERROR_STOP=1','-At','-d',database], input=query, stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout.strip()
def envfile(path, values):
 if path.exists(): return
 path.write_text(''.join(k+'='+v+'\n' for k,v in values.items()))
 path.chmod(0o600)

for part in ['config','releases','data','acme','backups']:
 (ROOT/part).mkdir(parents=True,exist_ok=True)
(ROOT/'config').chmod(0o700)
run(['id','lectus-saas'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL) if subprocess.run(['id','lectus-saas'],stdout=subprocess.PIPE,stderr=subprocess.PIPE).returncode==0 else run(['useradd','--system','--home-dir',str(ROOT),'--shell','/sbin/nologin','lectus-saas'])
run(['chown','lectus-saas:lectus-saas',str(ROOT/'data')])
database_secrets=ROOT/'config/database-secrets.json'
if database_secrets.exists(): passwords=json.loads(database_secrets.read_text())
else:
 passwords={service:secrets.token_urlsafe(36) for service in ['identity','catalog','trade','member']}
 database_secrets.write_text(json.dumps(passwords));database_secrets.chmod(0o600)
for service,password in passwords.items():
 name='lectus_saas_'+service
 if sql("SELECT COUNT(*) FROM pg_roles WHERE rolname='"+name+"'")=='0':sql("CREATE ROLE "+name+" LOGIN PASSWORD '"+password+"';")
 if sql("SELECT COUNT(*) FROM pg_database WHERE datname='"+name+"'")=='0':sql('CREATE DATABASE '+name+' OWNER '+name+';')
 sql('REVOKE CONNECT ON DATABASE '+name+' FROM PUBLIC; GRANT CONNECT ON DATABASE '+name+' TO '+name+';')
 sql('REVOKE CREATE ON SCHEMA public FROM PUBLIC; GRANT ALL ON SCHEMA public TO '+name+';',name)
 common={'SPRING_PROFILES_ACTIVE':'postgres','SERVER_ADDRESS':'127.0.0.1','SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE':'3','SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE':'1','CORS_ORIGINS':'https://'+DOMAIN,'SERVER_TOMCAT_THREADS_MAX':'12','SERVER_TOMCAT_THREADS_MIN_SPARE':'1'}
 if service=='member':
  common.update({'VIRIDAC_PROFILE':'postgres','VIRIDAC_BIND_ADDRESS':'127.0.0.1','VIRIDAC_C_PORT':'18181','VIRIDAC_DB_URL':'jdbc:postgresql://127.0.0.1:5432/'+name,'VIRIDAC_DB_USERNAME':name,'VIRIDAC_DB_PASSWORD':password,
   'VIRIDAC_ALLOWED_ORIGINS':'https://'+DOMAIN,'LECTUS_MALL_LOCAL_ENABLED':'true','LECTUS_MALL_BOOTSTRAP_PASSWORD':secrets.token_urlsafe(24),
   'VIRIDAC_SMS_ENABLED':'false','VIRIDAC_AMAP_ENABLED':'false','VIRIDAC_AI_ENABLED':'false','VIRIDAC_SANDBOX_PAYMENT_ENABLED':'false','VIRIDAC_SANDBOX_CONFIRM_ENABLED':'false','VIRIDAC_ALLOW_DEV_DEPOSIT':'false','VIRIDAC_ORDER_TIMEOUT_TASK_ENABLED':'false','VIRIDAC_UPLOAD_DIRECTORY':str(ROOT/'data/uploads'),'LECTUS_DEMO_ENABLED':'false'})
 else:
  common.update({'PORT':str({'identity':18182,'catalog':18183,'trade':18184}[service]),'DB_URL':'jdbc:postgresql://127.0.0.1:5432/'+name,'DB_USER':name,'DB_PASSWORD':password,'IDENTITY_URL':'http://127.0.0.1:18182','CATALOG_CACHE':'false','SEED_CATALOG':'false','ASSETS_ORIGIN':'http://127.0.0.1:9000/lectus-assets/'})
 envfile(ROOT/'config'/f'{service}.env',common)
envfile(ROOT/'config/gateway.env',{'PORT':'18180','SERVER_ADDRESS':'127.0.0.1','IDENTITY_URL':'http://127.0.0.1:18182','CATALOG_URL':'http://127.0.0.1:18183','TRADE_URL':'http://127.0.0.1:18184','MEMBER_URL':'http://127.0.0.1:18181','FRONTEND_PUBLIC_ORIGIN':'https://'+DOMAIN})
for service in ['identity','catalog','trade','member','gateway']:
 unit=f'''[Unit]
Description=Lectus SaaS {service}
After=network.target postgresql.service

[Service]
Type=simple
User=lectus-saas
Group=lectus-saas
WorkingDirectory={ROOT}/data
EnvironmentFile={ROOT}/config/{service}.env
Environment=TZ=Asia/Shanghai
ExecStart=/usr/lib/jvm/jre-21/bin/java -Xms24m -Xmx128m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=24m -XX:ActiveProcessorCount=1 -Dfile.encoding=UTF-8 -jar {ROOT}/current/backend/{service}.jar
Restart=on-failure
RestartSec=8
MemoryMax=384M
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths={ROOT}/data
UMask=0027
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
'''
 Path('/etc/systemd/system/lectus-saas-'+service+'.service').write_text(unit)
run(['systemctl','daemon-reload'])
print('Prepared isolated PostgreSQL databases, configuration and five service units. No services started.')

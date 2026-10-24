raise SystemExit('Retired legacy remote/MySQL tool. Use backend/deploy PostgreSQL deployment instead.')
"""Provision Lectus-only resources and keep local SSH tunnels alive. No business code is deployed.

Passwords are prompted without echo. Generated application credentials live only in
.runtime/ (gitignored); SSH and database root credentials are never saved.
"""
import argparse
import getpass
import json
import os
from pathlib import Path
import re
import secrets
import socketserver
import threading
import time
import select
import hashlib
import subprocess
import tempfile
import urllib.request
from urllib.parse import quote

import boto3
from botocore.config import Config
import paramiko
import pymysql
import redis

ROOT=Path(__file__).resolve().parents[1]
RUNTIME=ROOT/'.runtime'

class Forward(socketserver.ThreadingTCPServer):
    allow_reuse_address=True
    daemon_threads=True

def tunnel(ssh,port,host,remote):
    class Handler(socketserver.BaseRequestHandler):
        def handle(self):
            channel=ssh.get_transport().open_channel('direct-tcpip',(host,remote),self.request.getpeername())
            try:
                while True:
                    ready,_,_=select.select([self.request,channel],[],[],30)
                    for source in ready:
                        data=source.recv(65536)
                        if not data:return
                        (channel if source is self.request else self.request).sendall(data)
            finally:channel.close()
    server=Forward(('127.0.0.1',port),Handler)
    threading.Thread(target=server.serve_forever,daemon=True).start()
    return server

def connect_ssh():
    ssh=paramiko.SSHClient();ssh.load_system_host_keys()
    known=Path.home()/'.ssh/known_hosts'
    if known.exists():ssh.load_host_keys(str(known))
    ssh.set_missing_host_key_policy(paramiko.RejectPolicy())
    password=getpass.getpass('SSH password: ')
    ssh.connect('39.107.112.161',username='root',password=password,look_for_keys=False,allow_agent=False,timeout=15)
    ssh.get_transport().set_keepalive(20)
    return ssh

def minio_credentials(ssh):
    # Read only MinIO's configured environment; never log the file or its contents.
    sftp=ssh.open_sftp();values={}
    try:
        for path in ['/etc/systemd/system/minio.service','/usr/lib/systemd/system/minio.service','/etc/default/minio','/etc/sysconfig/minio']:
            try:
                with sftp.open(path) as f:text=f.read().decode()
            except OSError:continue
            for line in text.splitlines():
                match=re.search(r'(MINIO_ROOT_USER|MINIO_ROOT_PASSWORD|MINIO_ACCESS_KEY|MINIO_SECRET_KEY)\s*=\s*([^\r\n]+)',line)
                if match:values[match[1]]=match[2].strip().strip('"').strip("'")
            for envpath in re.findall(r'^EnvironmentFile=-?([^\s]+)',text,re.M):
                try:
                    with sftp.open(envpath) as f:env=f.read().decode()
                    for line in env.splitlines():
                        match=re.match(r'(MINIO_ROOT_USER|MINIO_ROOT_PASSWORD|MINIO_ACCESS_KEY|MINIO_SECRET_KEY)\s*=\s*(.*)',line)
                        if match:values[match[1]]=match[2].strip().strip('"').strip("'")
                except OSError:pass
    finally:sftp.close()
    user=values.get('MINIO_ROOT_USER',values.get('MINIO_ACCESS_KEY'))
    password=values.get('MINIO_ROOT_PASSWORD',values.get('MINIO_SECRET_KEY'))
    if not user or not password:raise RuntimeError('MinIO credentials were not found in its service environment; configure them explicitly.')
    return user,password

def scoped_s3_user(ssh,access,secret,config):
    from minio.minioadmin import MinioAdmin
    from minio.credentials import StaticProvider
    user='lectus-import';password=config.get('s3_secret_key') or secrets.token_urlsafe(32)
    policy={'Version':'2012-10-17','Statement':[
        {'Effect':'Allow','Action':['s3:ListBucket','s3:GetBucketLocation'],'Resource':['arn:aws:s3:::lectus-assets']},
        {'Effect':'Allow','Action':['s3:GetObject','s3:PutObject'],'Resource':['arn:aws:s3:::lectus-assets/catalog/*','arn:aws:s3:::lectus-assets/models/*']}]}
    admin=MinioAdmin(endpoint='127.0.0.1:19000',credentials=StaticProvider(access,secret),secure=False)
    admin.user_add(user,password)
    admin.policy_add('lectus-assets-import',policy=policy)
    admin.policy_set('lectus-assets-import',user=user)
    return user,password

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--bootstrap',action='store_true');args=parser.parse_args()
    RUNTIME.mkdir(exist_ok=True)
    ssh=connect_ssh();servers=[]
    try:
        servers += [tunnel(ssh,19906,'127.0.0.1',3306),tunnel(ssh,19179,'127.0.0.1',6379),tunnel(ssh,19000,'127.0.0.1',9000)]
        config_path=RUNTIME/'remote.json'
        config=json.loads(config_path.read_text()) if config_path.exists() else {}
        if args.bootstrap:
            root_password=getpass.getpass('MySQL root password: ')
            root_tunnel=tunnel(ssh,29906,'39.107.112.161',3306);servers.append(root_tunnel)
            admin=pymysql.connect(host='127.0.0.1',port=29906,user='root',password=root_password,autocommit=True,charset='utf8mb4')
            dbpass=config.get('db_password') or secrets.token_urlsafe(32)
            with admin.cursor() as cur:
                cur.execute('CREATE DATABASE IF NOT EXISTS lectus CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci')
                cur.execute("CREATE USER IF NOT EXISTS 'lectus_app'@'localhost' IDENTIFIED BY %s",(dbpass,))
                cur.execute("ALTER USER 'lectus_app'@'localhost' IDENTIFIED BY %s",(dbpass,))
                cur.execute("GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON lectus.* TO 'lectus_app'@'localhost'")
            admin.close();print('MySQL: Lectus database and scoped application user ready.',flush=True)
            # Redis permissions are restricted to this project's keyspace. Other ACLs/configuration are untouched.
            redispass=config.get('redis_password') or secrets.token_urlsafe(32)
            config.update(db_password=dbpass,redis_password=redispass)
            config_path.write_text(json.dumps(config),encoding='utf-8');os.chmod(config_path,0o600)
            r=redis.Redis(host='127.0.0.1',port=19179)
            r.execute_command('ACL','SETUSER','lectus_app','reset','on','>'+redispass,'~lectus:*','+get','+set','+del','+unlink','+ping','+select','+hello','+client|setname','+client|id')
            try:r.execute_command('ACL','SAVE')
            except redis.ResponseError:print('Redis: no ACL file configured; restore the scoped user with --bootstrap after a Redis restart.',flush=True)
            print('Redis: lectus:* scoped user ready.',flush=True)
            access,secret=minio_credentials(ssh)
            s3=boto3.client('s3',endpoint_url='http://127.0.0.1:19000',aws_access_key_id=access,aws_secret_access_key=secret,region_name='us-east-1',config=Config(signature_version='s3v4',s3={'addressing_style':'path'}))
            names={b['Name'] for b in s3.list_buckets()['Buckets']}
            if 'lectus-assets' not in names:s3.create_bucket(Bucket='lectus-assets')
            # Only content-addressed, public product assets can be read anonymously; no listing or upload rights.
            policy={'Version':'2012-10-17','Statement':[{'Effect':'Allow','Principal':{'AWS':['*']},'Action':['s3:GetObject'],'Resource':['arn:aws:s3:::lectus-assets/catalog/*','arn:aws:s3:::lectus-assets/models/*']}]}
            s3.put_bucket_policy(Bucket='lectus-assets',Policy=json.dumps(policy))
            scoped_access,scoped_secret=scoped_s3_user(ssh,access,secret,config)
            # Save application/import credentials only, never middleware root passwords.
            config.update(db_password=dbpass,redis_password=redispass,s3_access_key=scoped_access,s3_secret_key=scoped_secret)
            config_path.write_text(json.dumps(config),encoding='utf-8')
            os.chmod(config_path,0o600)
            print('MinIO: isolated lectus-assets bucket ready.',flush=True)
        if not config:raise RuntimeError('Run --bootstrap before opening the development tunnels.')
        db=pymysql.connect(host='127.0.0.1',port=19906,user='lectus_app',password=config['db_password'],database='lectus')
        with db.cursor() as cur:cur.execute('SELECT 1');assert cur.fetchone()[0]==1
        db.close()
        assert redis.Redis(host='127.0.0.1',port=19179,username='lectus_app',password=config['redis_password']).ping()
        print('READY: local MySQL 19906, Redis 19179, MinIO 19000; remote services in use, application code stays local.',flush=True)
        while ssh.get_transport().is_active():time.sleep(1)
    finally:
        for server in servers:server.shutdown();server.server_close()
        ssh.close()

if __name__=='__main__':main()

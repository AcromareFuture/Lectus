"""Read the legacy source catalog and copy into the new PostgreSQL catalog database.
No writes to the legacy database. Credentials never leave the host or appear in output.
"""
from pathlib import Path
import json
import os
import subprocess
from urllib.parse import urlparse

TARGET='lectus_saas_catalog'
def command(args, **kwargs):
 return subprocess.run(args,check=True,universal_newlines=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,**kwargs).stdout
def pg(query):
 return command(['runuser','-u','postgres','--','psql','-v','ON_ERROR_STOP=1','-At','-F','\t','-d',TARGET],input=query).strip()
if int(pg('SELECT COUNT(*) FROM catalog_products'))>0:
 print('New source catalog already contains data; retained existing records.')
 raise SystemExit(0)
config={}
for line in Path('/opt/lectus/config/api.env').read_text().splitlines():
 if '=' in line:
  k,v=line.split('=',1);config[k]=v.strip('"')
url=urlparse(config['DB_URL'].replace('jdbc:','',1))
if url.scheme!='mysql' or url.hostname not in ['localhost','127.0.0.1']:
 raise RuntimeError('Unexpected legacy catalog connection')
env=os.environ.copy();env['MYSQL_PWD']=config['DB_PASSWORD']
def source(query):
 return command(['mysql','--host='+url.hostname,'--port='+str(url.port or 3306),'--user='+config['DB_USER'],'--default-character-set=utf8mb4','--batch','--raw','--skip-column-names',url.path.lstrip('/'),'-e',query],env=env)
tables=['catalog_batches','catalog_brands','catalog_products','catalog_colorways','catalog_skus','catalog_images','catalog_sku_images','catalog_issues','catalog_models','catalog_meta']
sql=['BEGIN;','SET standard_conforming_strings=on;'];counts={}
for table in tables:
 columns=[line.split('\t')[0] for line in source('SHOW COLUMNS FROM '+table).splitlines()]
 pg_types=dict(line.split('\t') for line in pg("SELECT column_name,data_type FROM information_schema.columns WHERE table_name='"+table+"'" ).splitlines())
 columns=[c for c in columns if c in pg_types]
 expr=','.join("'"+c+"',`"+c+'`' for c in columns)
 rows=[json.loads(line) for line in source('SELECT JSON_OBJECT('+expr+') FROM '+table).splitlines()]
 counts[table]=len(rows)
 def literal(column,value):
  if value is None:return 'NULL'
  if pg_types[column]=='boolean':return 'TRUE' if value else 'FALSE'
  if isinstance(value,(int,float)):return str(value)
  return "'"+str(value).replace("'","''")+"'"
 for row in rows:
  statement='INSERT INTO '+table+'('+','.join(columns)+') VALUES('+','.join(literal(c,row[c]) for c in columns)+')'
  if table=='catalog_meta':statement+=' ON CONFLICT(id) DO UPDATE SET value_text=EXCLUDED.value_text'
  sql.append(statement+';')
sql.append('COMMIT;')
pg('\n'.join(sql))
print(json.dumps({'catalogCopy':'PASS','targetDatabase':TARGET,'rows':counts},ensure_ascii=False))

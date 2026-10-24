"""Validate and import the supplied source catalog. Dry-run is the default.

No collection scripts are executed. Source prices, stock signals, identifiers and
specifications are preserved; selling prices and own stock are never invented.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import time

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError
from PIL import Image
import os
import psycopg

ROOT=Path(__file__).resolve().parents[3]
DEFAULT_SOURCE=Path(r'C:\Users\heng\Documents\Codex\2026-10-17\sh\outputs\商品库_第1批')
ARCHIVE_SHA='3f588aae594aac7a3d3957a8c38183376bdfd09f5bf90dc97568069fa3aa0f57'
BATCH='batch1-'+ARCHIVE_SHA[:16]

def environment_config():
    return {'s3_access_key':os.environ.get('AWS_ACCESS_KEY_ID'),
            's3_secret_key':os.environ.get('AWS_SECRET_ACCESS_KEY'),
            's3_endpoint':os.environ.get('S3_ENDPOINT','http://127.0.0.1:19000')}

def encoded(value):return json.dumps(value,ensure_ascii=False,separators=(',',':'))

def load(source):
    data={name:json.loads((source/'数据'/(name+'.json')).read_text(encoding='utf-8-sig')) for name in ['brands','spus','colorways','skus','images','sku_images']}
    for name,key in [('brands','brand_id'),('spus','spu_id'),('colorways','colorway_id'),('skus','sku_id'),('images','image_id')]:
        assert len({row[key] for row in data[name]})==len(data[name]),'Duplicate '+name
    products={p['spu_id']:p for p in data['spus']};colors={c['colorway_id']:c for c in data['colorways']};skus={s['sku_id']:s for s in data['skus']};images={i['image_id']:i for i in data['images']}
    for s in skus.values():assert s['spu_id'] in products and colors[s['colorway_id']]['spu_id']==s['spu_id']
    for link in data['sku_images']:
        assert link['sku_id'] in skus and link['image_id'] in images
        assert skus[link['sku_id']]['spu_id']==images[link['image_id']]['spu_id'],'Cross-product image'
    good=[i for i in images.values() if i['status']=='ok']
    def check_image(i):
        path=(source/i['local_file']).resolve();assert path.is_relative_to(source.resolve())
        assert hashlib.sha256(path.read_bytes()).hexdigest()==i['sha256'],'Image checksum mismatch'
        with Image.open(path) as image:image.load();assert image.size==(1200,1200)
    with ThreadPoolExecutor(max_workers=6) as pool:list(pool.map(check_image,good))
    return data,products,colors,skus,images

def requirements(products,colors,images):
    return [{'productId':p['spu_id'],'brand':p['brand_name'],'name':p['model'],
        'colorways':[{'colorwayId':c['colorway_id'],'name':c['color_name'],'styleCode':c['style_code'],
            'referenceImage':images.get(c.get('main_image_id'),{}).get('local_file'),
            'sourceUrl':c['source_url'],'status':'PENDING_ASSET'} for c in colors.values() if c['spu_id']==p['spu_id']]} for p in products.values() if p['category']=='鞋类']

def assets(source,images):
    cache=ROOT/'.local/work/catalog-assets';jobs={}
    def prepare(i):
        prefix='catalog/v1/'+i['sha256']+'/'
        original=source/i['local_file']
        paths={'original.jpg':original}
        for file,edge in [('thumb.webp',480),('detail.webp',960)]:
            target=cache/prefix/file
            if not target.exists():
                target.parent.mkdir(parents=True,exist_ok=True)
                with Image.open(original) as image:
                    image.thumbnail((edge,edge),Image.Resampling.LANCZOS)
                    image.save(target,'WEBP',quality=85,method=4)
            paths[file]=target
        return {prefix+name:path for name,path in paths.items()}
    unique={i['sha256']:i for i in images.values() if i['status']=='ok'}
    with ThreadPoolExecutor(max_workers=6) as pool:
        for result in pool.map(prepare,unique.values()):jobs.update(result)
    return jobs

def upload(jobs,config):
    s3=boto3.client('s3',endpoint_url=config['s3_endpoint'],aws_access_key_id=config['s3_access_key'],aws_secret_access_key=config['s3_secret_key'],region_name='us-east-1',config=Config(signature_version='s3v4',s3={'addressing_style':'path'},max_pool_connections=8,retries={'max_attempts':4}))
    existing=set()
    for page in s3.get_paginator('list_objects_v2').paginate(Bucket='lectus-assets',Prefix='catalog/v1/'):
        existing.update(x['Key'] for x in page.get('Contents',[]))
    pending=[(key,path) for key,path in jobs.items() if key not in existing]
    print(f'Assets: {len(jobs)} objects, {len(pending)} to upload; existing objects retained.',flush=True)
    def send(job):
        key,path=job;content='image/webp' if key.endswith('.webp') else 'image/jpeg'
        s3.put_object(Bucket='lectus-assets',Key=key,Body=path.read_bytes(),ContentType=content,CacheControl='public,max-age=31536000,immutable',Metadata={'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    with ThreadPoolExecutor(max_workers=6) as pool:
        for count,_ in enumerate(pool.map(send,pending),1):
            if count%200==0 or count==len(pending):print(f'Uploaded {count}/{len(pending)} objects.',flush=True)
    return len(pending)

def database(config):
    return psycopg.connect(os.environ['CATALOG_DATABASE_URL'], autocommit=False)

def write_catalog(data,products,colors,skus,images,config,summary):
    db=database(config)
    def upsert(cur,table,columns,records,preserve=()):
        if not records:return
        fields=columns.split(',');updates=','.join(f'{x}=EXCLUDED.{x}' for x in fields[1:] if x not in preserve)
        sql=f'INSERT INTO {table}({columns}) VALUES({",".join(["%s"]*len(fields))}) ON CONFLICT ({fields[0]}) DO UPDATE SET {updates}'
        for start in range(0,len(records),300):cur.executemany(sql,records[start:start+300])
    try:
        with db.cursor() as cur:
            cur.execute('SELECT pg_try_advisory_xact_lock(%s)',(7162310042,));assert cur.fetchone()[0],'Another import is running'
            upsert(cur,'catalog_batches','id,archive_sha256,imported_at,status,summary',[(BATCH,ARCHIVE_SHA,datetime.now(timezone.utc).replace(tzinfo=None),'IMPORTED',encoded(summary))],('imported_at',))
            upsert(cur,'catalog_brands','id,name',[(r['brand_id'],r['brand_name']) for r in data['brands']])
            upsert(cur,'catalog_products','id,brand_id,category,name,featured,state,main_image_id,batch_id,raw_json',[(r['spu_id'],r['brand_id'],r['category'],r['model'],r['featured'],'PREVIEW',r['main_image_id'] or None,BATCH,encoded(r)) for r in products.values()],('state',))
            upsert(cur,'catalog_colorways','id,product_id,name,color_explicit,style_code,main_image_id,raw_json',[(r['colorway_id'],r['spu_id'],r['color_name'],r['color_explicit'],r['style_code'],r['main_image_id'] or None,encoded(r)) for r in colors.values()])
            upsert(cur,'catalog_skus','id,product_id,colorway_id,specs_json,source_sku,source_variant_id,source_url,source_available,source_price_amount,source_price_currency,main_image_id,raw_json',[(r['sku_id'],r['spu_id'],r['colorway_id'],encoded(r['specs']),r['source_sku'],r['source_variant_id'],r['source_url'],r['source_available'],Decimal(r['source_price_amount']) if r['source_price_amount'] else None,r['source_price_currency'],r['main_image_id'] or None,encoded(r)) for r in skus.values()])
            image_rows=[]
            for r in images.values():
                prefix='catalog/v1/'+r['sha256']+'/' if r['status']=='ok' else None
                image_rows.append((r['image_id'],r['spu_id'],r.get('colorway_id') or None,r.get('sha256'),r['status'],prefix+'original.jpg' if prefix else None,prefix+'thumb.webp' if prefix else None,prefix+'detail.webp' if prefix else None,r.get('source_position',0),encoded(r)))
            upsert(cur,'catalog_images','id,product_id,colorway_id,sha256,status,object_key,thumb_key,detail_key,source_position,raw_json',image_rows)
            links=[];issues=[]
            for link in data['sku_images']:
                s=skus[link['sku_id']];i=images[link['image_id']]
                review=bool(i.get('colorway_id') and s['colorway_id']!=i['colorway_id'])
                links.append((s['sku_id'],i['image_id'],link['mapping_method'],review))
                if review:issues.append((s['sku_id']+':'+i['image_id'],BATCH,'IMAGE_MAPPING_REVIEW',s['sku_id'],encoded(link)))
            # Composite-key associations are inserted separately to avoid changing their keys.
            cur.executemany('INSERT INTO catalog_sku_images(sku_id,image_id,mapping_method,review_required) VALUES(%s,%s,%s,%s) ON CONFLICT (sku_id,image_id) DO UPDATE SET mapping_method=EXCLUDED.mapping_method,review_required=EXCLUDED.review_required',links)
            for s in skus.values():
                issues.append((s['sku_id']+':stock',BATCH,'OWN_STOCK_UNKNOWN',s['sku_id'],'No own inventory in source package'))
                if not s['source_price_currency']:issues.append((s['sku_id']+':currency',BATCH,'CURRENCY_UNKNOWN',s['sku_id'],'Source amount retained; selling price has not been set'))
                if not s['main_image_id']:issues.append((s['sku_id']+':image',BATCH,'MISSING_IMAGE',s['sku_id'],'No verified main image'))
            upsert(cur,'catalog_issues','id,batch_id,kind,entity_id,detail',issues)
            models=[('MODEL-'+c['colorway_id'],c['spu_id'],c['colorway_id'],'PENDING_ASSET') for c in colors.values() if products[c['spu_id']]['category']=='鞋类']
            # A re-import must never replace reviewed 3D bindings with placeholders.
            cur.executemany('INSERT INTO catalog_models(id,product_id,colorway_id,status) VALUES(%s,%s,%s,%s) ON CONFLICT DO NOTHING',models)
            cur.execute("UPDATE catalog_meta SET value_text=%s WHERE id='revision'",(str(time.time_ns()),))
            for table,expected in [('catalog_brands',len(data['brands'])),('catalog_products',len(products)),('catalog_colorways',len(colors)),('catalog_skus',len(skus)),('catalog_images',len(images)),('catalog_sku_images',len(links))]:
                cur.execute('SELECT COUNT(*) FROM '+table);actual=cur.fetchone()[0]
                assert actual>=expected,(table,actual,expected)
            cur.execute('SELECT COUNT(*) FROM catalog_skus WHERE sale_price_cents IS NOT NULL OR own_stock IS NOT NULL');summary['skusWithBusinessFields']=cur.fetchone()[0]
            cur.execute("SELECT COUNT(*) FROM catalog_models WHERE status='READY' AND reviewed=TRUE");summary['readyModels']=cur.fetchone()[0]
        db.commit()
    except Exception:db.rollback();raise
    finally:
        db.close()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path,default=DEFAULT_SOURCE);parser.add_argument('--apply',action='store_true');parser.add_argument('--rollback',metavar='BATCH_ID');args=parser.parse_args()
    config=environment_config() if args.apply or args.rollback else None
    if args.rollback:
        db=database(config)
        with db.cursor() as cur:
            cur.execute("UPDATE catalog_products SET state='HIDDEN' WHERE batch_id=%s",(args.rollback,));count=cur.rowcount
            cur.execute("UPDATE catalog_meta SET value_text=%s WHERE id='revision'",(str(time.time_ns()),))
        db.commit();db.close();print(f'Hidden {count} products in batch {args.rollback}; source records/assets retained.');return
    data,products,colors,skus,images=load(args.source)
    required=requirements(products,colors,images)
    report=ROOT/'.local/work/catalog-import';report.mkdir(parents=True,exist_ok=True)
    (report/'shoe-model-requirements.json').write_text(json.dumps(required,ensure_ascii=False,indent=2),encoding='utf-8')
    summary={'batchId':BATCH,'brands':len(data['brands']),'spus':len(products),'colorways':len(colors),'skus':len(skus),'images':sum(i['status']=='ok' for i in images.values()),'skuImageLinks':len(data['sku_images']),'shoeProducts':len(required),'shoeColorways':sum(len(r['colorways']) for r in required),'readyModels':0,'mode':'APPLY' if args.apply else 'DRY_RUN'}
    print(json.dumps(summary,ensure_ascii=False),flush=True)
    if args.apply:
        jobs=assets(args.source,images);summary['uploadedObjects']=upload(jobs,config)
        write_catalog(data,products,colors,skus,images,config,summary)
        print('Database import committed; selling prices and own stock preserved.',flush=True)
    (report/('import-result.json' if args.apply else 'dry-run.json')).write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':main()

"""Validate licensed, reviewed GLBs and bind them to exact catalog colorway IDs.

Usage: python backend/tools/catalog/bind_models.py path/to/manifest.json [--apply]
Dry-run is the default; this tool does not generate or acquire models.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import time
from urllib.parse import urlparse

import boto3
from botocore.config import Config
from PIL import Image
from io import BytesIO

from import_catalog import ROOT,database,environment_config

def validate_glb(path):
    data=path.read_bytes()
    if len(data)>25*1024*1024:raise ValueError('Mobile GLB must be at most 25 MiB; optimize it before binding')
    if len(data)<20:raise ValueError('Truncated GLB')
    magic,version,length=struct.unpack_from('<4sII',data)
    if magic!=b'glTF' or version!=2 or length!=len(data):raise ValueError('Invalid GLB 2.0 header')
    offset=12;chunks=[]
    while offset<len(data):
        size,kind=struct.unpack_from('<II',data,offset);offset+=8
        if size%4 or offset+size>len(data):raise ValueError('Invalid GLB chunk')
        chunks.append((kind,data[offset:offset+size]));offset+=size
    if not chunks or chunks[0][0]!=0x4E4F534A:raise ValueError('First chunk must contain glTF JSON')
    doc=json.loads(chunks[0][1].decode().rstrip(' \0'))
    if not doc.get('meshes'):raise ValueError('GLB has no geometry')
    if len(doc.get('buffers',[]))!=1 or doc['buffers'][0].get('uri'):raise ValueError('Use one embedded binary buffer')
    binaries=[c[1] for c in chunks if c[0]==0x004E4942]
    if len(binaries)!=1 or doc['buffers'][0]['byteLength']>len(binaries[0]):raise ValueError('Missing or truncated embedded binary data')
    for view in doc.get('bufferViews',[]):
        if view.get('buffer',0)!=0 or view.get('byteOffset',0)<0 or view.get('byteOffset',0)+view['byteLength']>len(binaries[0]):raise ValueError('Buffer view is out of range')
    faces=0
    for mesh in doc['meshes']:
        for primitive in mesh['primitives']:
            if primitive.get('mode',4)!=4:raise ValueError('Use triangle primitives')
            accessor=primitive.get('indices',primitive.get('attributes',{}).get('POSITION'))
            if accessor is None:raise ValueError('Primitive has no position data')
            faces+=doc['accessors'][accessor]['count']//3
    if faces>200000:raise ValueError('Model exceeds 200,000 triangles; simplify before mobile use')
    for image in doc.get('images',[]):
        if image.get('uri') or 'bufferView' not in image:raise ValueError('Textures must be embedded')
        view=doc['bufferViews'][image['bufferView']];start=view.get('byteOffset',0)
        payload=binaries[0][start:start+view['byteLength']]
        if image.get('mimeType') in ['image/png','image/jpeg','image/webp']:
            with Image.open(BytesIO(payload)) as picture:
                if max(picture.size)>4096:raise ValueError('Texture exceeds 4096px; optimize before mobile use')
    variants=[v['name'] for v in doc.get('extensions',{}).get('KHR_materials_variants',{}).get('variants',[])]
    return data,hashlib.sha256(data).hexdigest(),faces,variants

def main():
    parser=argparse.ArgumentParser();parser.add_argument('manifest',type=Path);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    entries=json.loads(args.manifest.read_text(encoding='utf-8-sig'))
    config=environment_config();db=database(config);validated=[];seen=set();hashes={}
    try:
        with db.cursor() as cur:
            for entry in entries:
                if entry.get('reviewed') is not True:raise ValueError('Each binding requires a completed visual/model review')
                for key in ['productId','colorwayId','file','license','licenseUrl','sourceUrl','copyright']:
                    if not entry.get(key):raise ValueError('Missing '+key)
                for key in ['licenseUrl','sourceUrl']:
                    if urlparse(entry[key]).scheme not in ['https','http']:raise ValueError('Provide a verifiable source/license URL')
                if any(word in entry['license'].lower() for word in ['noncommercial','non-commercial','by-nc','editorial']):raise ValueError('License is not suitable for this commerce catalog')
                if entry['colorwayId'] in seen:raise ValueError('Duplicate colorway in manifest')
                seen.add(entry['colorwayId'])
                cur.execute("SELECT p.category FROM catalog_colorways c JOIN catalog_products p ON p.id=c.product_id WHERE c.id=%s AND c.product_id=%s",(entry['colorwayId'],entry['productId']))
                row=cur.fetchone()
                if not row or row[0]!='鞋类':raise ValueError('Unknown shoe/colorway mapping')
                path=(args.manifest.parent/entry['file']).resolve()
                data,sha,faces,variants=validate_glb(path)
                variant=entry.get('variantName') or None
                if variant and variant not in variants:raise ValueError('Material variant does not exist in GLB')
                previous=hashes.get((sha,variant))
                if previous and previous!=entry['colorwayId'] and not entry.get('reviewedSharedAppearance'):
                    raise ValueError('Same model/material assigned to different colorways without explicit shared-appearance review')
                hashes[(sha,variant)]=entry['colorwayId']
                validated.append((entry,data,sha,faces,variant))
        print(json.dumps({'mode':'APPLY' if args.apply else 'DRY_RUN','bindings':len(validated),'uniqueAssets':len({v[2] for v in validated}),'bytes':sum(len(v[1]) for v in validated)}),flush=True)
        if not args.apply:return
        s3=boto3.client('s3',endpoint_url=config['s3_endpoint'],aws_access_key_id=config['s3_access_key'],aws_secret_access_key=config['s3_secret_key'],region_name='us-east-1',config=Config(signature_version='s3v4',s3={'addressing_style':'path'}))
        with db.cursor() as cur:
            for entry,data,sha,faces,variant in validated:
                key='models/'+sha+'.glb'
                s3.put_object(Bucket='lectus-assets',Key=key,Body=data,ContentType='model/gltf-binary',CacheControl='public,max-age=31536000,immutable',Metadata={'sha256':sha})
                cur.execute("UPDATE catalog_models SET status='READY',object_key=%s,sha256=%s,byte_size=%s,license_name=%s,license_url=%s,source_url=%s,copyright_name=%s,material_variant=%s,reviewed=TRUE WHERE product_id=%s AND colorway_id=%s",(key,sha,len(data),entry['license'],entry['licenseUrl'],entry['sourceUrl'],entry['copyright'],variant,entry['productId'],entry['colorwayId']))
                if cur.rowcount!=1:raise ValueError('Model slot was not found; import the shoe catalog first')
            cur.execute("UPDATE catalog_meta SET value_text=%s WHERE id='revision'",(str(time.time_ns()),))
        db.commit();print('Reviewed model bindings committed; catalog cache revision advanced.')
    except Exception:db.rollback();raise
    finally:db.close()

if __name__=='__main__':main()

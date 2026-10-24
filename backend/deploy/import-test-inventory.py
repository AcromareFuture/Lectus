"""Explicitly initialize test stock from imported catalog; never resets stock/prices.
Run on the deployment host after taking the standard member database backup.
"""
import json
import os
import subprocess
from datetime import datetime

def pg(database, query):
    r = subprocess.run(['runuser','-u','postgres','--','psql','-XqAt','-v','ON_ERROR_STOP=1','-d',database],input=query,stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
    if r.returncode:
        raise RuntimeError('Inventory database operation failed')
    return r.stdout.strip()

def q(value):
    return 'NULL' if value is None else "'" + str(value).replace("'", "''") + "'"

prices = {p['id']: p['priceCents'] for p in json.load(open('/opt/lectus-saas/config/test-prices.json'))['products']}
rows = json.loads(pg('lectus_saas_catalog', """SELECT json_agg(t) FROM (
 SELECT s.id,s.product_id,s.specs_json,p.name,p.category,c.name color,
 COALESCE(i.thumb_key,pi.thumb_key) image FROM catalog_skus s
 JOIN catalog_products p ON p.id=s.product_id
 JOIN catalog_colorways c ON c.id=s.colorway_id
 LEFT JOIN catalog_images i ON i.id=s.main_image_id AND i.status='ok'
 LEFT JOIN catalog_images pi ON pi.id=p.main_image_id AND pi.status='ok'
 WHERE p.state<>'HIDDEN' ORDER BY s.id) t;"""))
assert len({r['product_id'] for r in rows}) == 250
assert all(r['product_id'] in prices for r in rows)
stamp = datetime.utcnow().strftime('%Y%m%dT%H%M%SZ')
with open('/opt/lectus-saas/backups/member-before-inventory-' + stamp + '.sql','w') as f:
    os.chmod(f.name,0o600)
    subprocess.check_call(['runuser','-u','postgres','--','pg_dump','lectus_saas_member'],stdout=f)
sql = ['BEGIN;', 'ALTER TABLE lc_product ADD COLUMN IF NOT EXISTS source_product_id VARCHAR(32);', 'CREATE INDEX IF NOT EXISTS lc_product_source ON lc_product(source_product_id);', 'ALTER TABLE lc_product ALTER COLUMN name TYPE VARCHAR(300);', 'ALTER TABLE lc_order ALTER COLUMN product_name TYPE VARCHAR(300);']
for r in rows:
    specs = json.loads(r['specs_json'])
    spec = ' · '.join(str(v) for v in specs.values() if v is not None)
    if r['color'] and r['color'] not in spec:
        spec = r['color'] + (' · ' + spec if spec else '')
    spec = spec or '标准款'
    assert len(spec) <= 150 and len(r['name']) <= 300
    image = '/api/catalog/assets/' + r['image'] if r['image'] else 'product-shirt-blue-480.webp'
    assert len(image) <= 150
    values = [r['id'],'M-1001',r['name'],spec,r['category'],image,prices[r['product_id']],20,'销售中','2026-10-23',r['product_id']]
    sql.append('INSERT INTO lc_product(id,merchant_id,name,spec,category,image,price_fen,stock,status,submitted,source_product_id) VALUES (' + ','.join(q(v) for v in values) + ') ON CONFLICT(id) DO NOTHING;')
# Exact display editions get their own test inventory, independent of source SKUs.
for id,name,spec,image,price in [('wp-kobe','Nike Kobe 5 Protro Dodgers','道奇蓝','product-kobe-480.webp',149900),('wp-samba','adidas Samba OG','白黑','product-samba-480.webp',79900)]:
    for size in ['38','39','40','41','42','43','44']:
        v=[id+'-'+size,'M-1001',name,spec+' · '+size,'鞋履',image,price,20,'销售中','2026-10-23',id]
        sql.append('INSERT INTO lc_product(id,merchant_id,name,spec,category,image,price_fen,stock,status,submitted,source_product_id) VALUES ('+','.join(q(x) for x in v)+') ON CONFLICT(id) DO NOTHING;')
sql.append('COMMIT;')
pg('lectus_saas_member','\n'.join(sql))
print('Test inventory linked:', len(rows), 'source SKUs across 250 products; existing stock retained.')

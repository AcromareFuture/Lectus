"""Atomic application release; retains previous release and rollback target."""
import os
import subprocess
import tarfile
import time
import urllib.request
from pathlib import Path

base = Path('/opt/lectus-saas')
current = (base/'current').resolve()
release = base/'releases'/('mall-' + time.strftime('%Y%m%dT%H%M%SZ',time.gmtime()))
subprocess.check_call(['cp','-al',str(current),str(release)])
with tarfile.open(str(base/'config/mall-ui-patch.tar.gz')) as tar:
    entries=tar.getmembers()
    for entry in entries:
        assert entry.isfile() and entry.name.startswith('web/')
        assert '..' not in Path(entry.name).parts
        dest=release/entry.name
        if dest.exists():
            dest.unlink()  # Break hard links before replacing files.
    tar.extractall(str(release))
jar = release/'backend/member.jar'
jar.unlink()
import shutil
shutil.copyfile(str(base/'config/member-next.jar'),str(jar))
subprocess.check_call(['chown','-R','lectus-saas:lectus-saas',str(release)])
# Fix only untouched display test SKUs to the already published reference prices.
query="""UPDATE lc_product SET price_fen=149900 WHERE source_product_id='wp-kobe' AND price_fen=179900
AND NOT EXISTS(SELECT 1 FROM lc_order WHERE product_id=lc_product.id);
UPDATE lc_product SET price_fen=79900 WHERE source_product_id='wp-samba' AND price_fen=89900
AND NOT EXISTS(SELECT 1 FROM lc_order WHERE product_id=lc_product.id);"""
subprocess.check_call(['runuser','-u','postgres','--','psql','-Xq','-v','ON_ERROR_STOP=1','-d','lectus_saas_member','-c',query],stdout=subprocess.DEVNULL)
temp=base/'next'
if temp.is_symlink(): temp.unlink()
temp.symlink_to(release)
os.replace(str(temp),str(base/'current'))
subprocess.check_call(['systemctl','restart','lectus-saas-member'])
ready=False
for attempt in range(30):
    try:
        with urllib.request.urlopen('http://127.0.0.1:18181/api/mall/public/products/SPU-000FE86E738A/variants',timeout=2) as r:
            if r.status==200: ready=True;break
    except Exception: time.sleep(1)
if not ready:
    temp.symlink_to(current)
    os.replace(str(temp),str(base/'current'))
    subprocess.check_call(['systemctl','restart','lectus-saas-member'])
    raise RuntimeError('Member health failed; application release rolled back')
print('Published:', release.name, 'previous release retained:', current.name)

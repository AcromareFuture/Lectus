"""Publish community API/UI with a DB backup and automatic service rollback."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import time
import urllib.request

base = Path('/opt/lectus-saas')
stamp = time.strftime('%Y%m%dT%H%M%SZ', time.gmtime())
previous = (base / 'current').resolve()
release = base / 'releases' / ('community-' + stamp)
backup = base / 'backups' / ('before-community-' + stamp)
backup.mkdir(mode=0o700, parents=True)
with (backup / 'member.dump').open('wb') as out:
    subprocess.check_call(['runuser', '-u', 'postgres', '--', 'pg_dump', '-Fc', 'lectus_saas_member'], stdout=out)
os.chmod(backup / 'member.dump', 0o600)
nginx = Path('/etc/nginx/conf.d/lectusglobal.conf')
shutil.copy2(nginx, backup / 'nginx.conf')
def core_counts():
    query = "SELECT (SELECT count(*) FROM v_member), (SELECT count(*) FROM v_order), (SELECT count(*) FROM v_order_item), (SELECT count(*) FROM v_goods_sku)"
    return subprocess.check_output(['runuser', '-u', 'postgres', '--', 'psql', '-d', 'lectus_saas_member', '-Atc', query], universal_newlines=True, cwd='/tmp').strip()

before_counts = core_counts()
(backup / 'core-counts.txt').write_text(before_counts + '\n')
subprocess.check_call(['cp', '-al', str(previous), str(release)])
with tarfile.open(base / 'config/community-ui.tar.gz') as archive:
    index = None
    for entry in archive.getmembers():
        assert entry.isfile() and entry.name.startswith('web/') and '..' not in Path(entry.name).parts
        if entry.name == 'web/index.html':
            index = archive.extractfile(entry).read()
            assert b'/desktop-assets/' in index and b'/lectus/desktop-assets/' not in index
            continue
        target = release / entry.name
        if target.exists():
            target.unlink()
        archive.extract(entry, str(release))
    assert index is not None
    target = release / 'web/index.html'
    target.unlink()
    target.write_bytes(index)
for name in ['member', 'gateway']:
    target = release / 'backend' / (name + '.jar')
    target.unlink()
    shutil.copyfile(base / 'config' / ('community-' + name + '.jar'), target)
subprocess.check_call(['chown', '-R', 'lectus-saas:lectus-saas', str(release)])

def switch(target):
    link = base / 'community-next'
    if link.is_symlink():
        link.unlink()
    link.symlink_to(target)
    os.replace(link, base / 'current')

def ready(url):
    for _ in range(40):
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                body = json.load(response)
                if response.status == 200 and body.get('success') and 'items' in body.get('data', {}):
                    return
        except Exception:
            time.sleep(1)
    raise RuntimeError('Community API health check failed: ' + url)

try:
    switch(release)
    subprocess.check_call(['systemctl', 'restart', 'lectus-saas-member'])
    ready('http://127.0.0.1:18181/api/community/public/feed')
    print('Member ready', flush=True)
    subprocess.check_call(['systemctl', 'restart', 'lectus-saas-gateway'])
    ready('http://127.0.0.1:18180/api/community/public/feed')
    print('Gateway ready', flush=True)
    print('Existing members/orders/items/SKUs before:', before_counts, 'after:', core_counts(), flush=True)
    shutil.copyfile(base / 'config/community-nginx.conf', nginx)
    subprocess.check_call(['nginx', '-t'])
    subprocess.check_call(['systemctl', 'reload', 'nginx'])
except Exception:
    switch(previous)
    shutil.copyfile(backup / 'nginx.conf', nginx)
    subprocess.run(['nginx', '-t'], check=True)
    subprocess.run(['systemctl', 'reload', 'nginx'], check=True)
    for name in ['member', 'gateway']:
        subprocess.run(['systemctl', 'restart', 'lectus-saas-' + name], check=True)
    raise
print('Published:', release.name, 'previous:', previous.name, 'backup:', backup.name, flush=True)

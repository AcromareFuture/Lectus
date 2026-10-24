"""Publish a validated storefront-only archive; retain previous release for rollback."""
import os
import re
import subprocess
import sys
import tarfile
import time
import urllib.request
from pathlib import Path

base = Path('/opt/lectus-saas')
archive = Path(sys.argv[1]).resolve()
if archive.parent != base / 'config' or not archive.name.endswith('.tar.gz'):
    raise ValueError('Archive must be an explicit .tar.gz in the deployment config directory')
current = (base / 'current').resolve()
if current.parent != base / 'releases':
    raise ValueError('Unexpected current release path')
release = base / 'releases' / ('interaction-' + time.strftime('%Y%m%dT%H%M%SZ', time.gmtime()))

with tarfile.open(str(archive)) as tar:
    entries = tar.getmembers()
    names = {entry.name for entry in entries}
    for entry in entries:
        parts = Path(entry.name).parts
        if not entry.isfile() or '..' in parts or not (entry.name == 'web/index.html' or entry.name.startswith('web/desktop-assets/')):
            raise ValueError('Unexpected archive entry: ' + entry.name)
    page = tar.extractfile('web/index.html').read().decode('utf-8')
    assets = re.findall(r'(?:src|href)="(/desktop-assets/[^"?]+)"', page)
    if not assets or '/lectus/desktop-assets/' in page or any('web' + asset not in names for asset in assets):
        raise ValueError('Storefront must reference bundled root-relative assets')
    subprocess.check_call(['cp', '-al', str(current), str(release)])
    for entry in entries:
        dest = release / entry.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.exists():
            dest.unlink()  # Never overwrite a hard link to the previous release.
        with tar.extractfile(entry) as source, dest.open('wb') as target:
            target.write(source.read())
    subprocess.check_call(['chown', '-R', 'lectus-saas:lectus-saas', str(release)])

def switch(target):
    temporary = base / 'next-storefront'
    if temporary.is_symlink():
        temporary.unlink()
    temporary.symlink_to(target)
    os.replace(str(temporary), str(base / 'current'))

switch(release)
try:
    with urllib.request.urlopen('http://127.0.0.1:18180/api/health', timeout=8) as response:
        if response.status != 200:
            raise RuntimeError('Gateway health failed')
    if (base / 'current/web/index.html').read_bytes().decode('utf-8') != page:
        raise RuntimeError('Published page differs from validated archive')
except Exception:
    switch(current)
    raise
print('Published:', release.name, 'Previous:', current.name, 'Backend and database unchanged')

#!/usr/bin/python3
"""Certbot manual DNS hook. Publish the displayed TXT via the domain console."""
from pathlib import Path
import os
import json
import time

root=Path('/opt/lectus-saas/acme')
value=os.environ['CERTBOT_VALIDATION']
challenge={'name':'_acme-challenge.'+os.environ['CERTBOT_DOMAIN'],'type':'TXT','value':value}
(root/'dns-challenge.json').write_text(json.dumps(challenge))
print(json.dumps(challenge),flush=True)
marker=root/('dns-ready-'+value)
for attempt in range(180):
 if marker.exists():
  print('DNS confirmed by operator',flush=True)
  break
 time.sleep(5)
else:
 raise RuntimeError('DNS confirmation timed out; no certificate issued')

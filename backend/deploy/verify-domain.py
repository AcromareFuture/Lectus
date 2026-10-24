"""Read-only domain, static asset and role-boundary checks on the deployment host.
Uses the existing isolated QA credentials; never prints credentials or tokens.
"""
from pathlib import Path
from urllib.request import Request, urlopen, build_opener, HTTPRedirectHandler
from urllib.error import HTTPError
from urllib.parse import urljoin
import json
import re
import ssl
import socket

ORIGIN = 'https://www.lectusglobal.space'
credentials = json.loads(Path('/opt/lectus-saas/config/qa-credentials.json').read_text())
checks = []

def request(path, body=None, token=None, method=None):
 headers = {'Origin': ORIGIN, 'Content-Type': 'application/json'}
 if token: headers['Authorization'] = 'Bearer ' + token
 req = Request(urljoin(ORIGIN, path), data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method or ('POST' if body is not None else 'GET'))
 try:
  with urlopen(req, timeout=20) as response:
   return response.status, response.headers, response.read()
 except HTTPError as error:
  return error.code, error.headers, error.read()

def api(path, body=None, token=None):
 status, _, raw = request('/api/' + path, body, token)
 assert status == 200, (path, status)
 result = json.loads(raw)
 assert result['success'], path
 return result['data']

for path in ['/', '/merchant/', '/platform/']:
 status, headers, raw = request(path)
 assert status == 200 and 'text/html' in headers['Content-Type']
 assert headers['Cache-Control'] == 'no-store'
 assets = re.findall(r'(?:src|href)="([^"]+\.(?:js|css))"', raw.decode())
 assert assets
 for asset in assets:
  code, asset_headers, body = request(urljoin(path, asset))
  assert code == 200 and len(body) > 50, asset
  assert 'text/html' not in asset_headers['Content-Type'], asset
 checks.append({'entry': path, 'status': status, 'assets': len(assets)})
assert request('/merchant/assets/missing-test.js')[0] == 404
assert request('/platform/assets/missing-test.js')[0] == 404

class NoRedirect(HTTPRedirectHandler):
 def redirect_request(self, *args): return None
try:
 build_opener(NoRedirect).open('http://www.lectusglobal.space/platform/', timeout=15)
 raise AssertionError('Missing HTTPS redirect')
except HTTPError as error:
 assert error.code == 301 and error.headers['Location'] == ORIGIN + '/platform/'
for path in ['/merchant', '/platform']:
 with urlopen(ORIGIN + path, timeout=15) as response:
  assert response.geturl() == ORIGIN + path + '/'
with socket.create_connection(('www.lectusglobal.space', 443), timeout=15) as raw_socket:
 with ssl.create_default_context().wrap_socket(raw_socket, server_hostname='www.lectusglobal.space') as tls:
  checks.append({'tls': tls.version(), 'expires': tls.getpeercert()['notAfter']})

assert request('/api/mall/workspace/snapshot')[0] == 401
assert request('/api/mall/orders')[0] == 401
merchant = api('mall/workspace/login', {'username': 'merchant', 'password': credentials['staffPassword']})['token']
platform = api('mall/workspace/login', {'username': 'platform', 'password': credentials['staffPassword']})['token']
try:
 own = api('mall/workspace/snapshot', token=merchant)
 all_records = api('mall/workspace/snapshot', token=platform)
 assert all(o['merchantId'] == 'M-1001' for o in own['orders'])
 assert all(p['merchantId'] == 'M-1001' for p in own['products'])
 other = next(o for o in all_records['orders'] if o['merchantId'] != 'M-1001')
 assert request('/api/mall/workspace/orders/' + other['id'] + '/ship', {'carrier': '顺丰速运', 'tracking': 'SFTESTDENIED'}, merchant)[0] == 403
 assert request('/api/mall/workspace/merchants/M-1002/action', {'action': 'disable', 'reason': '权限边界测试'}, merchant)[0] == 403
 checks.append({'authorization': 'PASS', 'anonymous': 401, 'crossMerchant': 403, 'platformRoleRequired': 403})
finally:
 for token in [merchant, platform]: api('mall/workspace/logout', {}, token)

status, _, raw = request('/api/catalog/products?size=1')
assert status == 200
catalog = json.loads(raw)
assert catalog['items']
image = catalog['items'][0]['image']
if image:
 status, headers, body = request('/api/catalog/assets/' + image)
 assert status == 200 and headers['Content-Type'].startswith('image/') and len(body) > 100
 checks.append({'catalogImageProxy': 'PASS', 'contentType': headers['Content-Type']})
report = {'result': 'PASS', 'checks': checks}
Path('/opt/lectus-saas/data/online-domain-checks.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
print(json.dumps(report, ensure_ascii=False))

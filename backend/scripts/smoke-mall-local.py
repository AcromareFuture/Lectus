"""Exercise the local-only mall workflow through the gateway; keeps labeled test orders."""
import json
import os
import secrets
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE = os.environ.get('LECTUS_SMOKE_BASE', 'http://127.0.0.1:8080/api/').rstrip('/') + '/'
PUBLIC_ORIGIN = os.environ.get('LECTUS_SMOKE_ORIGIN', 'http://127.0.0.1:4206')
TOKEN_ORIGINS = {}

def api(path, body=None, token=None, method=None):
    origin = TOKEN_ORIGINS.get(token, PUBLIC_ORIGIN)
    if path == 'mall/workspace/login' and PUBLIC_ORIGIN.startswith('http://127.0.0.1:'):
        origin = 'http://127.0.0.1:' + ('4208' if body['username'] == 'platform' else '4207')
    headers = {'Content-Type': 'application/json', 'Origin': origin}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method or ('POST' if body is not None else 'GET'))
    try:
        with urlopen(request, timeout=15) as response:
            result = json.load(response)
    except HTTPError as exc:
        raise RuntimeError(f'{path}: HTTP {exc.code}') from None
    if not result.get('success'):
        raise RuntimeError(f'{path}: {result.get("message")}')
    data = result.get('data')
    if path == 'mall/workspace/login':
        TOKEN_ORIGINS[data['token']] = origin
    return data

assert api('mall/public/config')['localSimulation'], 'Requires local simulation profile'
username = os.environ.get('LECTUS_SMOKE_USER', 'workflow_demo')
password = os.environ.get('LECTUS_SMOKE_PASSWORD', 'Lectus@2026')
staff_password = os.environ.get('LECTUS_SMOKE_STAFF_PASSWORD', password)
try:
    member = api('member/login', {'username': username, 'password': password})
except RuntimeError:
    member = api('member/register', {'username': username, 'password': password, 'nickname': '流程体验用户'})
user_token = member['token']
merchant = api('mall/workspace/login', {'username': 'merchant', 'password': staff_password})['token']
platform = api('mall/workspace/login', {'username': 'platform', 'password': staff_password})['token']
product = next(p for p in api('mall/public/products') if p['merchantId'] == 'M-1001' and p['stock'] >= 3)

def create():
    return api('mall/orders', {'productId': product['id'], 'quantity': 1, 'requestKey': secrets.token_hex(16), 'recipient': '测试收货人', 'phone': '13800000000', 'address': '浙江省杭州市 测试街道 1 号（仅限本地流程测试）'}, user_token)

def act(order, action, reason=''):
    return api('mall/orders/' + order['id'] + '/action', {'action': action, 'reason': reason}, user_token)

def snapshot(token):
    return api('mall/workspace/snapshot', token=token)

order = create()
assert order['expiresAt'] and order['status'] == '待付款'
paid = act(order, 'pay', '支付宝')
assert paid['amount'] == product['price'] and paid['paymentRef'].startswith('TEST-P-')
assert act(order, 'pay', '支付宝')['paymentRef'] == paid['paymentRef']
api('mall/workspace/orders/' + order['id'] + '/ship', {'carrier': '顺丰速运', 'tracking': 'SFTEST' + secrets.token_hex(6).upper()}, merchant)
for stage in range(3):
    api('mall/workspace/orders/' + order['id'] + '/delivery', {'expectedStage': stage}, merchant)
completed = act(order, 'receive')
assert completed['status'] == '已完成' and completed['deliveryStage'] == 4
assert len(completed['events']) == 7
for token in [merchant, platform]:
    view = next(o for o in snapshot(token)['orders'] if o['id'] == order['id'])
    assert view['status'] == completed['status'] and view['paymentRef'] == paid['paymentRef']
    assert view['events'] == completed['events']

refund_order = create()
act(refund_order, 'pay', '微信支付')
requested = act(refund_order, 'refund', '本地测试：需要更换商品规格')
after_id = requested['aftersale']['id']
api('mall/workspace/aftersales/' + after_id + '/action', {'action': 'approve', 'reason': '本地测试：审核通过，按原支付方式退回'}, platform)
refunded = next(o for o in api('mall/orders', token=user_token) if o['id'] == refund_order['id'])
assert refunded['status'] == '已退款' and refunded['aftersale']['refundRef'].startswith('TEST-R-')
assert refunded['aftersale']['paymentMethod'] == '微信支付'
for token in [merchant, platform]:
    view = next(o for o in snapshot(token)['orders'] if o['id'] == refunded['id'])
    assert view['aftersale']['refundRef'] == refunded['aftersale']['refundRef']

cancelled = act(create(), 'cancel')
assert cancelled['closedAt'] and cancelled['status'] == '已取消'
# Cross-store cart, atomic split checkout, combined payment and receipt persistence.
other_product = next(p for p in api('mall/public/products') if p['merchantId'] != product['merchantId'] and p['stock'] >= 1)
for item in api('mall/cart', token=user_token):
    api('mall/cart/items/' + item['id'], token=user_token, method='DELETE')
for p in [product, other_product]:
    api('mall/cart/items', {'productId': p['id'], 'quantity': 1}, user_token)
cart = api('mall/cart', token=user_token)
assert len(cart) == 2
form = {'requestKey': secrets.token_hex(16), 'items': [{'productId': i['id'], 'quantity': i['quantity'], 'expectedPrice': i['price']} for i in cart], 'recipient': '跨店结算测试', 'phone': '13800000000', 'address': '浙江省杭州市 测试街道 2 号（仅限线上测试）'}
group = api('mall/checkouts', form, user_token)
assert len(group['orders']) == 2 and group['status'] == 'pending'
assert not api('mall/cart', token=user_token)
assert api('mall/checkouts', form, user_token)['id'] == group['id']
payment = {'method': '支付宝', 'expectedAmount': group['pendingAmount']}
paid_group = api('mall/checkouts/' + group['id'] + '/pay', payment, user_token)
assert paid_group['status'] == 'paid' and paid_group['pendingAmount'] == 0
again = api('mall/checkouts/' + group['id'] + '/pay', payment, user_token)
assert {o['paymentRef'] for o in again['orders']} == {o['paymentRef'] for o in paid_group['orders']}
platform_orders = {o['id']: o for o in snapshot(platform)['orders']}
for order_item in paid_group['orders']:
    assert platform_orders[order_item['id']]['paymentRef'] == order_item['paymentRef']

for token in [merchant, platform]:
    api('mall/workspace/logout', {}, token)
api('member/logout', {}, user_token)
print(json.dumps({'result': 'PASS', 'flows': ['pay/duplicate-pay/ship/delivery/receive', 'refund/approve/receipt', 'cancel', 'three-role consistency', 'cross-store cart/atomic checkout/combined payment/retry'], 'demoUser': username, 'checkout': group['id'], 'completedOrder': order['id'], 'refundedOrder': refunded['id']}, ensure_ascii=False))

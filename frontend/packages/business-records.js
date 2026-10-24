// Historical system messages are adapted for display; stored business records are not rewritten.
export function eventDetail(event) {
  const detail = businessNote(event.detail);
  if (event.key === 'paid' && detail.includes('测试通道')) return '支付记录已更新，等待商家处理。';
  if (event.key === 'refunded' && detail.includes('测试通道')) return '退款处理记录已更新。';
  if (event.key?.startsWith('delivery-')) return detail.replace('（测试轨迹）', '');
  return detail;
}
export function auditAction(action) {
  return ({ '退款通过（测试通道）': '退款审核通过', '更新测试物流': '更新配送进度', '订单refund': '申请退款', '订单cancel': '取消订单', '订单receive': '确认收货', '订单pay': '确认付款' })[action] || action;
}
export function transactionReference(value) {
  return typeof value === 'string' && value.startsWith('TEST-') ? value.slice(5) : value;
}

const serviceMessages = new Map([
  ['模拟订单不存在', '未找到这笔订单，请返回订单列表。'],
  ['该商品暂不能模拟购买', '这件商品暂不可购买，请选择其他商品。'],
  ['请先完成或取消待支付的模拟订单', '请先完成或取消待付款订单。'],
  ['仅本地联调允许模拟支付', '支付服务暂不可用，请稍后再试。'],
  ['物流测试仅在本地环境开放', '配送进度暂时无法更新，请稍后再试。'],
]);

// Translate known service messages only. Preserve customer names, reasons and notes.
export function serviceMessage(message) {
  return serviceMessages.get(message) || message;
}

// Only these two known QA notes are adapted; arbitrary customer text stays intact.
export function businessNote(value = '') {
  return value
    .replace('本地测试：需要更换商品规格', '需要更换商品规格')
    .replace('本地测试：审核通过，按原支付方式退回', '审核通过，按原支付方式退回');
}

export const accountOrderGroups = [
  { id: 'all', label: '全部订单' },
  { id: 'pending', label: '待扫码' },
  { id: 'completed', label: '已完成' },
  { id: 'closed', label: '已关闭' },
];
export function filterAccountOrders(orders, group) {
  if (group === 'pending') return orders.filter(order => order.state === 'PENDING');
  if (group === 'completed') return orders.filter(order => order.state === 'PAID');
  if (group === 'closed') return orders.filter(order => ['CANCELLED', 'EXPIRED'].includes(order.state));
  return orders;
}

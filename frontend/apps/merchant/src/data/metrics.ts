import type { Order, Period } from './demo';

export function orderMetrics(orders: Order[], period: Period, now = new Date()) {
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
  const end = Date.parse(today + 'T00:00:00Z');
  const start = period === 'week' ? end - 6 * 86400000 : Date.parse((period === 'month' ? today.slice(0, 7) + '-01' : today.slice(0, 4) + '-01-01') + 'T00:00:00Z');
  const count = Math.round((end - start) / 86400000) + 1;
  const bucket = period === 'week' ? 1 : Math.ceil(count / 12);
  const size = Math.ceil(count / bucket);
  const values = Array<number>(size).fill(0), orderCounts = Array<number>(size).fill(0), previous = Array<number>(size).fill(0), previousCounts = Array<number>(size).fill(0);
  const selected: Order[] = [];
  for (const order of orders) {
    if (!['待发货', '运输中', '已完成', '退款中'].includes(order.status)) continue;
    const day = order.paidAt ? new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(order.paidAt)) : order.time.slice(0, 10);
    const offset = Math.round((Date.parse(day + 'T00:00:00Z') - start) / 86400000);
    if (offset >= 0 && offset < count) { const index = Math.floor(offset / bucket); values[index]! += Math.round(order.amount * 100); orderCounts[index]!++; selected.push(order); }
    else if (offset >= -count && offset < 0) { const index = Math.floor((offset + count) / bucket); previous[index]! += Math.round(order.amount * 100); previousCounts[index]!++; }
  }
  return {
    label: ({ week: '近 7 天', month: '本月', year: '今年' })[period], dates: new Date(start).toISOString().slice(0, 10) + ' 至 ' + today,
    labels: Array.from({ length: size }, (_, i) => new Date(start + i * bucket * 86400000).toISOString().slice(5, 10)),
    values: values.map(value => value / 100), previous: previous.map(value => value / 100), orderCounts, previousCounts,
    sales: values.reduce((sum, value) => sum + value, 0) / 100, orders: selected.length, selected,
  };
}

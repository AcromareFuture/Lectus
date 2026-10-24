export type Page = 'overview' | 'products' | 'orders' | 'marketing' | 'finance' | 'settings';
export type Period = 'week' | 'month' | 'year';
export type OrderStatus = '待发货' | '运输中' | '已完成' | '退款中' | '待付款' | '已取消' | '已退款';
export interface Product {
  id: string; name: string; subtitle: string; category: string; image: string;
  price: number; stock: number; sales: number; status: '销售中' | '已下架' | '草稿' | '待审核' | '已驳回';
}
export interface Order {
  paymentMethod?: string; paymentRef?: string; paidAt?: string; shippedAt?: string; completedAt?: string; closeReason?: string; deliveryStage?: number; events?: { key: string; title: string; detail: string; time: string }[];
  id: string; productId: string; customer: string; variant: string; amount: number;
  count: number; status: OrderStatus; time: string; carrier?: string; tracking?: string; productName?: string; image?: string; recipient?: string; phone?: string; address?: string;
}

export const productsSeed: Product[] = [
  { id: 'LC-1001', name: 'ASICS GEL-KAYANO 14', subtitle: '复古跑鞋 · 银绿', category: '鞋履', image: 'product-asics-480.webp', price: 1090, stock: 128, sales: 186, status: '销售中' },
  { id: 'LC-1002', name: 'adidas SAMBA OG', subtitle: '经典低帮板鞋 · 黑白', category: '鞋履', image: 'product-samba-480.webp', price: 799, stock: 86, sales: 142, status: '销售中' },
  { id: 'LC-1003', name: 'New Balance 9060', subtitle: '休闲运动鞋 · 云雨灰', category: '鞋履', image: 'product-nb9060-480.webp', price: 1199, stock: 18, sales: 98, status: '销售中' },
  { id: 'LC-1004', name: '莱择 日常条纹衬衫', subtitle: '宽松棉质衬衫 · 雾蓝', category: '服饰', image: 'product-shirt-blue-480.webp', price: 369, stock: 64, sales: 76, status: '销售中' },
  { id: 'LC-1005', name: 'Calvin Klein CK ONE', subtitle: '中性淡香水 · 100ml', category: '生活方式', image: 'product-ck-one-480.webp', price: 299, stock: 0, sales: 52, status: '已下架' },
];

export const ordersSeed: Order[] = [
  { id: 'LC202610220018', productId: 'LC-1001', customer: '林**', variant: '银绿 / 42', amount: 1090, count: 1, status: '待发货', time: '2026-10-22 10:42' },
  { id: 'LC202610220017', productId: 'LC-1002', customer: '陈**', variant: '黑白 / 38', amount: 799, count: 1, status: '待发货', time: '2026-10-22 10:26' },
  { id: 'LC202610220016', productId: 'LC-1004', customer: '周**', variant: '雾蓝 / M', amount: 738, count: 2, status: '运输中', time: '2026-10-22 09:58', carrier: '顺丰速运', tracking: 'SF-DEMO-0016' },
  { id: 'LC202610220015', productId: 'LC-1003', customer: '许**', variant: '云雨灰 / 41', amount: 1199, count: 1, status: '已完成', time: '2026-10-22 09:37' },
  { id: 'LC202610210094', productId: 'LC-1001', customer: '张**', variant: '银绿 / 40', amount: 1090, count: 1, status: '退款中', time: '2026-10-21 21:08' },
  { id: 'LC202610210093', productId: 'LC-1004', customer: '沈**', variant: '雾蓝 / L', amount: 369, count: 1, status: '已完成', time: '2026-10-21 20:41' },
];

export const periodData = {
  week: { label: '近 7 天', dates: '10 月 16 日 — 10 月 22 日', sales: 36890, orders: 42, visitors: 2486, conversion: '1.69', growth: '12.8', values: [3.2, 4.8, 3.9, 7.2, 5.3, 6.1, 6.39], labels: ['周五', '周六', '周日', '周一', '周二', '周三', '周四'], previous: [2.4, 4.1, 3.2, 5.5, 4.8, 5.8, 6.87] },
  month: { label: '本月', dates: '2026 年 10 月 1 日 — 10 月 22 日', sales: 128640, orders: 156, visitors: 8642, conversion: '1.81', growth: '18.6', values: [6.2, 8.7, 7.4, 11.6, 9.8, 13.2, 10.9, 14.3, 12.6, 16.2, 17.9, 15.4], labels: ['01', '03', '05', '07', '09', '11', '13', '15', '17', '19', '21', '22'], previous: [4.9, 7.1, 6.1, 9.1, 7.4, 10.9, 9.4, 11.1, 10.6, 12.4, 14.8, 11.7] },
  year: { label: '今年', dates: '2026 年 1 月 — 10 月', sales: 1085260, orders: 1328, visitors: 71648, conversion: '1.85', growth: '24.3', values: [62, 78, 71, 94, 86, 115, 105, 132, 139, 128.6], labels: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月'], previous: [48, 64, 60, 72, 67, 92, 81, 107, 113, 108.4] },
} satisfies Record<Period, object>;

export const money = (value: number) => value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const integer = (value: number) => value.toLocaleString('zh-CN');
export const imageUrl = (name: string) => /^\/api\/catalog\/assets\/[a-zA-Z0-9_./-]+$/.test(name) && !name.includes('..') ? name : `${import.meta.env.BASE_URL}images/${name}`;

export function downloadCsv(filename: string, rows: (string | number)[][]) {
  // Prefix formula-like fields so freeform preview names remain plain spreadsheet text.
  const escape = (value: string | number) => {
    const text = String(value);
    const safe = typeof value === 'string' && /^[=+@\-\t\r]/.test(text) ? `'${text}` : text;
    return `"${safe.replaceAll('"', '""')}"`;
  };
  const csv = '\uFEFF' + rows.map(row => row.map(escape).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url; link.download = filename; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

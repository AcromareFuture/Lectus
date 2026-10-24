import { computed, reactive, ref } from 'vue';

export type Page = 'overview' | 'merchants' | 'products' | 'orders' | 'aftersales' | 'finance' | 'settings';
export type MerchantStatus = '营业中' | '待审核' | '已驳回' | '已停用';
export type ProductStatus = '销售中' | '待审核' | '已驳回' | '已下架' | '草稿';
export type OrderStatus = '待付款' | '待发货' | '运输中' | '已完成' | '退款中' | '已退款' | '已取消';
export interface Merchant { id: string; name: string; initials: string; type: '自营' | '入驻'; category: string; company: string; contact: string; submitted: string; status: MerchantStatus; reason?: string }
export interface Product { id: string; name: string; spec: string; image: string; merchantId: string; category: string; price: number; stock: number; status: ProductStatus; submitted: string; reason?: string }
export interface Order { paymentMethod?: string; paymentRef?: string; paidAt?: string; shippedAt?: string; completedAt?: string; closeReason?: string; deliveryStage?: number; events?: { key: string; title: string; detail: string; time: string }[]; recipient?: string; phone?: string; address?: string; productName?: string; id: string; merchantId: string; productId: string; customer: string; amount: number; quantity: number; time: string; status: OrderStatus; carrier?: string; tracking?: string }
export interface AfterSale { id: string; orderId: string; reason: string; amount: number; time: string; status: '待处理' | '已处理' | '已驳回'; previousStatus: OrderStatus; resolvedAt?: string; refundRef?: string; paymentMethod?: string; result?: string }
export interface Settlement { id: string; merchantId: string; orderIds: string[]; period: string; rate: number; status: '待核对' | '已核对'; verifiedAt?: string }
export interface AuditLog { id: number; time: string; action: string; target: string; detail: string }
export type Detail = { kind: 'merchant'; item: Merchant } | { kind: 'product'; item: Product } | { kind: 'order'; item: Order } | { kind: 'aftersale'; item: AfterSale } | { kind: 'settlement'; item: Settlement };

// Independent frontend fixtures. These records never invoke merchant, payment, or logistics APIs.
export const merchants = reactive<Merchant[]>([]);
export const products = reactive<Product[]>([]);
export const orders = reactive<Order[]>([]);
export const aftersales = reactive<AfterSale[]>([]);
export const settlements = reactive<Settlement[]>([]);
export const auditLogs = reactive<AuditLog[]>([]);
export const settings = reactive({ platformName: '莱择商城', supportPhone: '', contactEmail: '', productReview: true, merchantNotice: true });
export const toast = ref('');
let timer: ReturnType<typeof setTimeout>;
export function notify(message: string) { toast.value = message; clearTimeout(timer); timer = setTimeout(() => toast.value = '', 4000); }
export const money = (value: number) => value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const merchantName = (id: string) => merchants.find(m => m.id === id)?.name || id;
export const productName = (id: string) => products.find(p => p.id === id)?.name || id;
export const imageUrl = (name: string) => /^\/api\/catalog\/assets\/[a-zA-Z0-9_./-]+$/.test(name) && !name.includes('..') ? name : `${import.meta.env.BASE_URL}images/${name}`;
export const pendingCounts = computed(() => ({ merchants: merchants.filter(m => m.status === '待审核').length, products: products.filter(p => p.status === '待审核').length, orders: orders.filter(o => o.status === '待发货').length, aftersales: aftersales.filter(a => a.status === '待处理').length, finance: settlements.filter(s => s.status === '待核对').length }));
export const paidOrders = (list: Order[]) => list.filter(o => ['待发货', '运输中', '已完成', '退款中'].includes(o.status));
export function settlementTotals(s: Settlement) {
  const gross = Math.round(orders.filter(o => s.orderIds.includes(o.id) && o.status === '已完成').reduce((sum, o) => sum + o.amount * 100, 0));
  const fee = Math.round(gross * s.rate);
  return { gross: gross / 100, fee: fee / 100, net: (gross - fee) / 100 };
}
export function addLog(action: string, target: string, detail: string) {
  const time = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());
  auditLogs.unshift({ id: Date.now(), time, action, target, detail });
}
export function badgeClass(status: string) {
  if (['营业中', '销售中', '已完成', '已核对', '已处理'].includes(status)) return 'success';
  if (['待审核', '待发货', '待处理', '待核对', '退款中'].includes(status)) return 'warning';
  if (['已驳回', '已停用', '已退款'].includes(status)) return 'danger';
  return 'neutral';
}
export function downloadCsv(name: string, rows: (string | number)[][]) {
  const content = rows.map(row => row.map(value => {
    const raw = String(value);
    const safe = /^[=+\-@\t\r]/.test(raw) ? `'${raw}` : raw;
    return `"${safe.replaceAll('"', '""')}"`;
  }).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob(['\uFEFF', content], { type: 'text/csv;charset=utf-8;' }));
  const a = document.createElement('a'); a.href = url; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}

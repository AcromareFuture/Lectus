<script setup lang="ts">
import { auditAction, businessNote } from '../../../../packages/business-records';
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
import { ArrowUpRight, Download, Store, PackageCheck, RotateCcw, Wallet, ChevronRight, ShoppingBag, ChartNoAxesCombined } from '@lucide/vue';
import { merchants, orders, pendingCounts, paidOrders, money, merchantName, productName, downloadCsv, notify, auditLogs } from '../data/platform';
import type { Detail, Page } from '../data/platform';
import TrendChart from '../components/TrendChart.vue';
const emit = defineEmits<{ navigate: [page: Page, filter?: string]; detail: [detail: Detail] }>();
const range = ref<7 | 14>(7);
const clock = ref(Date.now());
let clockTimer: ReturnType<typeof setInterval>;
onMounted(() => { clockTimer = setInterval(() => { clock.value = Date.now(); }, 60000); });
onBeforeUnmount(() => clearInterval(clockTimer));
const today = computed(() => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(clock.value));
const selectedDay = ref(today.value);
const chartMetric = ref<'amount' | 'count'>('amount');
const days = computed(() => Array.from({ length: range.value }, (_, i) => new Date(Date.parse(`${today.value}T00:00:00Z`) - (range.value - 1 - i) * 86400000).toISOString().slice(0, 10)));
const currentOrders = computed(() => orders.filter(o => o.time.slice(0, 10) >= days.value[0]! && o.time.slice(0, 10) <= today.value));
const paid = computed(() => paidOrders(currentOrders.value));
const sales = computed(() => paid.value.reduce((s, o) => s + o.amount, 0));
const pendingTotal = computed(() => pendingCounts.value.merchants + pendingCounts.value.products + pendingCounts.value.aftersales + pendingCounts.value.finance);
const series = computed(() => days.value.map(day => {
  const list = paid.value.filter(o => o.time.startsWith(day));
  return { day, amount: list.reduce((s, o) => s + o.amount, 0), count: list.length };
}));
const ranking = computed(() => merchants.map(m => {
  const list = paid.value.filter(o => o.merchantId === m.id);
  return { ...m, amount: list.reduce((s, o) => s + o.amount, 0), count: list.length };
}).filter(m => m.count > 0).sort((a, b) => b.amount - a.amount));
const todos = computed(() => [
  { page: 'merchants' as Page, name: '商家入驻审核', subtitle: '核对企业资料与经营类目', count: pendingCounts.value.merchants, filter: '待审核', icon: Store, tone: 'cyan' },
  { page: 'products' as Page, name: '商品上架审核', subtitle: '检查商品信息与提交资料', count: pendingCounts.value.products, filter: '待审核', icon: PackageCheck, tone: 'cyan' },
  { page: 'aftersales' as Page, name: '售后待处理', subtitle: '处理退款申请与交易争议', count: pendingCounts.value.aftersales, filter: '待处理', icon: RotateCcw, tone: 'gold' },
  { page: 'finance' as Page, name: '结算待核对', subtitle: '核对商家账单与应结金额', count: pendingCounts.value.finance, filter: '待核对', icon: Wallet, tone: 'rose' },
]);
function exportReport() { downloadCsv('Lectus-平台经营概览.csv', [['日期', '成交金额', '成交订单数'], ...series.value.map(s => [s.day, s.amount, s.count])]); notify('当前周期的经营报表已导出'); }
</script>

<template>
  <div class="overview-toolbar"><div class="segmented" aria-label="统计周期"><button :aria-pressed="range === 7" @click="range = 7">近 7 天</button><button :aria-pressed="range === 14" @click="range = 14">近 14 天</button></div><span class="period-caption">{{ days[0] }} 至 {{ today }}</span><button class="secondary-button" @click="exportReport"><Download :size="16" aria-hidden="true" />导出报表</button></div>
  <section class="stats-grid" aria-label="平台核心指标">
    <div class="stat"><span>成交金额<ChartNoAxesCombined :size="18" aria-hidden="true" /></span><strong><small>¥</small>{{ money(sales) }}</strong><p>已支付订单金额，扣除已退款订单</p></div>
    <div class="stat"><span>成交订单<ShoppingBag :size="18" aria-hidden="true" /></span><strong>{{ paid.length }}<small>单</small></strong><p>所选周期内已支付的订单</p></div>
    <div class="stat"><span>营业中商家<Store :size="18" aria-hidden="true" /></span><strong>{{ merchants.filter(m => m.status === '营业中').length }}<small>家</small></strong><p>平台当前营业中的自营及入驻店铺</p></div>
    <div class="stat"><span>运营待办<PackageCheck :size="18" aria-hidden="true" /></span><strong>{{ pendingTotal }}<small>项</small></strong><p>审核、售后及结算待处理事项</p></div>
  </section>

  <div class="dashboard-grid">
    <section class="panel trend-panel"><header class="section-heading"><div><h2>成交趋势</h2><p>已支付，扣除已退款订单</p></div><div class="segmented compact" aria-label="趋势指标"><button :aria-pressed="chartMetric === 'amount'" @click="chartMetric = 'amount'">成交额</button><button :aria-pressed="chartMetric === 'count'" @click="chartMetric = 'count'">订单数</button></div></header><TrendChart :series="series" :metric="chartMetric" :selected-day="selectedDay" @select="selectedDay = $event" /></section>
    <section class="panel todo-panel"><header class="section-heading"><div><h2>待处理事项</h2><p>平台当前待办</p></div><span class="badge neutral">{{ pendingTotal }} 项</span></header><button v-for="t in todos" :key="t.page" class="todo-row" @click="emit('navigate', t.page, t.filter)"><span :class="['task-icon', t.tone]"><component :is="t.icon" :size="20" aria-hidden="true" /></span><span class="todo-copy"><strong>{{ t.name }}</strong><small>{{ t.subtitle }}</small></span><strong class="todo-count">{{ String(t.count).padStart(2, '0') }}</strong><ChevronRight :size="17" aria-hidden="true" /></button></section>
    <section class="panel ranking-panel"><header class="section-heading"><div><h2>商家成交排行</h2><p>按所选周期成交金额排序</p></div><button class="text-button" @click="emit('navigate', 'merchants')">全部商家<ArrowUpRight :size="15" aria-hidden="true" /></button></header><div class="ranking-header"><span>商家</span><span>成交额 / 订单数</span></div><button v-for="(m, i) in ranking" :key="m.id" class="ranking-row" @click="emit('detail', { kind: 'merchant', item: merchants.find(item => item.id === m.id)! })"><span class="rank-number">{{ String(i + 1).padStart(2, '0') }}</span><span class="avatar">{{ m.initials }}</span><span class="rank-name"><strong>{{ m.name }}</strong><small>{{ m.type }} · {{ m.category }}</small></span><span class="rank-amount"><strong>¥{{ money(m.amount) }}</strong><small>{{ m.count }} 单</small></span></button><p v-if="!ranking.length" class="empty-state">当前周期暂无成交记录。</p></section>
    <section class="panel activity-panel"><header class="section-heading"><div><h2>最近操作</h2><p>审核、售后与结算操作记录</p></div><button class="text-button" @click="emit('navigate', 'settings', 'logs')">查看日志<ArrowUpRight :size="15" aria-hidden="true" /></button></header><div class="activity-list"><div v-for="log in auditLogs.slice(0, 3)" :key="log.id" class="activity-item"><i /><div><strong>{{ auditAction(log.action) }}</strong><p>{{ log.target }} · {{ businessNote(log.detail) }}</p><time>{{ log.time }}</time></div></div></div><div class="demo-notice">业务状态与操作记录持续更新，便于追踪处理进度。</div></section>
  </div>
  <section class="panel recent-panel"><header class="section-heading"><div><h2>最近订单</h2><p>平台全部订单，按下单时间倒序</p></div><button class="text-button" @click="emit('navigate', 'orders')">查看全部<ArrowUpRight :size="15" aria-hidden="true" /></button></header><div class="recent-orders"><button v-for="o in orders.slice(0, 3)" :key="o.id" @click="emit('detail', { kind: 'order', item: o })"><span class="task-icon cyan"><ShoppingBag :size="20" aria-hidden="true" /></span><span><strong>{{ productName(o.productId) }}</strong><small>{{ merchantName(o.merchantId) }} · {{ o.time.slice(11) }}</small></span><span class="recent-amount"><strong>¥{{ money(o.amount) }}</strong><small>{{ o.status }}</small></span><ChevronRight :size="16" aria-hidden="true" /></button></div></section>
</template>

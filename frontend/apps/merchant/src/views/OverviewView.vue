<script setup lang="ts">
import { computed, ref } from 'vue';
import { ArrowUpRight, ArrowRight, ShoppingBag, PackageCheck, RotateCcw, AlertCircle, ChevronRight, ShieldCheck, CircleHelp, Users, ReceiptText, MousePointer2 } from '@lucide/vue';
import SalesChart from '../components/SalesChart.vue';
import { money, integer, imageUrl } from '../data/demo';
import type { Period, Page, Product, Order } from '../data/demo';
import { orderMetrics } from '../data/metrics';
const props = defineProps<{ period: Period; products: Product[]; orders: Order[] }>();
const emit = defineEmits<{ navigate: [page: Page, filter?: string]; product: [product: Product]; order: [order: Order] }>();
const data = computed(() => orderMetrics(props.orders, props.period));
const metric = ref<'sales' | 'orders'>('sales');
const top = computed(() => props.products.map(product => ({ ...product, sales: data.value.selected.filter(o => o.productId === product.id).reduce((sum, o) => sum + o.count, 0) })).filter(product => product.sales > 0).sort((a, b) => b.sales - a.sales).slice(0, 4));
const tasks = computed(() => [
  { title: '待发货订单', count: props.orders.filter(o => o.status === '待发货').length, note: '查看订单并填写物流信息', icon: PackageCheck, page: 'orders' as Page, filter: '待发货', color: 'cyan' },
  { title: '售后待处理', count: props.orders.filter(o => o.status === '退款中').length, note: '查看买家的售后申请', icon: RotateCcw, page: 'orders' as Page, filter: '退款中', color: 'gold' },
  { title: '库存预警', count: props.products.filter(p => p.stock > 0 && p.stock < 20).length, note: '库存低于 20 件的商品', icon: AlertCircle, page: 'products' as Page, filter: '库存预警', color: 'rose' },
]);
</script>

<template>
  <section class="stats-grid" aria-label="经营指标">
    <article class="stat"><div class="stat-label">成交金额 <ShoppingBag :size="17" /></div><div class="stat-number"><span class="currency">¥</span>{{ money(data.sales) }}</div><p>已支付，扣除已退款订单</p></article>
    <article class="stat"><div class="stat-label">成交订单 <ReceiptText :size="17" /></div><div class="stat-number">{{ data.orders }}<span class="number-unit">单</span></div><p>所选周期内已支付的订单</p></article>
    <article class="stat"><div class="stat-label">待发货订单 <PackageCheck :size="17" /></div><div class="stat-number">{{ orders.filter(o => o.status === '待发货').length }}<span class="number-unit">单</span></div><p>请及时处理并登记物流信息</p></article>
    <article class="stat"><div class="stat-label">售后待处理 <RotateCcw :size="17" /></div><div class="stat-number">{{ orders.filter(o => o.status === '退款中').length }}<span class="number-unit">单</span></div><p>查看买家的售后申请</p></article>
  </section>

  <div class="dashboard-grid">
    <section class="panel trend-panel">
      <header class="panel-heading"><div><h2>成交趋势<span class="help-tip" tabindex="0" aria-label="按支付日期统计，已扣除已退款订单" title="按支付日期统计，已扣除已退款订单"><CircleHelp :size="15" /></span></h2><p>成交趋势<span class="dot-separator">·</span>{{ data.dates }}</p></div><div class="segmented" aria-label="趋势指标"><button :aria-pressed="metric === 'sales'" :class="{ active: metric === 'sales' }" @click="metric = 'sales'">成交额</button><button :aria-pressed="metric === 'orders'" :class="{ active: metric === 'orders' }" @click="metric = 'orders'">订单数</button></div></header>
      <div class="trend-summary"><strong>{{ metric === 'sales' ? `¥ ${money(data.sales)}` : `${integer(data.orders)} 单` }}</strong><div class="chart-legend"><span><i class="legend-current" />本周期</span><span><i class="legend-previous" />上一周期</span></div></div>
      <SalesChart :period="period" :metric="metric" :orders="orders" />
    </section>

    <section class="panel todo-panel">
      <header class="panel-heading"><div><h2>待处理事项</h2><p>订单、售后与库存预警</p></div><span class="tiny-counter">{{ tasks.reduce((n, t) => n + t.count, 0) }} 项</span></header>
      <button v-for="task in tasks" :key="task.title" class="todo-item" @click="emit('navigate', task.page, task.filter)"><span :class="['task-icon', task.color]"><component :is="task.icon" :size="20" /></span><span class="todo-copy"><strong>{{ task.title }}</strong><small>{{ task.note }}</small></span><span class="todo-count">{{ task.count.toString().padStart(2, '0') }}</span><ChevronRight :size="16" /></button>
      <div class="todo-note"><span class="status-dot" />店铺营业中<span>订单状态持续更新</span></div>
    </section>

    <section class="panel bestsellers-panel">
      <header class="panel-heading"><div><h2>商品销量排行</h2><p>商品销量排行<span class="dot-separator">·</span>按当前周期统计</p></div><button class="text-button" @click="emit('navigate', 'products')">全部商品 <ArrowUpRight :size="16" /></button></header>
      <div class="ranking-table"><div class="ranking-head"><span>商品</span><span>成交单价</span><span>销量</span></div><button v-for="(product, i) in top" :key="product.id" class="ranking-row" @click="emit('product', product)"><span class="rank">{{ String(i + 1).padStart(2, '0') }}</span><span class="product-thumbnail"><img :src="imageUrl(product.image)" :alt="product.name" /></span><span class="product-description"><strong>{{ product.name }}</strong><small>{{ product.subtitle }}</small></span><span class="ranking-price">¥{{ integer(product.price) }}</span><strong class="ranking-sales">{{ product.sales }}<small>件</small></strong></button><p v-if="!top.length" class="modal-note">当前周期暂无商品成交记录。</p></div>
    </section>

    <section class="panel health-panel">
      <header class="panel-heading"><div><h2>店铺体验评分</h2><p>店铺体验分<span class="dot-separator">·</span>暂无评分记录</p></div><ShieldCheck :size="21" class="muted-icon" /></header>
      <div class="health-score"><strong>—<span>/ 5.0</span></strong><span class="quality-tag">暂无评分</span></div>
      <p class="modal-note">店铺评分根据买家评价与服务记录计算，积累评价后展示。</p>
      <button class="text-button health-link" @click="emit('navigate', 'settings')">查看店铺信息 <ArrowRight :size="16" /></button>
    </section>
  </div>

  <section class="panel recent-panel"><header class="panel-heading"><div><h2>最近订单</h2><p>按下单时间倒序展示</p></div><button class="text-button" @click="emit('navigate', 'orders')">查看全部 <ArrowUpRight :size="16" /></button></header><div class="recent-orders"><button v-for="order in orders.slice(0, 3)" :key="order.id" @click="emit('order', order)"><span class="recent-avatar">{{ order.customer.charAt(0) }}</span><span><strong>{{ order.customer }} 的订单</strong><small>{{ order.time.slice(11) }}<span class="dot-separator">·</span>{{ products.find(p => p.id === order.productId)?.name }}</small></span><span class="recent-amount">¥{{ money(order.amount) }}</span><span :class="['badge', order.status === '待发货' ? 'warning' : 'neutral']">{{ order.status }}</span><ChevronRight :size="16" /></button></div></section>
</template>

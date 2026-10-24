<script setup lang="ts">
import { computed, ref } from 'vue';
import { Search, SlidersHorizontal, ArrowUpRight, PackageOpen } from '@lucide/vue';
import { imageUrl, money, downloadCsv } from '../data/demo';
import type { Order, Product } from '../data/demo';
const props = defineProps<{ orders: Order[]; products: Product[]; initialFilter: string }>();
const emit = defineEmits<{ detail: [order: Order]; notify: [message: string] }>();
const filter = ref(props.initialFilter || '全部订单');
const query = ref('');
const tabs = ['全部订单', '待付款', '已取消', '已退款', '待发货', '运输中', '已完成', '退款中'];
const filtered = computed(() => props.orders.filter(order => (filter.value === '全部订单' || order.status === filter.value) && `${order.id} ${order.customer} ${product(order)?.name}`.toLowerCase().includes(query.value.toLowerCase())));
const product = (order: Order) => props.products.find(p => p.id === order.productId);
function exportOrders() {
  downloadCsv('Lectus-订单明细.csv', [['订单号', '商品', '买家', '金额', '状态'], ...filtered.value.map(o => [o.id, product(o)?.name || '', o.customer, o.amount, o.status])]);
  emit('notify', '已导出当前筛选下的订单');
}
</script>

<template>
  <section class="management-summary"><div><span class="eyebrow">ORDER MANAGEMENT</span><h2>订单处理</h2><p>查询订单状态，查看详情并处理发货。</p></div><div class="summary-counter"><strong>{{ orders.filter(o => o.status === '待发货').length.toString().padStart(2, '0') }}</strong><span>笔订单待发货</span></div></section>
  <section class="panel management-panel"><header class="management-toolbar"><div class="tabs" aria-label="订单状态"><button v-for="tab in tabs" :key="tab" :class="{ active: filter === tab }" :aria-pressed="filter === tab" @click="filter = tab">{{ tab }}<span v-if="tab === '待发货'">{{ orders.filter(o => o.status === tab).length }}</span></button></div><button class="text-button" @click="exportOrders">导出订单 <ArrowUpRight :size="16" /></button></header><div class="filter-toolbar"><label class="search-field"><Search :size="18" /><input v-model="query" aria-label="搜索订单" placeholder="搜索订单号、买家或商品名称" /></label><span class="filter-caption"><SlidersHorizontal :size="15" />已筛选 {{ filtered.length }} 笔订单</span></div>
    <div class="table-scroll"><table class="data-table orders-table"><thead><tr><th>商品 / 订单号</th><th>买家</th><th>实付金额</th><th>订单状态</th><th>下单时间</th><th class="align-right">操作</th></tr></thead><tbody><tr v-for="order in filtered" :key="order.id"><td><div class="table-product"><img v-if="product(order)" :src="imageUrl(product(order)!.image)" alt="" /><span><strong>{{ product(order)?.name }}</strong><small>{{ order.id }}<br />{{ order.variant }} · {{ order.count }} 件</small></span></div></td><td>{{ order.customer }}</td><td class="amount-cell">¥{{ money(order.amount) }}</td><td><span :class="['badge', { warning: order.status === '待发货', danger: order.status === '退款中', success: order.status === '已完成', neutral: order.status === '运输中' }]">{{ order.status }}</span></td><td class="date-cell">{{ order.time.slice(0, 10) }}<br />{{ order.time.slice(11) }}</td><td class="align-right"><button class="table-action" @click="emit('detail', order)">{{ order.status === '待发货' ? '去发货' : '查看详情' }}<ArrowUpRight :size="14" /></button></td></tr></tbody></table></div><div v-if="!filtered.length" class="empty-state"><PackageOpen :size="36" /><h3>没有找到符合条件的订单</h3><p>请调整关键词或订单状态。</p><button class="secondary-button" @click="query = ''; filter = '全部订单'">重置筛选</button></div><footer class="table-footer"><span>共 {{ filtered.length }} 笔订单 · 数据来自统一订单库</span><span>第 1 / 1 页</span></footer>
  </section>
</template>

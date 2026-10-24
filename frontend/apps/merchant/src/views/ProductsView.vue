<script setup lang="ts">
import { computed, ref } from 'vue';
import { Search, Plus, ArrowUpRight, PackageOpen, AlertCircle } from '@lucide/vue';
import { imageUrl, integer } from '../data/demo';
import type { Product } from '../data/demo';
const props = defineProps<{ products: Product[]; initialFilter: string }>();
const emit = defineEmits<{ detail: [product: Product]; create: []; toggle: [product: Product] }>();
const filter = ref(props.initialFilter || '全部商品');
const query = ref('');
const category = ref('全部分类');
const filtered = computed(() => props.products.filter(p => (filter.value === '全部商品' || (filter.value === '库存预警' ? p.stock > 0 && p.stock < 20 : p.status === filter.value)) && (category.value === '全部分类' || p.category === category.value) && `${p.name} ${p.id} ${p.subtitle}`.toLowerCase().includes(query.value.toLowerCase())));
</script>

<template>
  <section class="management-summary"><div><span class="eyebrow">PRODUCT MANAGEMENT</span><h2>商品列表</h2><p>管理商品信息、售价、库存及上下架状态。</p></div><button class="primary-button" @click="emit('create')"><Plus :size="18" />新增商品</button></section>
  <section class="panel management-panel"><header class="management-toolbar"><div class="tabs" aria-label="商品状态"><button v-for="tab in ['全部商品', '销售中', '已下架', '草稿', '待审核', '已驳回', '库存预警']" :key="tab" :class="{ active: filter === tab }" :aria-pressed="filter === tab" @click="filter = tab">{{ tab }}<AlertCircle v-if="tab === '库存预警'" :size="14" /></button></div><span class="muted">{{ products.length }} 件商品</span></header><div class="filter-toolbar"><label class="search-field"><Search :size="18" /><input v-model="query" aria-label="搜索商品" placeholder="搜索商品名称或商品编号" /></label><select v-model="category" class="select-control" aria-label="商品分类"><option>全部分类</option><option>鞋履</option><option>服饰</option><option>生活方式</option></select></div>
    <div class="table-scroll"><table class="data-table"><thead><tr><th>商品信息</th><th>售价</th><th>库存</th><th>累计销量</th><th>状态</th><th class="align-right">操作</th></tr></thead><tbody><tr v-for="product in filtered" :key="product.id"><td><div class="table-product"><img :src="imageUrl(product.image)" alt="" /><span><strong>{{ product.name }}</strong><small>{{ product.subtitle }}<br />{{ product.id }}</small></span></div></td><td class="amount-cell">¥{{ integer(product.price) }}</td><td><span :class="{ 'low-stock': product.stock < 20 }">{{ product.stock }}</span><small v-if="product.stock > 0 && product.stock < 20" class="stock-warning">需补货</small></td><td>{{ product.sales }}</td><td><span :class="['badge', product.status === '销售中' ? 'success' : 'neutral']">{{ product.status }}</span></td><td><div class="row-actions"><button class="table-action" @click="emit('detail', product)">编辑 <ArrowUpRight :size="14" /></button><button class="subtle-action" @click="emit('toggle', product)">{{ product.status === '销售中' ? '下架' : product.status === '待审核' ? '撤回审核' : '提交审核' }}</button></div></td></tr></tbody></table></div><div v-if="!filtered.length" class="empty-state"><PackageOpen :size="36" /><h3>这里还没有符合条件的商品</h3><p>请调整筛选条件或新增商品。</p><button class="secondary-button" @click="query = ''; filter = '全部商品'; category = '全部分类'">重置筛选</button></div><footer class="table-footer"><span>共 {{ filtered.length }} 件商品 · 商品状态自动更新</span><span>第 1 / 1 页</span></footer>
  </section>
</template>

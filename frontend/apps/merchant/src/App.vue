<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { LayoutDashboard, ShoppingBag, Package, TicketPercent, ChartNoAxesCombined, Settings2, Search, Bell, ChevronDown, ChevronsUpDown, ArrowUpRight, Plus, ArrowRight, X, Store, CircleHelp, Download, Menu, Check, Truck, FileText, ChevronRight, ExternalLink, CheckCheck } from '@lucide/vue';
import OverviewView from './views/OverviewView.vue';
import OrdersView from './views/OrdersView.vue';
import ProductsView from './views/ProductsView.vue';
import Modal from './components/Modal.vue';
import OrderJourney from './components/OrderJourney.vue';
import { periodData, money, imageUrl, downloadCsv } from './data/demo';
import type { Page, Period, Product, Order } from './data/demo';
import { orderMetrics } from './data/metrics';
import './styles.css';
import WorkspaceLogin from './components/WorkspaceLogin.vue';
import { workspaceRequest, workspaceSession, workspaceLogout } from './workspace';

const navigation = [
  { id: 'overview' as Page, name: '经营概览', icon: LayoutDashboard, en: 'Overview' },
  { id: 'orders' as Page, name: '订单管理', icon: ShoppingBag, en: 'Orders' },
  { id: 'products' as Page, name: '商品管理', icon: Package, en: 'Products' },
  { id: 'marketing' as Page, name: '营销活动', icon: TicketPercent, en: 'Marketing' },
  { id: 'finance' as Page, name: '财务对账', icon: ChartNoAxesCombined, en: 'Finance' },
  { id: 'settings' as Page, name: '店铺设置', icon: Settings2, en: 'Settings' },
];
const page = ref<Page>('overview');
const navFilter = ref('');
const viewKey = ref(0);
const period = ref<Period>('month');
const periodOpen = ref(false);
const mobileOpen = ref(false);
const products = reactive<Product[]>([]);
const orders = reactive<Order[]>([]);
const modal = ref<'search' | 'notifications' | 'product' | 'order' | 'help' | 'store' | null>(null);
const selectedProduct = ref<Product | null>(null);
const selectedOrder = ref<Order | null>(null);
const search = ref('');
const noticeRead = ref(false);
const toast = ref('');
let toastTimer: ReturnType<typeof setTimeout>;
const productDraft = reactive({ name: '', subtitle: '', category: '鞋履', price: 0, stock: 0 });
const shipmentConfirm = ref(false);
const deliveryConfirm = ref(false);
const localSimulation = ref(false);
const shipment = reactive({ carrier: '顺丰速运', tracking: '' });
const formError = ref('');
const current = computed(() => navigation.find(item => item.id === page.value)!);
const pending = computed(() => orders.filter(o => o.status === '待发货').length);
const searchProducts = computed(() => search.value.trim() ? products.filter(p => `${p.name} ${p.id}`.toLowerCase().includes(search.value.toLowerCase())).slice(0, 4) : products.slice(0, 3));
const searchOrders = computed(() => search.value.trim() ? orders.filter(o => `${o.id} ${o.customer}`.includes(search.value)).slice(0, 3) : []);
const modalTitle = computed(() => ({ search: '搜索商品或订单', notifications: '店铺消息', product: selectedProduct.value ? '编辑商品' : '新增商品', order: '订单详情', help: '工作台使用说明', store: '当前店铺' })[modal.value || 'help']);

function notify(message: string) { toast.value = message; clearTimeout(toastTimer); toastTimer = setTimeout(() => toast.value = '', 3800); }
function navigate(target: Page, filter = '') {
  page.value = target; navFilter.value = filter; viewKey.value++; mobileOpen.value = false; periodOpen.value = false;
  if (location.hash !== `#/${target}`) history.pushState(null, '', `#/${target}`);
  window.scrollTo({ top: 0, behavior: 'instant' });
}
function syncRoute() {
  const target = location.hash.replace('#/', '') as Page;
  page.value = navigation.some(n => n.id === target) ? target : 'overview';
  navFilter.value = ''; viewKey.value++;
}
function closeModal() { modal.value = null; formError.value = ''; }
function openProduct(product?: Product) {
  selectedProduct.value = product || null;
  Object.assign(productDraft, product ? { name: product.name, subtitle: product.subtitle, category: product.category, price: product.price, stock: product.stock } : { name: '', subtitle: '', category: '鞋履', price: 0, stock: 0 });
  formError.value = ''; modal.value = 'product';
}
const busy = ref(false);
const loadError = ref('');
const shopName = ref('商家工作台');
const shopStatus = ref('');
let polling: ReturnType<typeof setInterval>;
let fetching = false;
async function loadSnapshot() {
 if (!workspaceSession.value || fetching) return; fetching = true;
 try {
  const data = await workspaceRequest('snapshot');
  function sync<T extends {id: string}>(list: T[], incoming: T[]) { const merged = incoming.map(item => { const existing = list.find(old => old.id === item.id); return existing ? Object.assign(existing, item) : item; }); list.splice(0, list.length, ...merged); }
  localSimulation.value = data.localSimulation; sync(products, data.products); sync(orders, data.orders); shopName.value = data.merchants[0]?.name || '商家工作台'; shopStatus.value = data.merchants[0]?.status || ''; loadError.value = '';
 } catch(e) { loadError.value = (e as Error).message; } finally { fetching = false; }
}
async function saveProduct() {
 if (busy.value) return;
 if (!productDraft.name.trim() || !productDraft.subtitle.trim() || !Number.isFinite(productDraft.price) || productDraft.price <= 0 || !Number.isInteger(productDraft.stock) || productDraft.stock < 0) { formError.value = '请填写有效的名称、描述、售价和库存。'; return; }
 busy.value = true;
 try { await workspaceRequest('products' + (selectedProduct.value ? '/' + selectedProduct.value.id : ''), { ...productDraft }, selectedProduct.value ? 'PUT' : 'POST'); await loadSnapshot(); closeModal(); notify('草稿已保存，请提交平台审核'); }
 catch(e) { formError.value = (e as Error).message; } finally { busy.value = false; }
}
async function toggleProduct(product: Product) {
 if (busy.value) return; busy.value = true;
 try { await workspaceRequest('products/' + product.id + '/action', { action: ['销售中', '待审核'].includes(product.status) ? 'down' : 'submit' }); await loadSnapshot(); notify('商品状态已保存'); } catch(e) { notify((e as Error).message); } finally { busy.value = false; }
}
function openOrder(order: Order) { selectedOrder.value = order; shipment.tracking = ''; shipmentConfirm.value = false; deliveryConfirm.value = false; formError.value = ''; modal.value = 'order'; }
async function shipOrder() {
 if (!selectedOrder.value || busy.value) return;
 if (!shipmentConfirm.value) { shipmentConfirm.value = true; return; }
 busy.value = true;
 try { await workspaceRequest('orders/' + selectedOrder.value.id + '/ship', { carrier: shipment.carrier, tracking: shipment.tracking.trim() }); await loadSnapshot(); shipmentConfirm.value = false; notify('发货成功，运单已同步到买家订单'); }
 catch(e) { formError.value = (e as Error).message; } finally { busy.value = false; }
}
async function advanceDelivery() {
 if (!selectedOrder.value || busy.value) return;
 if (!deliveryConfirm.value) { deliveryConfirm.value = true; return; }
 busy.value = true; formError.value = '';
 try { await workspaceRequest('orders/' + selectedOrder.value.id + '/delivery', { expectedStage: selectedOrder.value.deliveryStage || 0 }); await loadSnapshot(); deliveryConfirm.value = false; notify('配送进度已同步'); }
 catch(e) { formError.value = (e as Error).message; } finally { busy.value = false; }
}
function exportReport() { const d = orderMetrics(orders, period.value); downloadCsv('Lectus-经营概览.csv', [['统计周期', d.dates], ['日期', '成交金额', '成交订单'], ...d.labels.map((label, i) => [label, d.values[i]!, d.orderCounts[i]!])]); notify('所选周期的经营报表已导出'); }
function handleKey(e: KeyboardEvent) { if((e.metaKey || e.ctrlKey) && e.key === 'k') { e.preventDefault(); modal.value = 'search'; } if(e.key === 'Escape') { periodOpen.value = false; mobileOpen.value = false; } }
onMounted(() => { syncRoute(); loadSnapshot(); polling = setInterval(loadSnapshot, 5000); window.addEventListener('popstate', syncRoute); window.addEventListener('hashchange', syncRoute); window.addEventListener('keydown', handleKey); });
onBeforeUnmount(() => { clearInterval(polling); clearTimeout(toastTimer); window.removeEventListener('popstate', syncRoute); window.removeEventListener('hashchange', syncRoute); window.removeEventListener('keydown', handleKey); });
</script>

<template>
  <WorkspaceLogin v-if="!workspaceSession" @login="loadSnapshot" />
  <template v-else>
  <a class="skip-link" href="#main-content">跳转到主要内容</a>
  <div class="workspace">
    <button v-if="mobileOpen" class="sidebar-scrim" aria-label="关闭导航" @click="mobileOpen = false" />
    <aside :class="['sidebar', { 'mobile-open': mobileOpen }]">
      <a class="brand" href="#/overview" @click.prevent="navigate('overview')"><span class="brand-wordmark">Lectus<span>·</span></span><span class="brand-subtitle">莱择 · 商家工作台</span></a>
      <button class="store-switch" @click="modal = 'store'"><span class="store-icon"><Store :size="20" /></span><span><strong>{{ shopName }}</strong><small><i class="status-dot" />{{ shopStatus }}</small></span><ChevronsUpDown :size="15" /></button>
      <span class="nav-caption">店铺经营<span>WORKSPACE</span></span>
      <nav aria-label="商家主导航"><a v-for="item in navigation" :key="item.id" :href="`#/${item.id}`" :class="['nav-item', { active: page === item.id }]" :aria-current="page === item.id ? 'page' : undefined" @click.prevent="navigate(item.id)"><component :is="item.icon" :size="20" /><span>{{ item.name }}</span><span v-if="item.id === 'orders' && pending" class="nav-count">{{ pending.toString().padStart(2, '0') }}</span><span v-else-if="page === item.id" class="nav-active-dot" /></a></nav>
      <div class="sidebar-bottom"><div class="help-card"><span class="help-symbol"><CircleHelp :size="20" /></span><strong>使用帮助</strong><p>查看各模块功能与操作说明。</p><button class="text-button" @click="modal = 'help'">查看说明 <ArrowUpRight :size="16" /></button></div><button class="account-button" @click="navigate('settings')"><span class="avatar">择</span><span><strong>店铺管理员</strong><small>官方旗舰店 · 主账号</small></span><Settings2 :size="17" /></button><button class="text-button" @click="workspaceLogout">退出登录</button></div>
    </aside>

    <div class="main-shell">
      <header class="topbar"><div class="breadcrumbs"><button class="mobile-menu icon-button" aria-label="打开导航" @click="mobileOpen = true"><Menu :size="21" /></button><span>商家工作台</span><ChevronRight :size="13" /><strong>{{ current.name }}</strong></div><div class="topbar-actions"><span class="preview-indicator"><i />订单动态<span>· 自动更新</span></span><button class="search-trigger" aria-label="搜索商品或订单" @click="modal = 'search'"><Search :size="17" /><span>搜索商品、订单</span><kbd>⌘ K</kbd></button><button class="icon-button notification-trigger" aria-label="查看店铺消息" @click="modal = 'notifications'"><Bell :size="19" /><i v-if="!noticeRead" /></button><button class="top-avatar" aria-label="管理员设置" @click="navigate('settings')">L</button></div></header>
      <main id="main-content" tabindex="-1" class="main-content">
        <header class="page-header"><div><div class="page-eyebrow">{{ page === 'overview' ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'full' }).format(new Date()) : `LECTUS MERCHANT / ${current.en.toUpperCase()}` }}</div><h1>{{ page === 'overview' ? '经营概览' : current.name }}<span v-if="page === 'overview'" class="greeting-dot" /></h1><p>{{ page === 'overview' ? '查看店铺成交、订单和待处理事项。' : shopName + " · 商家工作台" }}</p></div><div v-if="page === 'overview'" class="page-actions"><div class="period-picker"><button class="secondary-button" :aria-expanded="periodOpen" aria-controls="period-menu" @click="periodOpen = !periodOpen"><span class="calendar-symbol">{{ new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', day: 'numeric' }).format(new Date()).replace('日', '') }}</span>{{ periodData[period].label }}<ChevronDown :size="15" /></button><div v-if="periodOpen" id="period-menu" class="dropdown"><button v-for="key in (['week', 'month', 'year'] as Period[])" :key="key" @click="period = key; periodOpen = false">{{ periodData[key].label }}<Check v-if="period === key" :size="16" /></button></div></div><button class="secondary-button export-button" @click="exportReport"><Download :size="17" />导出报表</button><button class="primary-button" @click="openProduct()"><Plus :size="18" />发布商品</button></div></header>

        <div v-if="loadError" class="demo-notice" role="alert">{{ loadError }} <button class="text-button" @click="loadSnapshot">重试</button></div>
        <OverviewView v-if="page === 'overview'" :period="period" :products="products" :orders="orders" @navigate="navigate" @product="openProduct" @order="openOrder" />
        <OrdersView v-else-if="page === 'orders'" :key="viewKey" :orders="orders" :products="products" :initial-filter="navFilter" @detail="openOrder" @notify="notify" />
        <ProductsView v-else-if="page === 'products'" :key="viewKey" :products="products" :initial-filter="navFilter" @detail="openProduct" @create="openProduct()" @toggle="toggleProduct" />
        <section v-if="['marketing', 'finance', 'settings'].includes(page)" class="panel" style="padding:32px"><h2>{{ current.name }}</h2><p class="modal-note">该服务暂不可用。您可以继续管理商品、订单及售后。</p><p>{{ shopName }} · {{ workspaceSession.merchantId }} · {{ shopStatus }}</p></section>
        <footer class="workspace-footer"><span><span class="footer-wordmark">Lectus<span>·</span></span>商家管理系统</span><span>商家工作台<span class="dot-separator">·</span>店铺运营中心</span></footer>
      </main>
    </div>
  </div>

  <Modal :open="modal !== null" :title="modalTitle" :wide="modal === 'order'" @close="closeModal">
    <template v-if="modal === 'search'"><label class="search-field modal-search"><Search :size="20" /><input v-model="search" autofocus aria-label="全局搜索" placeholder="输入商品名、商品编号或订单号" /></label><div class="search-results"><span class="section-caption">{{ search ? '匹配的商品' : '店铺商品' }}</span><button v-for="p in searchProducts" :key="p.id" @click="openProduct(p)"><img :src="imageUrl(p.image)" alt="" /><span><strong>{{ p.name }}</strong><small>{{ p.id }} · ¥{{ money(p.price) }}</small></span><ArrowUpRight :size="17" /></button><template v-if="searchOrders.length"><span class="section-caption">匹配的订单</span><button v-for="o in searchOrders" :key="o.id" @click="openOrder(o)"><ShoppingBag :size="22" /><span><strong>{{ o.id }}</strong><small>{{ o.customer }} · {{ o.status }}</small></span><ArrowUpRight :size="17" /></button></template><div v-if="!searchProducts.length && !searchOrders.length" class="empty-state"><Search :size="28" /><h3>没有找到匹配内容</h3><p>请调整关键词后重新搜索。</p></div></div></template>
    <template v-else-if="modal === 'notifications'"><div class="notification-intro"><span>店铺待办</span><button class="text-button" @click="noticeRead = true"><CheckCheck :size="16" />全部已读</button></div><button class="notification-item" @click="closeModal(); navigate('orders', '待发货')"><span class="task-icon cyan"><ShoppingBag :size="20" /></span><span><strong>订单待发货</strong><small>{{ pending }} 笔订单等待发货，请核对收货及物流信息。</small></span><ChevronRight :size="17" /></button><button class="notification-item" @click="closeModal(); navigate('products', '库存预警')"><span class="task-icon gold"><Package :size="20" /></span><span><strong>商品库存预警</strong><small>{{ products.filter(p => p.stock > 0 && p.stock < 20).length }} 件商品库存低于 20 件，请及时补货。</small></span><ChevronRight :size="17" /></button></template>
    <template v-else-if="modal === 'product'"><p class="modal-note">商品先保存为草稿，提交后由平台审核。销售中或待审核商品须先下架或撤回再编辑。</p><form class="product-form" @submit.prevent="saveProduct"><label class="field-label">商品名称<input v-model="productDraft.name" required maxlength="60" placeholder="例如：莱择 日常条纹衬衫" /></label><label class="field-label">规格描述<input v-model="productDraft.subtitle" required maxlength="80" placeholder="例如：宽松棉质衬衫 · 雾蓝" /></label><div class="form-two-columns"><label class="field-label">商品分类<select v-model="productDraft.category"><option>鞋履</option><option>服饰</option><option>生活方式</option></select></label><label class="field-label">售价（元）<input v-model.number="productDraft.price" required type="number" min="0.01" max="99999999" step="0.01" /></label></div><label class="field-label">库存（件）<input v-model.number="productDraft.stock" required type="number" min="0" max="9999999" step="1" /></label><p class="form-hint">{{ selectedProduct ? '保留原商品图片，提交前请核对商品资料。' : '草稿创建后请在商品管理中核对图片、规格和库存，再提交审核。' }}</p><p v-if="formError" class="form-error" role="alert">{{ formError }}</p><footer class="form-footer"><button type="button" class="secondary-button" @click="closeModal">取消</button><button type="submit" :disabled="busy" class="primary-button"><Check :size="17" />{{ selectedProduct ? '保存修改' : '保存为草稿' }}</button></footer></form></template>
    <template v-else-if="modal === 'order' && selectedOrder">
     <div class="order-detail-heading"><div><span class="section-caption">{{ selectedOrder.id }}</span><h3>商品与配送信息</h3></div><span :class="['badge', selectedOrder.status === '待发货' ? 'warning' : 'neutral']">{{ selectedOrder.status }}</span></div>
     <div class="detail-product"><img :src="imageUrl(selectedOrder.image || 'product-shirt-blue-480.webp')" alt="商品图片" /><div><strong>{{ selectedOrder.productName }}</strong><p>{{ selectedOrder.variant }} · {{ selectedOrder.count }} 件</p></div><strong>¥{{ money(selectedOrder.amount) }}</strong></div>
     <dl class="order-info"><div><dt>买家</dt><dd>{{ selectedOrder.customer }}</dd></div><div><dt>下单时间</dt><dd>{{ selectedOrder.time }}</dd></div><div><dt>{{ selectedOrder.paidAt ? '实付金额' : '应付金额' }}</dt><dd>¥{{ money(selectedOrder.amount) }}</dd></div><div><dt>收货人</dt><dd>{{ selectedOrder.recipient }} · {{ selectedOrder.phone }}</dd></div><div><dt>收货地址</dt><dd>{{ selectedOrder.address }}</dd></div></dl>
     <form v-if="selectedOrder.status === '待发货'" class="shipment-form" @submit.prevent="shipOrder"><h3><Truck :size="18" />{{ shipmentConfirm ? '确认发货信息' : '填写发货信息' }}</h3><p class="modal-note">{{ shipmentConfirm ? '请核对承运商和运单号，确认后买家将收到发货通知。' : '填写已创建的快递运单，发货后买家可以查看配送进度。' }}</p><div class="form-two-columns"><label class="field-label">物流公司<select v-model="shipment.carrier" :disabled="shipmentConfirm || busy"><option>顺丰速运</option><option>中通快递</option><option>圆通速递</option></select></label><label class="field-label">运单号<input v-model="shipment.tracking" :readonly="shipmentConfirm" :disabled="busy" required pattern="[a-zA-Z0-9-]{6,60}" maxlength="60" placeholder="填写快递运单号" /></label></div><p class="modal-note">请先向承运商寄件，再填写对应运单号。登记发货不代为下单寄件。</p><p v-if="formError" class="form-error" role="alert">{{ formError }}</p><footer class="form-footer"><button type="button" class="secondary-button" :disabled="busy" @click="shipmentConfirm ? shipmentConfirm = false : closeModal()">{{ shipmentConfirm ? '返回修改' : '稍后处理' }}</button><button type="submit" :disabled="busy" class="primary-button">{{ busy ? '正在提交…' : shipmentConfirm ? '确认发货' : '核对并发货' }} <ArrowRight :size="16" /></button></footer></form>
     <div v-else-if="selectedOrder.tracking" class="order-followup"><Truck :size="20" /><span>{{ selectedOrder.carrier }} · {{ selectedOrder.tracking }}</span></div>
     <section v-if="localSimulation && selectedOrder.status === '运输中' && (selectedOrder.deliveryStage || 0) < 3" class="shipment-form"><h3>更新配送进度</h3><p class="modal-note">{{ deliveryConfirm ? '确认将包裹更新为“' + ['已揽收', '运输中', '派送中'][selectedOrder.deliveryStage || 0] + '”？更新后买家可立即查看该节点。' : '请依据承运商提供的信息更新配送节点。' }}</p><p v-if="formError" class="form-error" role="alert">{{ formError }}</p><footer class="form-footer"><button v-if="deliveryConfirm" class="secondary-button" :disabled="busy" @click="deliveryConfirm = false">取消</button><button class="secondary-button" :disabled="busy" @click="advanceDelivery">{{ busy ? '更新中…' : deliveryConfirm ? '确认更新' : '更新为' + ['已揽收', '运输中', '派送中'][selectedOrder.deliveryStage || 0] }}</button></footer></section>
     <OrderJourney :order="selectedOrder" />
    </template>
    <template v-else-if="modal === 'help'"><div class="welcome-guide"><span class="guide-logo">Lectus<span>·</span></span><p>商家工作台功能说明</p><div><ShoppingBag :size="22" /><span><strong>订单管理</strong><small>筛选订单，查看详情，登记发货信息。</small></span></div><div><Package :size="22" /><span><strong>商品管理</strong><small>搜索、编辑、上下架商品，创建商品草稿。</small></span></div><div><FileText :size="22" /><span><strong>经营报表</strong><small>切换经营周期，查看趋势并导出经营报表。</small></span></div><p class="modal-note">商品、订单和配送进度会同步到买家与平台。请核实收货信息与运单信息后处理发货。</p><button class="primary-button full-width" @click="closeModal">关闭说明 <ArrowRight :size="17" /></button></div></template>
    <template v-else-if="modal === 'store'"><div class="store-option"><span class="store-icon"><Store :size="24" /></span><span><strong>{{ shopName }}</strong><small>{{ workspaceSession.merchantId }} · 当前账号店铺</small></span><Check :size="19" /></div><p class="modal-note">当前店铺由后台账号绑定。切换店铺请退出后使用对应的商家账号登录。</p><button class="secondary-button full-width" @click="closeModal(); navigate('settings')">管理店铺信息 <ExternalLink :size="17" /></button></template>
  </Modal>
  <Transition name="toast"><div v-if="toast" class="toast-message" role="status"><span><Check :size="15" /></span>{{ toast }}<button aria-label="关闭提示" @click="toast = ''"><X :size="16" /></button></div></Transition>
</template>
</template>

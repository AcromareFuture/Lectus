<script setup lang="ts">
import { transactionReference, businessNote } from '../../../packages/business-records';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { LayoutDashboard, Store, PackageCheck, ShoppingBag, RotateCcw, Wallet, Settings2, ChevronRight, Search, Bell, Menu, ArrowUpRight, ShieldCheck, Check, X, CircleHelp, FileText } from '@lucide/vue';
import OverviewView from './views/OverviewView.vue';
import RegistryView from './views/RegistryView.vue';
import TransactionsView from './views/TransactionsView.vue';
import SettingsView from './views/SettingsView.vue';
import Modal from './components/Modal.vue';
import OrderJourney from './components/OrderJourney.vue';
import { merchants, products, orders, pendingCounts, toast, notify, merchantName, productName, money, imageUrl, settlementTotals, aftersales, settlements, auditLogs } from './data/platform';
import type { Page, Detail, Merchant, Product, AfterSale, Settlement } from './data/platform';
import './styles.css';
import WorkspaceLogin from './components/WorkspaceLogin.vue';
import { workspaceRequest, workspaceSession, workspaceLogout } from './workspace';

const navigation = [
  { id: 'overview' as Page, name: '运营概览', icon: LayoutDashboard, description: '查看平台成交、商家经营和待处理事项。' },
  { id: 'merchants' as Page, name: '商家管理', icon: Store, description: '审核入驻申请，查看商家资料和经营状态。' },
  { id: 'products' as Page, name: '商品审核', icon: PackageCheck, description: '查看商家提交的商品，处理审核与下架。' },
  { id: 'orders' as Page, name: '订单查询', icon: ShoppingBag, description: '查询平台订单、支付状态和配送信息。' },
  { id: 'aftersales' as Page, name: '售后处理', icon: RotateCcw, description: '查看退款申请，处理需要平台介入的售后。' },
  { id: 'finance' as Page, name: '结算对账', icon: Wallet, description: '核对商家结算账单、服务费和应结金额。' },
  { id: 'settings' as Page, name: '平台设置', icon: Settings2, description: '维护平台信息，查看操作记录。' },
];
const page = ref<Page>('overview');
const mobileOpen = ref(false);
const routeFilter = ref('');
const viewKey = ref(0);
const modal = ref<'detail' | 'action' | 'search' | 'notifications' | 'help' | null>(null);
const detail = ref<Detail | null>(null);
const search = ref('');
const reason = ref('');
const formError = ref('');
const readNotices = ref(false);
const action = ref<{ title: string; text: string; reasonRequired: boolean; danger: boolean; run: (reason: string) => Promise<unknown> } | null>(null);
const current = computed(() => navigation.find(n => n.id === page.value)!);
const pendingTotal = computed(() => pendingCounts.value.merchants + pendingCounts.value.products + pendingCounts.value.aftersales + pendingCounts.value.finance);
const modalTitle = computed(() => modal.value === 'action' ? action.value?.title || '' : modal.value === 'search' ? '搜索商家、商品或订单' : modal.value === 'notifications' ? '待办通知' : modal.value === 'help' ? '使用说明' : detail.value ? ({ merchant: '商家详情', product: '商品详情', order: '订单详情', aftersale: '售后详情', settlement: '结算账单' })[detail.value.kind] : '详情');
const matchedMerchants = computed(() => merchants.filter(m => `${m.name} ${m.id}`.toLowerCase().includes(search.value.trim().toLowerCase())).slice(0, 4));
const matchedProducts = computed(() => search.value.trim() ? products.filter(p => `${p.name} ${p.id}`.toLowerCase().includes(search.value.trim().toLowerCase())).slice(0, 4) : []);
const matchedOrders = computed(() => search.value.trim() ? orders.filter(o => `${o.id} ${o.customer}`.includes(search.value.trim())).slice(0, 3) : []);
const relatedOrder = computed(() => {
  const currentDetail = detail.value;
  return currentDetail?.kind === 'aftersale' ? orders.find(o => o.id === currentDetail.item.orderId) : undefined;
});

function navigate(target: Page, filter = '') {
  page.value = target; routeFilter.value = filter; viewKey.value++; mobileOpen.value = false;
  if (location.hash !== `#/${target}`) history.pushState(null, '', `#/${target}`);
  window.scrollTo({ top: 0, behavior: 'instant' });
}
function syncRoute() { const target = location.hash.slice(2) as Page; page.value = navigation.some(n => n.id === target) ? target : 'overview'; routeFilter.value = ''; viewKey.value++; mobileOpen.value = false; }
function closeModal() { modal.value = null; formError.value = ''; }
function showDetail(value: Detail) { detail.value = value; modal.value = 'detail'; }
function askAction(title: string, text: string, required: boolean, danger: boolean, run: (reason: string) => Promise<unknown>) {
  action.value = { title, text, reasonRequired: required, danger, run }; reason.value = ''; formError.value = ''; modal.value = 'action';
}
const busy = ref(false);
const loadError = ref('');
let polling: ReturnType<typeof setInterval>;
let fetching = false;
async function loadSnapshot() {
 if (!workspaceSession.value || fetching) return; fetching = true;
 try {
  const data = await workspaceRequest('snapshot');
  function sync<T extends {id: string | number}>(list: T[], incoming: T[]) { const merged = incoming.map(item => { const existing = list.find(old => old.id === item.id); return existing ? Object.assign(existing, item) : item; }); list.splice(0, list.length, ...merged); }
  sync(merchants, data.merchants); sync(products, data.products); sync(orders, data.orders);
  sync(aftersales, data.aftersales); sync(settlements, data.settlements); sync(auditLogs, data.auditLogs); loadError.value = '';
 } catch(e) { loadError.value = (e as Error).message; } finally { fetching = false; }
}
async function submitAction() {
 if (!action.value || busy.value) return;
 if (action.value.reasonRequired && reason.value.trim().length < 4) { formError.value = '请填写至少 4 个字的处理原因。'; return; }
 busy.value = true;
 try { await action.value.run(reason.value.trim()); await loadSnapshot(); closeModal(); notify('操作已保存'); }
 catch(e) { formError.value = (e as Error).message; } finally { busy.value = false; }
}
function reviewMerchant(m: Merchant, approve: boolean) { askAction(approve ? '通过入驻审核' : '驳回入驻申请', m.name + ' · 审核结果会保存并同步到商家端。', !approve, !approve, text => workspaceRequest('merchants/' + m.id + '/action', { action: approve ? 'approve' : 'reject', reason: text })); }
function toggleMerchant(m: Merchant) { const enable = m.status === '已停用'; askAction(enable ? '恢复营业' : '停用商家', enable ? '恢复营业后商品需重新审核。' : '停用后销售中的商品会同步下架。', true, !enable, text => workspaceRequest('merchants/' + m.id + '/action', { action: enable ? 'enable' : 'disable', reason: text })); }
function reviewProduct(p: Product, approve: boolean) { askAction(approve ? '通过商品审核' : '驳回商品审核', p.name + (approve ? ' · 通过后用户可在商城购买。' : ' · 请填写修改要求。'), !approve, !approve, text => workspaceRequest('products/' + p.id + '/action', { action: approve ? 'approve' : 'reject', reason: text })); }
function takeDown(p: Product) { askAction('下架商品', p.name + ' 将停止销售，历史订单保留。', true, true, text => workspaceRequest('products/' + p.id + '/action', { action: 'down', reason: text })); }
function resolveAfterSale(a: AfterSale, approve: boolean) { askAction(approve ? '确认退款' : '驳回退款', '订单 ' + a.orderId + ' · 退款金额 ¥' + money(a.amount) + (approve ? '，请核对原支付记录及退款金额。' : '，请说明驳回原因，结果会同步给买家。'), true, !approve, text => workspaceRequest('aftersales/' + a.id + '/action', { action: approve ? 'approve' : 'reject', reason: text })); }
function verifySettlement(_s: Settlement) { notify('结算服务暂不可用，请稍后处理。'); }
function keyboard(e: KeyboardEvent) { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); search.value = ''; modal.value = 'search'; } if (e.key === 'Escape') mobileOpen.value = false; }
onMounted(() => { syncRoute(); loadSnapshot(); polling = setInterval(loadSnapshot, 5000); window.addEventListener('hashchange', syncRoute); window.addEventListener('popstate', syncRoute); window.addEventListener('keydown', keyboard); });
onBeforeUnmount(() => { clearInterval(polling); window.removeEventListener('hashchange', syncRoute); window.removeEventListener('popstate', syncRoute); window.removeEventListener('keydown', keyboard); });
</script>

<template>
  <WorkspaceLogin v-if="!workspaceSession" @login="loadSnapshot" />
  <template v-else>
  <a href="#main-content" class="skip-link">跳转到主要内容</a>
  <div class="workspace">
    <button v-if="mobileOpen" class="sidebar-scrim" aria-label="关闭导航" @click="mobileOpen = false" />
    <aside :class="['sidebar', { open: mobileOpen }]">
      <a class="brand" href="#/overview" @click.prevent="navigate('overview')"><span class="brand-wordmark">Lectus<span>·</span></span><span>莱择 · 平台运营</span></a>
      <div class="workspace-identity"><span class="identity-icon"><ShieldCheck :size="22" aria-hidden="true" /></span><div><strong>平台运营中心</strong><small><i class="status-dot" />账号已登录</small></div></div>
      <div class="nav-caption">运营管理<span>PLATFORM</span></div>
      <nav aria-label="平台主导航"><a v-for="n in navigation" :key="n.id" :href="`#/${n.id}`" :class="['nav-item', { active: page === n.id }]" :aria-current="page === n.id ? 'page' : undefined" @click.prevent="navigate(n.id)"><component :is="n.icon" :size="20" aria-hidden="true" /><span>{{ n.name }}</span><span v-if="['merchants', 'products', 'aftersales'].includes(n.id) && pendingCounts[n.id as keyof typeof pendingCounts]" class="nav-count">{{ pendingCounts[n.id as keyof typeof pendingCounts] }}</span><i v-else-if="page === n.id" class="nav-dot" /></a></nav>
      <div class="sidebar-bottom"><button class="help-link" @click="modal = 'help'"><CircleHelp :size="19" aria-hidden="true" />使用说明<ArrowUpRight :size="15" aria-hidden="true" /></button><button class="account-button" @click="navigate('settings')"><span class="avatar">L</span><span><strong>平台管理员</strong><small>后台账号</small></span><Settings2 :size="17" aria-hidden="true" /></button><button class="help-link" @click="workspaceLogout">退出登录</button></div>
    </aside>
    <div class="main-shell">
      <header class="topbar"><div class="breadcrumbs"><button class="icon-button mobile-menu" aria-label="打开导航" @click="mobileOpen = true"><Menu :size="21" aria-hidden="true" /></button><span>平台运营</span><ChevronRight :size="14" aria-hidden="true" /><strong>{{ current.name }}</strong></div><div class="topbar-actions"><span class="preview-label"><i class="status-dot" />业务动态 · 自动更新</span><button class="search-trigger" @click="search = ''; modal = 'search'"><Search :size="17" aria-hidden="true" /><span>搜索商家、订单</span><kbd>Ctrl K</kbd></button><button class="icon-button notice-button" aria-label="查看待办通知" @click="modal = 'notifications'"><Bell :size="20" aria-hidden="true" /><i v-if="!readNotices && pendingTotal" /></button><button class="top-avatar" aria-label="平台设置" @click="navigate('settings')">L</button></div></header>
      <main id="main-content" class="main-content" tabindex="-1">
        <header class="page-heading"><div><span class="eyebrow">LECTUS / PLATFORM OPERATIONS</span><h1>{{ current.name }}<span class="heading-dot" /></h1><p>{{ current.description }}</p></div><div class="page-date"><span class="status-dot" />工作台<span>平台运营</span></div></header>
        <div v-if="loadError" class="demo-notice" role="alert">{{ loadError }} <button class="text-button" @click="loadSnapshot">重试</button></div>
        <OverviewView v-if="page === 'overview'" @navigate="navigate" @detail="showDetail" />
        <RegistryView v-else-if="page === 'merchants' || page === 'products'" :key="viewKey" :page="page" :initial-filter="routeFilter" @detail="showDetail" />
        <TransactionsView v-else-if="page === 'orders' || page === 'aftersales' || page === 'finance'" :key="viewKey" :page="page" :initial-filter="routeFilter" @detail="showDetail" />
        <SettingsView v-else :key="viewKey" :initial-filter="routeFilter" />
        <footer class="workspace-footer"><span><strong>Lectus<span>·</span></strong>平台运营后台</span><span>商家经营 · 订单履约 · 平台服务</span></footer>
      </main>
    </div>
  </div>

  <Modal :open="modal !== null" :title="modalTitle" :wide="modal === 'detail'" @close="closeModal">
    <template v-if="modal === 'action' && action"><p class="modal-note">{{ action.text }}</p><form @submit.prevent="submitAction"><label v-if="action.reasonRequired" class="field-label">处理原因<textarea v-model="reason" required minlength="4" maxlength="200" rows="3" placeholder="填写具体原因，便于后续查询" /></label><p v-if="formError" class="form-error" role="alert">{{ formError }}</p><div class="demo-notice"><FileText :size="16" aria-hidden="true" />本次处理将记入操作日志。</div><footer class="form-footer"><button type="button" class="secondary-button" @click="closeModal">取消</button><button type="submit" :disabled="busy" :class="action.danger ? 'danger-button' : 'primary-button'">确认操作</button></footer></form></template>
    <template v-else-if="modal === 'search'"><label class="search-field modal-search"><Search :size="19" aria-hidden="true" /><input v-model="search" aria-label="全局搜索" placeholder="输入商家名、商品名或订单号" autofocus /></label><div class="search-results"><span class="section-label">商家</span><button v-for="m in matchedMerchants" :key="m.id" @click="showDetail({ kind: 'merchant', item: m })"><span class="avatar">{{ m.initials }}</span><span><strong>{{ m.name }}</strong><small>{{ m.id }} · {{ m.status }}</small></span><ChevronRight :size="17" aria-hidden="true" /></button><template v-if="matchedProducts.length"><span class="section-label">商品</span><button v-for="p in matchedProducts" :key="p.id" @click="showDetail({ kind: 'product', item: p })"><img :src="imageUrl(p.image)" alt="" /><span><strong>{{ p.name }}</strong><small>{{ p.id }} · {{ p.status }}</small></span><ChevronRight :size="17" aria-hidden="true" /></button></template><template v-if="matchedOrders.length"><span class="section-label">订单</span><button v-for="o in matchedOrders" :key="o.id" @click="showDetail({ kind: 'order', item: o })"><ShoppingBag :size="22" aria-hidden="true" /><span><strong>{{ o.id }}</strong><small>{{ merchantName(o.merchantId) }} · {{ o.status }}</small></span><ChevronRight :size="17" aria-hidden="true" /></button></template><p v-if="!matchedMerchants.length && !matchedProducts.length && !matchedOrders.length" class="empty-state">未找到匹配记录，请更换关键词。</p></div></template>
    <template v-else-if="modal === 'notifications'"><div class="section-heading"><p class="muted">当前有 {{ pendingTotal }} 项运营待办</p><button class="text-button" @click="readNotices = true"><Check :size="16" aria-hidden="true" />{{ readNotices ? '已读' : '标记已读' }}</button></div><button v-for="n in [{ p: 'merchants', title: '商家入驻待审核', filter: '待审核' }, { p: 'products', title: '商品待审核', filter: '待审核' }, { p: 'aftersales', title: '售后待处理', filter: '待处理' }, { p: 'finance', title: '结算待核对', filter: '待核对' }]" :key="n.p" class="notice-row" @click="closeModal(); navigate(n.p as Page, n.filter)"><span>{{ n.title }}</span><strong>{{ pendingCounts[n.p as keyof typeof pendingCounts] }}</strong><ChevronRight :size="18" aria-hidden="true" /></button></template>
    <template v-else-if="modal === 'help'"><p class="modal-note">管理平台商品、订单和商家，查看最新业务状态与操作记录。</p><ul class="help-list"><li><strong>商家与商品</strong><span>支持搜索、筛选、查看资料、审核与停用。自营店与入驻商家在同一列表中管理。</span></li><li><strong>订单与售后</strong><span>查询订单、支付流水和配送进度；退款审核结果会同步给买家和商家。</span></li><li><strong>结算与日志</strong><span>账单按关联的已完成订单计算；核对操作不触发打款。操作日志在平台设置中查看。</span></li></ul><div class="demo-notice">账单核对与资金划转分开处理，实际到账请以支付机构记录为准。</div></template>
    <template v-else-if="modal === 'detail' && detail">
      <template v-if="detail.kind === 'merchant'"><div class="detail-title"><span class="avatar large">{{ detail.item.initials }}</span><div><h3>{{ detail.item.name }}</h3><span class="muted">{{ detail.item.id }} · {{ detail.item.type }}</span></div><span class="badge">{{ detail.item.status }}</span></div><dl class="detail-grid"><div><dt>企业名称</dt><dd>{{ detail.item.company }}</dd></div><div><dt>经营类目</dt><dd>{{ detail.item.category }}</dd></div><div><dt>联系人</dt><dd>{{ detail.item.contact }}</dd></div><div><dt>申请时间</dt><dd>{{ detail.item.submitted }}</dd></div><div><dt>资质资料</dt><dd>请核验商家提交的营业执照及品牌授权文件</dd></div><div v-if="detail.item.reason"><dt>最近处理原因</dt><dd>{{ businessNote(detail.item.reason) }}</dd></div></dl><div class="demo-notice">请完成企业资质和经营范围核验后，再处理入驻申请。</div><footer class="form-footer"><template v-if="detail.item.status === '待审核'"><button class="secondary-button" @click="reviewMerchant(detail.item, false)">驳回申请</button><button class="primary-button" @click="reviewMerchant(detail.item, true)"><Check :size="17" aria-hidden="true" />通过审核</button></template><button v-else-if="detail.item.type !== '自营' && ['营业中', '已停用'].includes(detail.item.status)" :class="detail.item.status === '营业中' ? 'danger-button' : 'primary-button'" @click="toggleMerchant(detail.item)">{{ detail.item.status === '营业中' ? '停用商家' : '恢复营业' }}</button><button v-else class="secondary-button" @click="closeModal">关闭</button></footer></template>
      <template v-else-if="detail.kind === 'product'"><div class="detail-product"><img :src="imageUrl(detail.item.image)" :alt="detail.item.name" /><div><span class="eyebrow">{{ detail.item.id }}</span><h3>{{ detail.item.name }}</h3><p>{{ detail.item.spec }}</p><strong>¥{{ money(detail.item.price) }}</strong></div></div><dl class="detail-grid"><div><dt>所属商家</dt><dd>{{ merchantName(detail.item.merchantId) }}</dd></div><div><dt>商品状态</dt><dd>{{ detail.item.status }}</dd></div><div><dt>库存</dt><dd>{{ detail.item.stock }} 件</dd></div><div><dt>提交时间</dt><dd>{{ detail.item.submitted }}</dd></div><div v-if="detail.item.reason"><dt>处理原因</dt><dd>{{ businessNote(detail.item.reason) }}</dd></div></dl><p class="modal-note">请核对商品图文、品牌授权及规格信息后再审核。</p><footer class="form-footer"><template v-if="detail.item.status === '待审核'"><button class="secondary-button" @click="reviewProduct(detail.item, false)">驳回审核</button><button class="primary-button" @click="reviewProduct(detail.item, true)">通过并上架</button></template><button v-else-if="detail.item.status === '销售中'" class="danger-button" @click="takeDown(detail.item)">下架商品</button><button v-else class="secondary-button" @click="closeModal">关闭</button></footer></template>
      <template v-else-if="detail.kind === 'order'"><div class="detail-title"><ShoppingBag :size="26" aria-hidden="true" /><div><h3>{{ detail.item.id }}</h3><span class="muted">{{ detail.item.time }}</span></div><span class="badge">{{ detail.item.status }}</span></div><dl class="detail-grid"><div><dt>商家</dt><dd>{{ merchantName(detail.item.merchantId) }}</dd></div><div><dt>商品</dt><dd>{{ productName(detail.item.productId) }} × {{ detail.item.quantity }}</dd></div><div><dt>买家</dt><dd>{{ detail.item.customer }}</dd></div><div><dt>订单金额</dt><dd>¥{{ money(detail.item.amount) }}</dd></div><div><dt>物流信息</dt><dd>{{ detail.item.tracking ? `${detail.item.carrier} · ${detail.item.tracking}` : '暂无物流记录' }}</dd></div><div><dt>收货信息</dt><dd>{{ detail.item.recipient }} · {{ detail.item.phone }} · {{ detail.item.address }}</dd></div></dl><div class="demo-notice">平台仅查询订单。发货由商家处理，退款在售后模块处理。</div><OrderJourney :order="detail.item" /></template>
      <template v-else-if="detail.kind === 'aftersale'"><div class="detail-title"><RotateCcw :size="25" aria-hidden="true" /><div><h3>{{ detail.item.id }}</h3><span class="muted">{{ detail.item.time }}</span></div><span class="badge">{{ detail.item.status }}</span></div><dl class="detail-grid"><div><dt>关联订单</dt><dd>{{ detail.item.orderId }}</dd></div><div><dt>所属商家</dt><dd>{{ relatedOrder ? merchantName(relatedOrder.merchantId) : '—' }}</dd></div><div><dt>申请原因</dt><dd>{{ businessNote(detail.item.reason) }}</dd></div><div><dt>退款金额</dt><dd>¥{{ money(detail.item.amount) }}</dd></div><div><dt>退回方式</dt><dd>{{ detail.item.paymentMethod || '原支付方式' }} · 原路退回</dd></div><div v-if="detail.item.refundRef"><dt>退款凭证</dt><dd>{{ transactionReference(detail.item.refundRef) }}</dd></div><div v-if="detail.item.resolvedAt"><dt>处理时间</dt><dd>{{ new Date(detail.item.resolvedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) }}</dd></div><div v-if="detail.item.result"><dt>处理说明</dt><dd>{{ businessNote(detail.item.result) }}</dd></div></dl><div class="demo-notice">退款结果以支付机构返回的状态为准。</div><footer v-if="detail.item.status === '待处理'" class="form-footer"><button class="secondary-button" @click="resolveAfterSale(detail.item, false)">驳回申请</button><button class="primary-button" @click="resolveAfterSale(detail.item, true)">同意退款</button></footer><OrderJourney v-if="relatedOrder" :order="relatedOrder" /></template>
      <template v-else-if="detail.kind === 'settlement'"><div class="detail-title"><Wallet :size="25" aria-hidden="true" /><div><h3>{{ detail.item.id }}</h3><span class="muted">{{ merchantName(detail.item.merchantId) }}</span></div><span class="badge">{{ detail.item.status }}</span></div><dl class="detail-grid"><div><dt>账单周期</dt><dd>{{ detail.item.period }}</dd></div><div><dt>已完成订单金额</dt><dd>¥{{ money(settlementTotals(detail.item).gross) }}</dd></div><div><dt>服务费（{{ detail.item.rate * 100 }}%）</dt><dd>¥{{ money(settlementTotals(detail.item).fee) }}</dd></div><div><dt>应结金额</dt><dd><strong>¥{{ money(settlementTotals(detail.item).net) }}</strong></dd></div></dl><h3 class="detail-subheading">关联订单</h3><div class="bill-orders"><div v-for="id in detail.item.orderIds" :key="id"><span>{{ id }}</span><strong>¥{{ money(orders.find(o => o.id === id)?.amount || 0) }}</strong></div></div><div class="demo-notice">仅计入关联的已完成订单，运费、补贴及跨期退款请单独核对。账单核对不执行打款。</div><footer v-if="detail.item.status === '待核对'" class="form-footer"><button class="primary-button" @click="verifySettlement(detail.item)">确认核对</button></footer><p v-else class="modal-note">核对时间：{{ detail.item.verifiedAt }}</p></template>
    </template>
  </Modal>
  <Transition name="toast"><div v-if="toast" class="toast-message" role="status"><Check :size="18" aria-hidden="true" />{{ toast }}</div></Transition>
</template>
</template>

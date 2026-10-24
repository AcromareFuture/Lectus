<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { ArrowUpRight, TicketPercent, CalendarDays, BadgeCheck, Store, CreditCard, Download, Check, Sparkles, ChevronRight } from '@lucide/vue';
import { downloadCsv, money } from '../data/demo';
import type { Page } from '../data/demo';
defineProps<{ page: Page }>();
const emit = defineEmits<{ notify: [message: string] }>();
const shop = reactive({ name: '莱择官方旗舰店', intro: '莱择官方自营店，主营鞋履、服饰及生活方式商品。', contact: '店铺管理员', phone: '138****0826', autoReply: true });
const saved = ref(false);
const campaigns = reactive([
  { name: '新客满减券', subtitle: '新人专享券', value: '30', rule: '满 ¥399 可用', period: '2026.10.01 — 2026.10.31', claimed: 128, total: 500, active: true },
  { name: '秋季促销券', subtitle: '秋季店铺券', value: '80', rule: '满 ¥999 可用', period: '2026.10.15 — 2026.11.15', claimed: 86, total: 300, active: true },
]);
const transactions = [
  { date: '2026-10-21', id: 'ST-DEMO-1021', amount: 6380, fee: 191.4, status: '已结算' },
  { date: '2026-10-20', id: 'ST-DEMO-1020', amount: 4196, fee: 125.88, status: '已结算' },
  { date: '2026-10-19', id: 'ST-DEMO-1019', amount: 5396, fee: 161.88, status: '已结算' },
];
const total = computed(() => transactions.reduce((sum, item) => sum + item.amount - item.fee, 0));
function save() { saved.value = true; emit('notify', '店铺信息已在本次预览中更新，刷新后重置'); }
function exportFinance() { downloadCsv('Lectus-演示对账单.csv', [['演示数据，无真实资金结算'], ['结算日期', '结算单号', '成交金额', '示例服务费', '净额'], ...transactions.map(t => [t.date, t.id, t.amount, t.fee, t.amount - t.fee])]); emit('notify', '演示对账单已导出'); }
</script>

<template>
  <template v-if="page === 'marketing'">
    <section class="management-summary"><div><span class="eyebrow">CAMPAIGN MANAGEMENT</span><h2>优惠券管理</h2><p>查看优惠券使用门槛、有效期及领取情况。</p></div><span class="soft-label"><Sparkles :size="16" />活动预览</span></section>
    <div class="campaign-grid"><article v-for="campaign in campaigns" :key="campaign.name" class="panel campaign-card"><div class="campaign-top"><span class="campaign-symbol"><TicketPercent :size="25" /></span><span :class="['badge', campaign.active ? 'success' : 'neutral']">{{ campaign.active ? '进行中' : '已暂停' }}</span></div><p class="eyebrow">{{ campaign.subtitle }}</p><h2>{{ campaign.name }}</h2><div class="coupon-value"><span>¥</span>{{ campaign.value }}<small>{{ campaign.rule }}</small></div><div class="coupon-divider" /><p class="campaign-date"><CalendarDays :size="15" />{{ campaign.period }}</p><div class="campaign-progress"><span>已领取 {{ campaign.claimed }} / {{ campaign.total }} 张</span><strong>{{ Math.round(campaign.claimed / campaign.total * 100) }}%</strong></div><div class="progress-track"><i :style="{ width: `${campaign.claimed / campaign.total * 100}%` }" /></div><button class="secondary-button full-width" @click="campaign.active = !campaign.active; emit('notify', campaign.active ? '演示活动已恢复' : '演示活动已暂停')">{{ campaign.active ? '暂停活动' : '恢复活动' }}<ChevronRight :size="16" /></button></article></div><p class="page-note">优惠券规则及领取数为演示样例，活动切换只影响当前预览。</p>
  </template>
  <template v-else-if="page === 'finance'">
    <section class="management-summary"><div><span class="eyebrow">SETTLEMENT OVERVIEW</span><h2>结算概览</h2><p>对账信息预览，不涉及真实结算或资金操作。</p></div><button class="secondary-button" @click="exportFinance"><Download :size="17" />导出演示对账单</button></section>
    <div class="finance-summary"><article class="panel finance-highlight"><CreditCard :size="24" /><p>样例已结算净额</p><strong>¥{{ money(total) }}</strong><span>以下 3 笔样例金额合计</span></article><article class="panel finance-explanation"><BadgeCheck :size="26" /><div><h2>结算规则说明</h2><p>服务费按 3% 展示计算，仅用于查看对账界面。正式费率、结算周期及资金通道尚未接入。</p><span class="soft-label">示例费率 3% · 非正式账单</span></div></article></div>
    <section class="panel management-panel"><header class="panel-heading"><div><h2>结算明细</h2><p>示例账单<span class="dot-separator">·</span>人民币 CNY</p></div><span class="badge neutral">3 笔</span></header><div class="table-scroll"><table class="data-table"><thead><tr><th>结算单号</th><th>结算日期</th><th>成交金额</th><th>示例服务费</th><th>净额</th><th>状态</th></tr></thead><tbody><tr v-for="item in transactions" :key="item.id"><td class="amount-cell">{{ item.id }}</td><td>{{ item.date }}</td><td>¥{{ money(item.amount) }}</td><td>¥{{ money(item.fee) }}</td><td class="amount-cell">¥{{ money(item.amount - item.fee) }}</td><td><span class="badge success">{{ item.status }}</span></td></tr></tbody></table></div></section>
  </template>
  <template v-else>
    <section class="management-summary"><div><span class="eyebrow">STORE SETTINGS</span><h2>店铺资料</h2><p>维护店铺名称、简介及联系信息。</p></div><span class="soft-label"><Store :size="16" />店铺设置</span></section>
    <div class="settings-grid"><form class="panel settings-form" @submit.prevent="save"><header class="panel-heading"><div><h2>店铺基本信息</h2><p>修改仅用于当前前端预览</p></div><BadgeCheck :size="21" class="muted-icon" /></header><label class="field-label">店铺名称<input v-model="shop.name" maxlength="30" required /></label><label class="field-label">店铺简介<textarea v-model="shop.intro" rows="3" maxlength="120" required /></label><div class="form-two-columns"><label class="field-label">联系人<input v-model="shop.contact" maxlength="20" required /></label><label class="field-label">绑定手机号<input :value="shop.phone" readonly /><small>预览中不支持修改绑定手机</small></label></div><div class="setting-toggle"><span><strong>自动回复</strong><small>在买家首次咨询时发送欢迎语</small></span><button type="button" role="switch" :aria-checked="shop.autoReply" aria-label="自动回复" :class="['switch', { on: shop.autoReply }]" @click="shop.autoReply = !shop.autoReply"><i /></button></div><footer class="form-footer"><span v-if="saved" class="saved-label"><Check :size="16" />本次预览已保存</span><span v-else class="muted">刷新页面后恢复演示内容</span><button type="submit" class="primary-button">保存修改 <ArrowUpRight :size="16" /></button></footer></form><aside class="panel shop-profile"><div class="profile-logo">L<span>·</span></div><span class="badge success">官方自营 · 演示店铺</span><h2>{{ shop.name }}</h2><p>{{ shop.intro }}</p><dl><div><dt>店铺编号</dt><dd>LC-DEMO-0001</dd></div><div><dt>主营类目</dt><dd>鞋履 / 服饰 / 生活方式</dd></div><div><dt>店铺状态</dt><dd><span class="status-dot" />营业中（示例）</dd></div></dl></aside></div>
  </template>
</template>

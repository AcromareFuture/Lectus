<script setup lang="ts">
import { auditAction, businessNote } from '../../../../packages/business-records';
import { reactive, ref } from 'vue';
import { Check, ShieldCheck, FileText, Settings2 } from '@lucide/vue';
import { settings, auditLogs, notify, addLog } from '../data/platform';
const props = defineProps<{ initialFilter?: string }>();
const tab = ref(props.initialFilter === 'logs' ? 'logs' : 'basic');
const draft = reactive({ ...settings });
const error = ref('');
function save() {
  if (!draft.platformName.trim() || !/^[0-9+\- ]{7,20}$/.test(draft.supportPhone) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.contactEmail)) { error.value = '请填写平台名称、有效的客服电话和邮箱。'; return; }
  error.value = '设置服务暂不可用，请稍后再试。';
}
</script>
<template>
  <div class="settings-layout"><div class="settings-menu" aria-label="设置栏目"><button :aria-pressed="tab === 'basic'" @click="tab = 'basic'"><Settings2 :size="18" aria-hidden="true" />基本信息</button><button :aria-pressed="tab === 'logs'" @click="tab = 'logs'"><FileText :size="18" aria-hidden="true" />操作日志<span>{{ auditLogs.length }}</span></button><div class="settings-account"><ShieldCheck :size="22" aria-hidden="true" /><strong>平台管理员</strong><p>已登录平台管理账号，请妥善保管账号信息。</p></div></div>
    <section v-if="tab === 'basic'" class="panel settings-form"><header class="section-heading"><div><h2>平台基本信息</h2><p>维护平台对外展示的名称及联系信息</p></div></header><form @submit.prevent="save"><label class="field-label">平台名称<input v-model="draft.platformName" required maxlength="30" /></label><div class="form-columns"><label class="field-label">客服电话<input v-model="draft.supportPhone" placeholder="尚未设置客服电话" required maxlength="20" type="tel" /></label><label class="field-label">客服邮箱<input v-model="draft.contactEmail" placeholder="尚未设置客服邮箱" required maxlength="80" type="email" /></label></div><div class="settings-divider" /><h3 class="detail-subheading">通知偏好</h3><label class="setting-toggle"><span><strong>商品审核提醒</strong><small>接收商家提交商品后的待审核提醒</small></span><input v-model="draft.productReview" type="checkbox" role="switch" aria-label="商品审核提醒" /></label><label class="setting-toggle"><span><strong>商家入驻提醒</strong><small>接收新商家入驻申请提醒</small></span><input v-model="draft.merchantNotice" type="checkbox" role="switch" aria-label="商家入驻提醒" /></label><div class="demo-notice">设置服务暂不可用，请稍后再试。</div><p v-if="error" class="form-error" role="alert">{{ error }}</p><footer class="form-footer"><button type="button" class="secondary-button" @click="Object.assign(draft, settings); error = ''">重置修改</button><button type="submit" class="primary-button" disabled><Check :size="17" aria-hidden="true" />保存设置</button></footer></form></section>
    <section v-else class="panel management-panel"><header class="list-heading"><div><h2>操作日志</h2><p>记录平台审核、售后及账单处理操作</p></div><span class="badge neutral">{{ auditLogs.length }} 条</span></header><div class="table-scroll"><table class="data-table logs-table"><caption class="sr-only">平台操作日志</caption><thead><tr><th>时间（北京时间）</th><th>操作</th><th>对象</th><th>说明</th></tr></thead><tbody><tr v-for="log in auditLogs" :key="log.id"><td class="tabular muted">{{ log.time }}</td><td><strong>{{ auditAction(log.action) }}</strong></td><td class="tabular">{{ log.target }}</td><td class="log-detail">{{ businessNote(log.detail) }}</td></tr></tbody></table></div><footer class="table-footer">操作记录按时间倒序展示。</footer></section>
  </div>
</template>

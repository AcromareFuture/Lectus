<script setup lang="ts">
import { PackageCheck, Clock3 } from '@lucide/vue';
import { eventDetail, transactionReference } from '../../../../packages/business-records';
interface Event { key: string; title: string; detail: string; time: string }
defineProps<{ order: { paymentMethod?: string; paymentRef?: string; paidAt?: string; shippedAt?: string; completedAt?: string; closeReason?: string; events?: Event[] } }>();
const date = (value?: string) => value ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(value)) : '—';
</script>
<template>
 <section class="order-journey">
  <h3><PackageCheck :size="18" />交易与履约记录</h3>
  <dl v-if="order.paidAt" class="journey-facts"><div><dt>支付方式</dt><dd>{{ order.paymentMethod || '支付记录' }}</dd></div><div><dt>支付时间</dt><dd>{{ date(order.paidAt) }}</dd></div><div><dt>交易流水</dt><dd>{{ transactionReference(order.paymentRef) || '历史订单未记录' }}</dd></div><div v-if="order.shippedAt"><dt>发货时间</dt><dd>{{ date(order.shippedAt) }}</dd></div><div v-if="order.completedAt"><dt>收货时间</dt><dd>{{ date(order.completedAt) }}</dd></div></dl>
  <p v-if="order.closeReason" class="journey-note">{{ order.closeReason }}</p>
  <ol v-if="order.events?.length" class="journey-events"><li v-for="event in order.events" :key="event.key"><i /><div><strong>{{ event.title }}</strong><time>{{ date(event.time) }}</time><p>{{ eventDetail(event) }}</p></div></li></ol>
  <p v-else class="journey-note"><Clock3 :size="15" />历史订单暂无进度记录</p>
 </section>
</template>
<style scoped>
.order-journey{margin-top:28px;padding-top:24px;border-top:1px solid #dce7e8;color:#254b51}.order-journey h3{display:flex;align-items:center;gap:8px;margin:0 0 18px;font-size:15px}.order-journey h3>span{font-size:10px;font-weight:400;color:#517178;margin-left:auto;border:1px solid #c6dadd;padding:3px 7px;border-radius:4px}.journey-facts{font-size:12px;line-height:1.8;margin:0 0 24px}.journey-facts>div{display:flex;justify-content:space-between;gap:18px;margin:9px 0}.journey-facts dt{color:#5c7479;flex-shrink:0}.journey-facts dd{margin:0;text-align:right;overflow-wrap:anywhere}.journey-events{list-style:none;padding:0;margin:0}.journey-events li{display:flex;gap:14px;position:relative;padding-bottom:22px}.journey-events li:not(:last-child):before{content:'';position:absolute;width:1px;left:4px;top:12px;bottom:0;background:#d6e4e5}.journey-events li>i{position:relative;width:9px;height:9px;border-radius:50%;background:#a9c6ca;flex-shrink:0;margin-top:5px}.journey-events li:first-child>i{background:#087783}.journey-events strong{font-size:13px;font-weight:600}.journey-events li:first-child strong{color:#087783}.journey-events time{font-size:11px;color:#5c7479;margin-left:12px}.journey-events p,.journey-note{font-size:12px;color:#5c7479;line-height:1.8;margin:5px 0 0}.journey-note{display:flex;gap:8px;align-items:center}
@media(max-width:480px){.journey-events time{display:block;margin:4px 0 0}.journey-facts>div{gap:12px}}
</style>

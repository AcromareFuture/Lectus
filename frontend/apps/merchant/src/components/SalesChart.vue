<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { Period, Order } from '../data/demo';
import { money } from '../data/demo';
import { orderMetrics } from '../data/metrics';
const props = defineProps<{ period: Period; metric: 'sales' | 'orders'; orders: Order[] }>();
const selected = ref<number | null>(null);
watch(() => [props.period, props.metric], () => selected.value = null);
const data = computed(() => orderMetrics(props.orders, props.period));
const chart = computed(() => {
  const values = props.metric === 'sales' ? data.value.values : data.value.orderCounts;
  const previous = props.metric === 'sales' ? data.value.previous : data.value.previousCounts;
  const max = Math.max(1, Math.ceil(Math.max(...values, ...previous, 0) * 1.1));
  return { values, previous, max, step: 552 / values.length };
});
const readout = computed(() => selected.value === null ? '' : props.metric === 'sales' ? `¥${money(chart.value.values[selected.value]!)}` : `${chart.value.values[selected.value]} 单`);
</script>

<template>
  <div class="sales-chart">
    <div class="chart-unit">{{ metric === 'sales' ? '单位：元' : '单位：单' }}<span v-if="selected !== null" class="chart-readout">{{ data.labels[selected] }} · {{ readout }}</span><span v-else class="chart-hint">悬停或选择柱形查看</span></div>
    <svg viewBox="0 0 620 235" role="group" :aria-label="`${data.label}${metric === 'sales' ? '成交金额' : '成交订单'}趋势`">
      <g v-for="tick in [0, 1, 2, 3, 4]" :key="tick">
        <line x1="40" x2="606" :y1="20 + tick * 44" :y2="20 + tick * 44" class="grid-line" />
        <text x="27" :y="24 + tick * 44" text-anchor="end" class="axis-label">{{ chart.max * (4 - tick) / 4 }}</text>
      </g>
      <g v-for="(value, i) in chart.values" :key="`${period}-${metric}-${i}`" class="chart-bar" role="button" tabindex="0" :aria-label="`${data.labels[i]}，${metric === 'sales' ? money(value) + '元' : value + '单'}`" :aria-pressed="selected === i" @mouseenter="selected = i" @focus="selected = i" @click="selected = i" @keydown.enter.prevent="selected = i" @keydown.space.prevent="selected = i">
        <rect :x="47 + i * chart.step" y="18" :width="chart.step - 10" height="182" fill="transparent" />
        <rect :x="49 + i * chart.step" :y="196 - chart.previous[i]! / chart.max * 176" :width="Math.max(8, chart.step * .24)" :height="chart.previous[i]! / chart.max * 176" rx="4" class="bar-previous" />
        <rect :x="49 + i * chart.step + chart.step * .28" :y="196 - value / chart.max * 176" :width="Math.max(10, chart.step * .32)" :height="value / chart.max * 176" rx="5" :class="['bar-current', { selected: selected === i }]" />
        <text :x="49 + i * chart.step + chart.step * .3" y="224" text-anchor="middle" class="axis-label">{{ data.labels[i] }}</text>
      </g>
    </svg>
    <p class="chart-footnote">按支付日期汇总，扣除已退款订单；上一周期为此前等长时段。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { init, use, graphic } from 'echarts/core';
import type { EChartsType, ComposeOption } from 'echarts/core';
import { LineChart } from 'echarts/charts';
import type { LineSeriesOption } from 'echarts/charts';
import { GridComponent, TooltipComponent, MarkPointComponent } from 'echarts/components';
import type { GridComponentOption, TooltipComponentOption } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { ArrowUpRight, ArrowDownRight, Minus } from '@lucide/vue';
import { money } from '../data/platform';

use([LineChart, GridComponent, TooltipComponent, MarkPointComponent, CanvasRenderer]);
type Point = { day: string; amount: number; count: number };
type ChartOption = ComposeOption<LineSeriesOption | GridComponentOption | TooltipComponentOption>;
const props = defineProps<{ series: Point[]; metric: 'amount' | 'count'; selectedDay: string }>();
const emit = defineEmits<{ select: [day: string] }>();
const host = ref<HTMLDivElement>();
let chart: EChartsType | undefined;
let observer: ResizeObserver | undefined;
const selectedIndex = computed(() => {
  const index = props.series.findIndex(p => p.day === props.selectedDay);
  return index < 0 ? props.series.length - 1 : index;
});
const selected = computed(() => props.series[selectedIndex.value]!);
const valueLabel = (value: number) => props.metric === 'amount' ? `¥${money(value)}` : `${value} 单`;
const selectedDate = computed(() => new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long', timeZone: 'UTC' }).format(new Date(`${selected.value.day}T00:00:00Z`)));
const change = computed(() => {
  const previous = props.series[selectedIndex.value - 1];
  if (!previous || previous[props.metric] === 0) return null;
  return (selected.value[props.metric] - previous[props.metric]) / previous[props.metric] * 100;
});
const peak = computed(() => props.series.reduce((a, b) => b[props.metric] > a[props.metric] ? b : a));
const average = computed(() => props.series.reduce((sum, p) => sum + p[props.metric], 0) / props.series.length);

function render() {
  if (!chart) return;
  const maximum = Math.max(...props.series.map(p => p[props.metric]), 1);
  const roughStep = maximum / 4;
  const magnitude = 10 ** Math.floor(Math.log10(roughStep));
  const step = props.metric === 'count' ? Math.max(1, Math.ceil(roughStep)) : [1, 2, 2.5, 5, 10].map(n => n * magnitude).find(n => n >= roughStep)!;
  const ceiling = Math.ceil(maximum / step) * step;
  const narrow = (host.value?.clientWidth || 600) < 400;
  const option: ChartOption = {
    animation: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    animationDuration: 380,
    animationDurationUpdate: 220,
    textStyle: { fontFamily: 'Manrope, Microsoft YaHei, sans-serif', color: '#63736e' },
    grid: { top: 21, right: 13, bottom: 28, left: props.metric === 'amount' ? 46 : 27 },
    tooltip: {
      trigger: 'axis', confine: true, renderMode: 'richText',
      backgroundColor: '#ffffff', borderColor: '#e0e9e5', borderWidth: 1, padding: [11, 14],
      textStyle: { color: '#1b2325', fontSize: 12, lineHeight: 22 },
      extraCssText: 'box-shadow:0 6px 20px rgba(27,35,37,.08);border-radius:6px;',
      axisPointer: { type: 'line', lineStyle: { color: '#6dbdc2', type: 'dashed', width: 1 }, label: { show: false } },
      formatter: params => {
        const item = (Array.isArray(params) ? params[0] : params) as { dataIndex: number };
        const point = props.series[item.dataIndex]!;
        return `${point.day}\n${props.metric === 'amount' ? '成交金额' : '成交订单'}    ${valueLabel(point[props.metric])}`;
      },
    },
    xAxis: {
      type: 'category', boundaryGap: false, data: props.series.map(p => p.day),
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#788781', fontSize: narrow ? 9 : 10, margin: 13, hideOverlap: true, showMinLabel: true, showMaxLabel: true, interval: props.series.length > 7 ? 1 : 0, formatter: (date: string) => `${Number(date.slice(5, 7))}/${date.slice(8)}` },
      axisPointer: { snap: true },
    },
    yAxis: {
      type: 'value', min: 0, max: ceiling, interval: step,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#829089', fontSize: 10, margin: 12, formatter: (value: number) => props.metric === 'amount' && value >= 1000 ? `${Number((value / 1000).toFixed(1))}k` : String(value) },
      splitLine: { lineStyle: { color: '#dfe8e3', type: [3, 4], width: 1 } },
    },
    series: [{
      type: 'line', name: props.metric === 'amount' ? '成交金额' : '成交订单',
      data: props.series.map(p => p[props.metric]), smooth: 0.22, smoothMonotone: 'x',
      showSymbol: false, symbol: 'circle', symbolSize: 7,
      lineStyle: { color: '#16b8c8', width: 3, cap: 'round' },
      itemStyle: { color: '#16b8c8', borderColor: '#ffffff', borderWidth: 2 },
      areaStyle: { color: new graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(22,184,200,.19)' }, { offset: 1, color: 'rgba(22,184,200,.005)' }]) },
      emphasis: { scale: 1.3, lineStyle: { width: 3 } },
      markPoint: { silent: true, symbol: 'circle', symbolSize: 10, label: { show: false }, itemStyle: { color: '#16b8c8', borderColor: '#ffffff', borderWidth: 3, shadowColor: 'rgba(8,119,131,.14)', shadowBlur: 7 }, data: [{ name: '当前日期', coord: [selected.value.day, selected.value[props.metric]] }] },
    }],
  };
  chart.setOption(option, { notMerge: true });
}
function selectWithKeyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const index = event.key === 'Home' ? 0 : event.key === 'End' ? props.series.length - 1 : Math.min(props.series.length - 1, Math.max(0, selectedIndex.value + (event.key === 'ArrowRight' ? 1 : -1)));
  emit('select', props.series[index]!.day);
  chart?.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex: index });
}
onMounted(() => {
  chart = init(host.value!);
  chart.on('updateAxisPointer', (event: unknown) => {
    const index = Number((event as { axesInfo?: { value: number }[] }).axesInfo?.[0]?.value);
    if (Number.isInteger(index) && props.series[index]) emit('select', props.series[index].day);
  });
  observer = new ResizeObserver(() => { chart?.resize(); });
  observer.observe(host.value!);
  render();
});
watch(() => [props.series, props.metric], render, { deep: true });
watch(() => props.selectedDay, () => {
  chart?.setOption({ series: [{ markPoint: { data: [{ name: '当前日期', coord: [selected.value.day, selected.value[props.metric]] }] } }] });
  chart?.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex: selectedIndex.value });
});
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose(); });
</script>

<template>
  <div class="trend-readout">
    <div class="trend-value"><span>{{ selectedDate }}</span><strong>{{ valueLabel(selected[metric]) }}</strong></div>
    <div v-if="change !== null" :class="['trend-change', { negative: change < 0, unchanged: change === 0 }]"><span><ArrowUpRight v-if="change > 0" :size="14" /><ArrowDownRight v-else-if="change < 0" :size="14" /><Minus v-else :size="14" />{{ change > 0 ? '+' : '' }}{{ change.toFixed(1) }}%</span><small>较前一日</small></div>
    <span v-else class="trend-no-comparison">前一日无可比数据</span>
  </div>
  <div ref="host" class="trend-canvas" role="group" tabindex="0" :aria-label="`${series.length} 天${metric === 'amount' ? '成交金额' : '订单数'}趋势，当前 ${selected.day}，${valueLabel(selected[metric])}。使用左右方向键切换日期。`" @keydown="selectWithKeyboard" />
  <div class="trend-footer"><span>日均{{ metric === 'amount' ? '成交' : '订单' }}<strong>{{ metric === 'amount' ? valueLabel(average) : `${average.toFixed(1)} 单` }}</strong></span><span>最高<strong>{{ Number(peak.day.slice(5, 7)) }}/{{ peak.day.slice(8) }} · {{ valueLabel(peak[metric]) }}</strong></span></div>
  <table class="sr-only"><caption>每日成交数据</caption><thead><tr><th>日期</th><th>成交金额</th><th>订单数</th></tr></thead><tbody><tr v-for="point in series" :key="point.day"><td>{{ point.day }}</td><td>{{ money(point.amount) }} 元</td><td>{{ point.count }} 单</td></tr></tbody></table>
</template>

<style scoped>
.trend-readout{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:22px 0 0;min-height:67px}.trend-value>span{display:block;font-size:11px;color:var(--muted);margin-bottom:4px}.trend-value>strong{display:block;font-size:29px;line-height:1.35;font-weight:700;font-variant-numeric:tabular-nums;letter-spacing:0}.trend-change{text-align:right;flex-shrink:0}.trend-change>span{display:inline-flex;align-items:center;gap:2px;color:#087783;font-size:12px;font-weight:700;background:#e3f0eb;padding:2px 6px;border-radius:4px;font-variant-numeric:tabular-nums}.trend-change.negative>span{color:#9a5241;background:#f3e9e3}.trend-change.unchanged>span{color:var(--muted);background:#e8eeeb}.trend-change>small{display:block;margin-top:4px;font-size:10px;color:var(--muted)}.trend-no-comparison{font-size:10px;color:var(--muted)}.trend-canvas{height:225px;width:100%;margin-top:8px;touch-action:pan-y}.trend-canvas:focus-visible{outline-offset:1px;border-radius:4px}.trend-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;border-top:1px solid var(--line);margin-top:9px;padding-top:13px;color:var(--muted);font-size:10px;flex-wrap:wrap}.trend-footer>span{display:flex;align-items:center;gap:8px}.trend-footer strong{font-size:11px;font-weight:600;color:#42554d;font-variant-numeric:tabular-nums}.trend-footer>span:last-child strong{font-size:10px}
@media(max-width:500px){.trend-readout{margin-top:18px}.trend-value>strong{font-size:26px}.trend-canvas{height:205px}.trend-footer{gap:7px;align-items:flex-start}.trend-footer>span{gap:6px;font-size:9px}.trend-footer strong{font-size:10px}.trend-footer>span:last-child strong{font-size:9px}}
@media(max-width:360px){.trend-value>strong{font-size:24px}.trend-change>span{font-size:11px}.trend-footer{flex-direction:column;gap:7px}.trend-canvas{height:190px}}
</style>

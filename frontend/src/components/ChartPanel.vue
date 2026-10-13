<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { init, use, type EChartsType, type EChartsCoreOption } from 'echarts/core';
import { LineChart, RadarChart, BarChart } from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  RadarComponent,
  LegendComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
use([
  LineChart,
  RadarChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  RadarComponent,
  LegendComponent,
  CanvasRenderer,
]);
const props = defineProps<{ option: EChartsCoreOption; summary: string }>();
const el = ref<HTMLDivElement>();
let chart: EChartsType | undefined;
let observer: ResizeObserver | undefined;
onMounted(() => {
  if (el.value) {
    chart = init(el.value);
    chart.setOption(props.option);
    observer = new ResizeObserver(() => chart?.resize());
    observer.observe(el.value);
  }
});
watch(
  () => props.option,
  (value) => chart?.setOption(value, true),
  { deep: true },
);
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>
<template>
  <div ref="el" class="chart" role="img" :aria-label="summary"></div>
  <p class="chart-summary">{{ summary }}</p>
</template>

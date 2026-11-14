<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { execute } from '../api/contract';
import type { Operations, Program, ProgramPage } from '../api/generated';
import { useCatalogStore } from '../stores/catalog';
import SchoolSourceLinks from './SchoolSourceLinks.vue';

const props = defineProps<{ initialYear: number }>();
type Query = Operations['op019']['input']['query'];
const catalog = useCatalogStore();
const year = ref(props.initialYear);
const major = ref('');
const region = ref('');
const score = ref<number | ''>('');
const difficulty = ref<Query['difficulty'] | ''>('');
const page = ref<ProgramPage>();
const submitted = ref<Query>();
const busy = ref(false);
const error = ref('');
const lifetime = new AbortController();
const totalPages = computed(() =>
  Math.ceil((page.value?.total ?? 0) / (page.value?.pageSize || 20)),
);

async function query(filters: Query) {
  if (busy.value) return;
  submitted.value = filters;
  busy.value = true;
  page.value = undefined;
  error.value = '';
  try {
    const result = await execute('op019', { query: filters }, { signal: lifetime.signal });
    catalog.collect(result);
    page.value = result;
  } catch (e) {
    if (!lifetime.signal.aborted)
      error.value = e instanceof Error ? e.message : '查询失败，请重试。';
  } finally {
    busy.value = false;
  }
}
function submit() {
  void query({
    year: year.value,
    ...(major.value.trim() ? { major: major.value.trim() } : {}),
    ...(region.value.trim() ? { region: region.value.trim() } : {}),
    ...(typeof score.value === 'number' ? { score: score.value } : {}),
    ...(difficulty.value ? { difficulty: difficulty.value } : {}),
    page: 1,
  });
}
function move(offset: number) {
  if (submitted.value && page.value)
    void query({ ...submitted.value, page: page.value.page + offset });
}
function group(program: Program) {
  if (program.sources.some((source) => source.version.startsWith('official-'))) return '未评级';
  return { easy: '较低', medium: '常规', hard: '较高' }[program.difficulty];
}
onBeforeUnmount(() => lifetime.abort());
</script>

<template>
  <section class="card section-gap" aria-label="院校专业筛选">
    <div class="card-heading"><h2>查询院校专业</h2></div>
    <form class="program-filters" @submit.prevent="submit">
      <label class="field"
        >招生年份<input v-model.number="year" type="number" min="2000" max="2100" required
      /></label>
      <label class="field"
        >专业名称<input v-model="major" maxlength="100" placeholder="例如：计算机"
      /></label>
      <label class="field"
        >地区<input
          v-model="region"
          list="program-search-regions"
          maxlength="100"
          placeholder="全部地区"
      /></label>
      <datalist id="program-search-regions">
        <option
          v-for="area in ['上海', '江苏', '北京', '浙江', '广东', '湖北', '四川', '陕西']"
          :key="area"
          :value="area"
        />
      </datalist>
      <label class="field"
        >参考总分<input
          v-model.number="score"
          type="number"
          min="0"
          max="500"
          step="any"
          placeholder="不限"
      /></label>
      <label class="field"
        >系统梯度<select v-model="difficulty">
          <option value="">全部梯度</option>
          <option value="easy">较低</option>
          <option value="medium">常规（含未评级）</option>
          <option value="hard">较高</option>
        </select></label
      >
      <button class="btn" :disabled="busy">{{ busy ? '查询中…' : '查询' }}</button>
    </form>
    <p class="muted small">
      参考总分仅比较已有复试线，缺线记录仍会保留；系统梯度不是高校发布的指标。
    </p>
    <p v-if="error" class="inline-error space-top" role="alert">{{ error }}</p>
  </section>
  <section v-if="page" class="card section-gap" aria-label="院校专业查询结果">
    <div class="card-heading">
      <h2>查询结果</h2>
      <span class="tag">共 {{ page.total }} 条</span>
    </div>
    <p v-if="!page.items.length" class="muted">没有符合条件的记录，请调整筛选条件后重新查询。</p>
    <div v-else class="program-table-wrap">
      <table class="program-table">
        <thead>
          <tr>
            <th scope="col">院校 / 招生专业</th>
            <th scope="col">地区 / 年份</th>
            <th scope="col">复试总分线</th>
            <th scope="col">招生计划</th>
            <th scope="col">报录比</th>
            <th scope="col">系统梯度</th>
            <th scope="col">数据来源</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in page.items" :key="item.id">
            <th scope="row">
              {{ item.schoolName }}<small>{{ item.programName }}</small>
            </th>
            <td>{{ item.region }} · {{ item.year }}</td>
            <td>{{ item.scoreLine == null ? '暂无数据' : item.scoreLine + ' 分' }}</td>
            <td>{{ item.enrollment == null ? '暂无数据' : item.enrollment + ' 人' }}</td>
            <td>{{ item.admissionRatio == null ? '暂无数据' : item.admissionRatio }}</td>
            <td>{{ group(item) }}</td>
            <td>
              <SchoolSourceLinks :sources="item.sources" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="page.items.length" class="muted small space-top">
      招生计划与实际录取人数口径不同；复试线仅对应所列年份，以来源公告为准。
    </p>
    <div v-if="totalPages > 1" class="row space-top">
      <button class="btn secondary" :disabled="page.page <= 1" @click="move(-1)">上一页</button>
      <span>第 {{ page.page }} / {{ totalPages }} 页</span>
      <button class="btn secondary" :disabled="page.page >= totalPages" @click="move(1)">
        下一页
      </button>
    </div>
  </section>
</template>

<style scoped>
.program-filters {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr)) auto;
  gap: 12px;
  align-items: end;
  margin-bottom: 12px;
}
.program-filters .field {
  margin: 0;
  min-width: 0;
}
.program-filters input,
.program-filters select {
  width: 100%;
  min-width: 0;
}
.program-table-wrap {
  overflow-x: auto;
}
.program-table {
  width: 100%;
  min-width: 850px;
  border-collapse: collapse;
  text-align: left;
}
.program-table :is(th, td) {
  padding: 12px 10px;
  border-bottom: 1px solid #e1ebe6;
  vertical-align: top;
}
.program-table thead {
  background: #edf5f0;
}
.program-table small {
  display: block;
  font-weight: 400;
}
@media (max-width: 1220px) {
  .program-filters {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 570px) {
  .program-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

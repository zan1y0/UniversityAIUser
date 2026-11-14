<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import SchoolSourceLinks from './SchoolSourceLinks.vue';
import { execute, waitJob } from '../api/contract';
import type {
  Program,
  ProgramFacts,
  ProgramRecommendation,
  SchoolEvaluation,
} from '../api/generated';
import { sessionValue, saveSessionValue } from '../domain/session-storage';
import { useAppStore } from '../stores/app';
import { useCatalogStore } from '../stores/catalog';

const props = defineProps<{
  operation: 'op020' | 'op021' | 'op022' | 'op023' | 'op024';
  initialYear: number;
  title: string;
}>();
const app = useAppStore();
const catalog = useCatalogStore();
const storageKey = app.user ? 'school-evaluation:' + app.user.id : '';
const lifetime = new AbortController();
const year = ref<number | ''>(props.initialYear);
const major = ref('');
const regions = ref('');
const score = ref<number | ''>('');
const difficulty = ref<'easy' | 'medium' | 'hard'>('medium');
const programId = ref('');
const programKeyword = ref('');
const comparisonIds = ref(['', '', '', '']);
const metric = ref<'' | 'scoreLine' | 'enrollment' | 'admissionRatio'>('');
const evaluationId = ref(storageKey ? sessionValue(storageKey) || '' : '');
const programs = ref<Program[]>([]);
const catalogBusy = ref(false);
const catalogError = ref('');
const busy = ref(false);
const status = ref('');
const error = ref('');
const recommendations = ref<ProgramRecommendation[]>();
const facts = ref<ProgramFacts[]>();
const evaluation = ref<SchoolEvaluation>();
const submittedProgram = ref<Program>();
const submittedScore = ref<number>();
let catalogRequest: AbortController | undefined;
let evaluationRequest:
  { programId: string; estimatedScore: number; profileVersion: number; key: string } | undefined;

const needsPrograms = ['op021', 'op022', 'op023'].includes(props.operation);
const selectedProgram = computed(() =>
  programs.value.find((program) => program.id === programId.value),
);
const programOptions = computed(() => {
  const keyword = programKeyword.value.trim().toLowerCase();
  if (!keyword) return programs.value;
  const selected = new Set([programId.value, ...comparisonIds.value]);
  return programs.value.filter(
    (program) =>
      selected.has(program.id) ||
      `${program.schoolName} ${program.programName} ${program.region}`
        .toLowerCase()
        .includes(keyword),
  );
});
const evaluationProgram = computed(
  () =>
    submittedProgram.value ||
    catalog.options('programId').find((option) => option.value === evaluation.value?.programId)
      ?.record,
);
const metricNames = { scoreLine: '复试总分线', enrollment: '招生计划', admissionRatio: '报录比' };
const missingNames: Record<string, string> = {
  ...metricNames,
  calibratedHistoricalAdmissions: '经过校准的历史录取样本',
};
const visibleMetrics = computed(() =>
  (Object.keys(metricNames) as (keyof typeof metricNames)[]).filter((type) =>
    facts.value?.some((row) => row.metrics.some((value) => value.type === type)),
  ),
);

function formatMetric(type: keyof typeof metricNames, value: number | null) {
  if (value == null) return '暂无数据';
  if (type === 'scoreLine') return value + ' 分';
  if (type === 'enrollment') return value + ' 人';
  return String(value);
}
function regionValues(value: string) {
  return [
    ...new Set(
      value
        .split(/[、，,]/)
        .map((region) => region.trim())
        .filter(Boolean),
    ),
  ];
}
async function loadPrograms() {
  catalogRequest?.abort();
  programs.value = [];
  programId.value = '';
  comparisonIds.value = ['', '', '', ''];
  catalogError.value = '';
  const selectedYear = year.value;
  if (
    !Number.isInteger(selectedYear) ||
    typeof selectedYear !== 'number' ||
    selectedYear < 2000 ||
    selectedYear > 2100
  ) {
    catalogBusy.value = false;
    return;
  }
  const controller = new AbortController();
  catalogRequest = controller;
  catalogBusy.value = true;
  try {
    const query = { year: selectedYear, pageSize: 100 };
    const first = await execute<'op019'>('op019', { query }, { signal: controller.signal });
    const items = [...first.items];
    const pages = Math.ceil(first.total / first.pageSize);
    for (let page = 2; page <= pages; page++) {
      const next = await execute<'op019'>(
        'op019',
        { query: { ...query, page } },
        { signal: controller.signal },
      );
      items.push(...next.items);
    }
    if (!controller.signal.aborted) {
      programs.value = items;
      catalog.collect(items);
    }
  } catch (e) {
    if (!controller.signal.aborted)
      catalogError.value = e instanceof Error ? e.message : '院校专业载入失败。';
  } finally {
    if (!controller.signal.aborted) catalogBusy.value = false;
  }
}
if (needsPrograms) watch(year, () => void loadPrograms(), { immediate: true });
onBeforeUnmount(() => {
  lifetime.abort();
  catalogRequest?.abort();
});

async function submit() {
  if (busy.value) return;
  const options = { signal: lifetime.signal };
  const inputYear = year.value;
  const inputScore = score.value;
  const operation = props.operation;
  busy.value = true;
  error.value = '';
  status.value = '正在查询…';
  recommendations.value = undefined;
  facts.value = undefined;
  evaluation.value = undefined;
  submittedProgram.value = undefined;
  submittedScore.value = undefined;
  try {
    if (operation === 'op024') {
      evaluation.value = await execute(
        'op024',
        { path: { id: evaluationId.value.trim() } },
        options,
      );
    } else {
      if (typeof inputYear !== 'number') throw new Error('请选择招生年份。');
      switch (operation) {
        case 'op020': {
          const selectedRegions = regionValues(regions.value);
          const undergraduateMajor = major.value.trim();
          if (!undergraduateMajor) throw new Error('请填写本科专业。');
          const result = await execute(
            'op020',
            {
              body: {
                undergraduateMajor,
                ...(selectedRegions.length ? { regions: selectedRegions } : {}),
                year: inputYear,
                ...(typeof inputScore === 'number' ? { estimatedScore: inputScore } : {}),
                difficulty: difficulty.value,
              },
            },
            options,
          );
          const keyword = undergraduateMajor.toLowerCase();
          const majorMatches = (item: ProgramRecommendation) =>
            Number(item.program.major.toLowerCase().includes(keyword));
          recommendations.value = [...result.items].sort(
            (a, b) => majorMatches(b) - majorMatches(a),
          );
          catalog.collect(result);
          break;
        }
        case 'op021':
          facts.value = [
            await execute(
              'op021',
              {
                path: { id: programId.value },
                query: {
                  year: inputYear,
                  ...(metric.value ? { metricTypes: [metric.value] } : {}),
                },
              },
              options,
            ),
          ];
          break;
        case 'op022': {
          const ids = comparisonIds.value.filter(Boolean);
          if (ids.length < 2 || new Set(ids).size !== ids.length)
            throw new Error('请选择 2 至 4 个不同的院校专业。');
          facts.value = (
            await execute('op022', { body: { year: inputYear, programIds: ids } }, options)
          ).programs;
          break;
        }
        case 'op023': {
          const program = selectedProgram.value;
          if (!program || typeof inputScore !== 'number')
            throw new Error('请选择院校专业并填写参考总分。');
          const profile = await execute('op004', {}, options);
          catalog.collect(profile);
          const body = {
            programId: program.id,
            estimatedScore: inputScore,
            profileVersion: profile.version,
          };
          if (
            evaluationRequest?.programId !== body.programId ||
            evaluationRequest.estimatedScore !== body.estimatedScore ||
            evaluationRequest.profileVersion !== body.profileVersion
          )
            evaluationRequest = { ...body, key: crypto.randomUUID() };
          const job = await execute('op023', { body }, { ...options, key: evaluationRequest.key });
          const result = await waitJob(job.jobId, options.signal, (job) => {
            status.value = job.status === 'queued' ? '正在等待评估…' : '正在生成评估…';
          });
          if (result?.kind !== 'schoolEvaluation') throw new Error('评估未返回有效结果。');
          evaluationRequest = undefined;
          submittedProgram.value = program;
          submittedScore.value = inputScore;
          evaluation.value = result;
          break;
        }
      }
    }
    if (evaluation.value && storageKey) saveSessionValue(storageKey, evaluation.value.id);
  } catch (e) {
    if (!lifetime.signal.aborted)
      error.value = e instanceof Error ? e.message : '操作失败，请重试。';
  } finally {
    busy.value = false;
    status.value = '';
  }
}
</script>

<template>
  <section class="card section-gap" :aria-label="title">
    <div class="card-heading">
      <h2>{{ title }}</h2>
    </div>
    <form class="school-fields" @submit.prevent="submit">
      <label v-if="operation !== 'op024'" class="field">
        招生年份<input
          v-model.number="year"
          type="number"
          min="2000"
          max="2100"
          required
          :disabled="busy"
        />
      </label>
      <template v-if="operation === 'op020'">
        <label class="field"
          >本科专业<input
            v-model="major"
            maxlength="100"
            required
            placeholder="例如：计算机科学与技术"
        /></label>
        <label class="field">意向地区<input v-model="regions" placeholder="不限地区" /></label>
        <label class="field"
          >系统梯度<select v-model="difficulty">
            <option value="easy">较低</option>
            <option value="medium">常规（含未评级）</option>
            <option value="hard">较高</option>
          </select></label
        >
      </template>
      <label v-if="needsPrograms" class="field">
        搜索院校专业<input
          v-model="programKeyword"
          placeholder="院校名、专业名或代码"
          :disabled="catalogBusy || busy"
        />
      </label>
      <label v-if="operation === 'op021' || operation === 'op023'" class="field">
        院校专业<select v-model="programId" required :disabled="catalogBusy || busy">
          <option value="">{{ catalogBusy ? '正在加载…' : '请选择院校专业' }}</option>
          <option v-for="program in programOptions" :key="program.id" :value="program.id">
            {{ program.schoolName }} · {{ program.programName }}
          </option>
        </select>
      </label>
      <label v-if="operation === 'op021'" class="field">
        历史指标<select v-model="metric">
          <option value="">全部指标</option>
          <option v-for="(name, key) in metricNames" :key="key" :value="key">{{ name }}</option>
        </select>
      </label>
      <template v-if="operation === 'op022'">
        <label v-for="(_, index) in comparisonIds" :key="index" class="field">
          院校专业 {{ index + 1
          }}<select
            v-model="comparisonIds[index]"
            :required="index < 2"
            :disabled="catalogBusy || busy"
          >
            <option value="">{{ index < 2 ? '请选择' : '可选' }}</option>
            <option
              v-for="program in programOptions"
              :key="program.id"
              :value="program.id"
              :disabled="comparisonIds.some((id, slot) => slot !== index && id === program.id)"
            >
              {{ program.schoolName }} · {{ program.programName }}
            </option>
          </select>
        </label>
      </template>
      <label v-if="operation === 'op020' || operation === 'op023'" class="field">
        参考总分<input
          v-model.number="score"
          type="number"
          min="0"
          max="500"
          step="any"
          :required="operation === 'op023'"
          :placeholder="operation === 'op023' ? '请输入总分' : '选填'"
        />
      </label>
      <label v-if="operation === 'op024'" class="field">
        评估编号<input
          v-model.trim="evaluationId"
          required
          maxlength="36"
          placeholder="请输入评估结果上的编号"
        />
      </label>
      <button class="btn" :disabled="busy || (needsPrograms && (catalogBusy || !programs.length))">
        {{ operation === 'op023' ? '开始评估' : operation === 'op022' ? '对比' : '查询' }}
      </button>
    </form>
    <p v-if="operation === 'op024' && !evaluationId" class="muted small space-top">
      评估编号可在「分析上岸概率与风险」生成的结果中找到。
    </p>
    <p v-if="catalogError" class="inline-error space-top" role="alert">
      {{ catalogError }}
      <button class="text-btn" type="button" @click="loadPrograms">重新加载</button>
    </p>
    <p
      v-else-if="needsPrograms && !catalogBusy && !programs.length && year"
      class="muted small space-top"
    >
      该年份暂无可选院校专业。
    </p>
    <p
      v-else-if="needsPrograms && !catalogBusy && !programOptions.length"
      class="muted small space-top"
    >
      没有匹配的院校专业，请调整搜索词。
    </p>
    <p v-if="status" class="muted small space-top" role="status">{{ status }}</p>
    <p v-if="error" class="inline-error space-top" role="alert">{{ error }}</p>
  </section>

  <section
    v-if="recommendations || facts || evaluation"
    class="card section-gap"
    aria-label="择校查询结果"
  >
    <div class="card-heading">
      <h2>
        {{
          recommendations
            ? '推荐院校'
            : operation === 'op022'
              ? '院校对比'
              : facts
                ? '历史指标'
                : '择校评估结果'
        }}
      </h2>
    </div>
    <template v-if="recommendations">
      <p v-if="!recommendations.length" class="muted">
        没有符合条件的推荐，请调整年份、地区或系统梯度。
      </p>
      <div v-else class="result-scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">院校 / 招生专业</th>
              <th scope="col">地区 / 年份</th>
              <th scope="col">复试总分线</th>
              <th scope="col">招生计划</th>
              <th scope="col">系统匹配分</th>
              <th scope="col">匹配说明</th>
              <th scope="col">数据来源</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in recommendations" :key="item.program.id">
              <th scope="row">
                {{ item.program.schoolName }}<small>{{ item.program.programName }}</small>
              </th>
              <td>{{ item.program.region }} · {{ item.program.year }}</td>
              <td>{{ formatMetric('scoreLine', item.program.scoreLine) }}</td>
              <td>{{ formatMetric('enrollment', item.program.enrollment) }}</td>
              <td>
                {{ item.matchScore != null ? Math.round(item.matchScore) + ' / 100' : '未计算' }}
              </td>
              <td>{{ item.reasons.join('；') }}</td>
              <td><SchoolSourceLinks :sources="item.program.sources" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="recommendations.length" class="muted small space-top">
        系统匹配分用于参考排序。<span v-if="recommendations[0]?.risks.length">
          {{ recommendations[0].risks.join('；') }}</span
        >
      </p>
    </template>
    <template v-if="facts">
      <div class="result-scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">院校 / 招生专业</th>
              <th scope="col">地区 / 年份</th>
              <th v-for="type in visibleMetrics" :key="type" scope="col">
                {{ metricNames[type] }}
              </th>
              <th scope="col">数据来源</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in facts" :key="row.program.id">
              <th scope="row">
                {{ row.program.schoolName }}<small>{{ row.program.programName }}</small>
              </th>
              <td>{{ row.program.region }} · {{ row.program.year }}</td>
              <td v-for="type in visibleMetrics" :key="type">
                {{
                  formatMetric(
                    type,
                    row.metrics.find((value) => value.type === type)?.value ?? null,
                  )
                }}
              </td>
              <td><SchoolSourceLinks :sources="row.program.sources" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="muted small space-top">复试线和招生计划以所列年份的官方公告为准。</p>
    </template>
    <template v-if="evaluation">
      <p v-if="evaluationProgram">
        <strong>{{ evaluationProgram.schoolName }} · {{ evaluationProgram.programName }}</strong>
      </p>
      <dl class="evaluation-details">
        <div>
          <dt>上岸概率</dt>
          <dd>
            {{
              evaluation.probability == null
                ? '暂无可用数据'
                : (evaluation.probability * 100).toFixed(1) + '%'
            }}
          </dd>
        </div>
        <div>
          <dt>评估时间</dt>
          <dd>
            {{
              new Date(evaluation.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
            }}
          </dd>
        </div>
        <div>
          <dt>评估编号</dt>
          <dd class="break-text">{{ evaluation.id }}</dd>
        </div>
      </dl>
      <p>{{ evaluation.reason }}</p>
      <p v-if="submittedProgram?.scoreLine != null && submittedScore != null">
        参考总分 {{ submittedScore }} 分，{{ submittedProgram.year }} 年该专业复试线
        {{ submittedProgram.scoreLine }} 分。
      </p>
      <p v-if="evaluation.missingFields.length" class="muted">
        缺少资料 ·
        {{ evaluation.missingFields.map((field) => missingNames[field] || field).join('、') }}
      </p>
      <ul v-if="evaluation.risks.length">
        <li v-for="risk in evaluation.risks" :key="risk">{{ risk }}</li>
      </ul>
      <SchoolSourceLinks :sources="evaluation.sources" />
    </template>
  </section>
</template>

<style scoped>
.school-fields {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px;
}
.school-fields .field {
  flex: 1 1 145px;
  min-width: 0;
  margin: 0;
}
.school-fields input,
.school-fields select {
  width: 100%;
  min-width: 0;
}
.school-fields button {
  align-self: end;
}
.result-scroll {
  overflow-x: auto;
}
.result-scroll table {
  width: 100%;
  min-width: 620px;
  border-collapse: collapse;
  text-align: left;
}
.result-scroll :is(th, td) {
  padding: 12px 10px;
  border-bottom: 1px solid #e1ebe6;
  vertical-align: top;
}
.result-scroll thead {
  background: #edf5f0;
}
.result-scroll small {
  display: block;
  font-weight: 400;
}
.evaluation-details {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 28px;
}
.evaluation-details dd {
  margin: 4px 0;
}
.break-text {
  overflow-wrap: anywhere;
}
</style>

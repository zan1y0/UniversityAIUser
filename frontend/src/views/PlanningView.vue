<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAppStore } from '../stores/app';
import { useStudyStore } from '../stores/study';
import type { Plan } from '../domain/types';
import { createTasks, localDate } from '../domain/logic';
import { backend } from '../api/backend';
import { useLoad } from '../composables/useLoad';
import AsyncState from '../components/AsyncState.vue';
import AppIcon from '../components/AppIcon.vue';
const app = useAppStore();
const study = useStudyStore();
const { status, error, run } = useLoad();
const cycle = ref('周计划');
const hours = ref(4);
const target = ref('计算机技术 · 基础巩固');
const start = ref(localDate());
const draft = ref<Plan | null>(null);
const filter = ref('全部任务');
const rawPlan = ref('');
const adjust = ref(false);
const adjustment = ref(30);
const current = computed(() => draft.value || study.state.plan);
const tasks = computed(
  () =>
    current.value?.tasks.filter(
      (t) =>
        filter.value === '全部任务' ||
        (filter.value === '今日任务' && t.date === localDate()) ||
        (filter.value === '未完成' && !t.done),
    ) || [],
);
const completed = computed(() => current.value?.tasks.filter((t) => t.done).length || 0);
const progress = computed(() =>
  current.value?.tasks.length
    ? Math.round((completed.value / current.value.tasks.length) * 100)
    : 0,
);
async function generate() {
  if (app.mode === 'live' && app.user) {
    rawPlan.value = '';
    const data = await run(() => backend.plan(app.user!.id));
    if (data !== undefined) rawPlan.value = JSON.stringify(data, null, 2);
    return;
  }
  draft.value = {
    id: crypto.randomUUID(),
    version: (study.state.plan?.version || 0) + 1,
    cycle: cycle.value,
    target: target.value,
    tasks: createTasks(cycle.value, hours.value, start.value),
    createdAt: new Date().toISOString(),
  };
  app.notify('候选计划已生成，确认后才生效');
}
function activate() {
  if (!draft.value) return;
  if (study.state.plan && !confirm('确认用候选计划替换当前生效计划？原计划的任务进度将被替换。'))
    return;
  study.state.plan = draft.value;
  draft.value = null;
  app.notify('学习计划已在本地演示中生效');
}
function changePlan() {
  if (!study.state.plan) return;
  study.state.plan.tasks.filter((t) => !t.done).forEach((t) => (t.minutes = adjustment.value));
  study.state.plan.version++;
  adjust.value = false;
  app.notify('未完成任务时长已调整，计划版本已更新');
}
</script>
<template>
  <section class="card">
    <div class="card-heading">
      <h2>制定适合自己的节奏</h2>
      <span class="tag">日 / 周 / 月计划</span>
    </div>
    <form @submit.prevent="generate">
      <div class="grid four">
        <label class="field"
          >计划周期<select v-model="cycle">
            <option v-for="c in ['日计划', '周计划', '月计划']" :key="c">{{ c }}</option>
          </select></label
        ><label class="field"
          >每天可用时间（小时）<input
            v-model.number="hours"
            type="number"
            min="1"
            max="12"
            step="0.5"
            required /></label
        ><label class="field"
          >开始日期<input v-model="start" type="date" required :min="localDate()" /></label
        ><label class="field"
          >目标与基础<input
            v-model="target"
            required
            maxlength="80"
            placeholder="目标专业与目前基础"
        /></label>
      </div>
      <div class="form-actions">
        <button class="btn" :disabled="status === 'loading'">
          <AppIcon name="sparkles" :size="16" />生成候选计划</button
        ><span class="muted small">演示按四科学习时间均分，个性化 AI 编排待后端实现。</span>
      </div>
    </form>
    <AsyncState :status="status" :error="error" @retry="generate" />
    <div v-if="rawPlan" class="notice">
      接口返回候选数据如下。当前后端缺少候选计划
      DTO、确认生效与任务更新接口，真实模式暂不允许本地生效。
    </div>
    <pre v-if="rawPlan" class="reading">{{ rawPlan }}</pre>
  </section>
  <template v-if="app.mode === 'demo'"
    ><div v-if="draft" class="notice section-gap">
      <div class="row between wrap">
        <div>
          <strong>候选计划 · 尚未生效</strong>
          <p>核对任务后确认。已有计划只有在确认替换后才改变。</p>
        </div>
        <div class="row">
          <button class="btn secondary" @click="draft = null">取消候选</button
          ><button class="btn" @click="activate">确认生效</button>
        </div>
      </div>
    </div>
    <div v-if="current" class="split section-gap">
      <section class="card">
        <div class="card-heading">
          <div>
            <h2>{{ current.cycle }} · {{ draft ? '候选' : '生效' }}版本 {{ current.version }}</h2>
            <p class="muted small">{{ current.target }}</p>
          </div>
          <span class="tag">{{ completed }} / {{ current.tasks.length }} 完成</span>
        </div>
        <div class="read-progress">
          <span>任务完成进度</span><span>{{ progress }}%</span>
        </div>
        <div class="progress"><span :style="{ width: progress + '%' }"></span></div>
        <div class="tabs space-top">
          <button
            v-for="f in ['全部任务', '今日任务', '未完成']"
            :key="f"
            class="tab"
            :class="{ active: filter === f }"
            @click="filter = f"
          >
            {{ f }}
          </button>
        </div>
        <div v-for="t in tasks" :key="t.id" class="task-row" :class="{ done: t.done }">
          <input
            v-model="t.done"
            class="task-check"
            type="checkbox"
            :disabled="!!draft"
            :aria-label="`完成 ${t.date} ${t.title}`"
          />
          <div class="task-info">
            <h3>{{ t.title }}</h3>
            <p>{{ t.date }} · {{ t.subject }} · {{ t.minutes }} 分钟</p>
          </div>
          <span class="tag" :class="t.done ? 'green' : t.date < localDate() ? 'orange' : ''">{{
            t.done ? '已完成' : t.date < localDate() ? '已超期' : '待完成'
          }}</span>
        </div>
        <p v-if="!tasks.length" class="empty-hint">当前筛选下没有任务。</p>
      </section>
      <div class="stack">
        <section class="card">
          <h2>计划提醒</h2>
          <p class="muted small space-top">
            {{ current.tasks.filter((t) => !t.done && t.date < localDate()).length }} 项超期任务，{{
              current.tasks.filter((t) => !t.done && t.date === localDate()).length
            }}
            项今日任务。
          </p>
          <p class="muted small space-top">
            完成任务时勾选记录。任务时长是安排，不直接当成实际学习时长。
          </p>
          <RouterLink to="/companion" class="btn secondary stretch space-top"
            >与陪伴伙伴一起执行</RouterLink
          >
        </section>
        <section v-if="!draft" class="card">
          <h2>动态调整</h2>
          <p class="muted small space-top">按完成情况调整未完成任务的时长；不改动已完成记录。</p>
          <button class="btn secondary stretch space-top" @click="adjust = !adjust">
            调整未完成任务
          </button>
          <form v-if="adjust" @submit.prevent="changePlan">
            <label class="field space-top"
              >每项任务时长（分钟）<input
                v-model.number="adjustment"
                type="number"
                min="5"
                max="180"
                step="5"
                required /></label
            ><button class="btn stretch space-top">确认调整</button>
          </form>
        </section>
      </div>
    </div>
    <div v-else class="state-box section-gap">
      先填写目标与可用时间，生成第一份学习计划。
    </div></template
  >
  <p v-else class="notice section-gap">
    当前生成接口仅接收 userId，尚不接收周期、基础和时间参数；真实个性化规划需要先补全后端契约。
  </p>
</template>

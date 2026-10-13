<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useAppStore } from '../stores/app';
import { useStudyStore } from '../stores/study';
import { localDate } from '../domain/logic';
import { backend } from '../api/backend';
import { useLoad } from '../composables/useLoad';
import AppIcon from '../components/AppIcon.vue';
import ChartPanel from '../components/ChartPanel.vue';
import AvatarGuide from '../components/AvatarGuide.vue';
import AsyncState from '../components/AsyncState.vue';
const app = useAppStore();
const study = useStudyStore();
const { status, error, run } = useLoad();
const liveData = ref('');
const reportOpen = ref(false);
const tasks = computed(
  () => study.state.plan?.tasks.filter((t) => t.date === localDate()).slice(0, 4) || [],
);
const subjectMinutes = computed(() =>
  ['数学', '英语', '政治', '专业课'].map((s) =>
    study.state.events.filter((e) => e.subject === s).reduce((n, e) => n + e.minutes, 0),
  ),
);
const focusRate = computed(() =>
  study.totalMinutes
    ? Math.round(
        (study.state.events.filter((e) => e.focused).reduce((n, e) => n + e.minutes, 0) /
          study.totalMinutes) *
          100,
      )
    : 0,
);
const weekDays = computed(() =>
  Array.from({ length: 7 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - 6 + i);
    return localDate(d);
  }),
);
const trendOption = computed(() => ({
  color: ['#6b8ae5'],
  tooltip: { trigger: 'axis' },
  grid: { left: 35, right: 15, top: 20, bottom: 25 },
  xAxis: {
    type: 'category',
    data: weekDays.value.map((d) => d.slice(5)),
    axisLine: { lineStyle: { color: '#e7ecf5' } },
    axisTick: { show: false },
    axisLabel: { color: '#99a5ba', fontSize: 10 },
  },
  yAxis: {
    type: 'value',
    name: '分钟',
    nameTextStyle: { color: '#9aa7bb', fontSize: 10 },
    axisLabel: { color: '#99a5ba', fontSize: 10 },
    splitLine: { lineStyle: { color: '#f0f3f9', type: 'dashed' } },
  },
  series: [
    {
      type: 'line',
      smooth: true,
      symbolSize: 6,
      areaStyle: { color: '#edf2ff' },
      data: weekDays.value.map((d) =>
        study.state.events.filter((e) => e.date === d).reduce((n, e) => n + e.minutes, 0),
      ),
    },
  ],
}));
const radarOption = computed(() => ({
  color: ['#7795e7'],
  radar: {
    indicator: ['数学', '英语', '政治', '专业课'].map((s) => ({ name: s, max: 100 })),
    axisName: { color: '#8b99b1', fontSize: 11 },
    splitLine: { lineStyle: { color: '#e7edf7' } },
    splitArea: { areaStyle: { color: ['#fff', '#f8faff'] } },
    radius: '65%',
  },
  series: [
    {
      type: 'radar',
      data: [
        {
          value: ['数学', '英语', '政治', '专业课'].map((s) => {
            const ids = study.state.attempts.filter((a) => {
              return (
                a.questionId.startsWith('q') &&
                (s === '数学'
                  ? Number(a.questionId.slice(1)) <= 3
                  : s === '英语'
                    ? Number(a.questionId.slice(1)) >= 4 && Number(a.questionId.slice(1)) <= 6
                    : s === '政治'
                      ? Number(a.questionId.slice(1)) >= 7 && Number(a.questionId.slice(1)) <= 9
                      : Number(a.questionId.slice(1)) >= 10)
              );
            });
            return ids.length
              ? Math.round((ids.filter((a) => a.correct).length / ids.length) * 100)
              : 0;
          }),
          areaStyle: { color: '#dfe9fc66' },
        },
      ],
    },
  ],
}));
const weekMinutes = computed(() =>
  study.state.events
    .filter((e) => weekDays.value.includes(e.date))
    .reduce((n, e) => n + e.minutes, 0),
);
async function load() {
  if (app.mode === 'live' && app.user) {
    const data = await run(() => backend.dashboard(app.user!.id));
    if (data !== undefined) liveData.value = JSON.stringify(data, null, 2);
  }
}
onMounted(load);
</script>
<template>
  <template v-if="app.mode === 'demo'">
    <section class="hero">
      <div>
        <div class="hero-kicker">KEEP GROWING, EVERY DAY</div>
        <h2>{{ app.displayName }}，今天也一起向前吧。</h2>
        <p>不必一次走很远，先完成今天的一小步。</p>
        <RouterLink to="/planning" class="btn"
          >查看我的学习计划<AppIcon name="arrow" :size="16"
        /></RouterLink>
      </div>
      <div class="hero-art"><AppIcon name="book" /></div>
    </section>
    <div class="grid four">
      <div class="card metric">
        <div>
          <p class="metric-label">累计学习时长</p>
          <p class="metric-value">{{ (study.totalMinutes / 60).toFixed(1) }}<small>小时</small></p>
          <p class="muted small">来自已完成课程与专注计时</p>
        </div>
        <span class="metric-icon"><AppIcon name="clock" /></span>
      </div>
      <div class="card metric">
        <div>
          <p class="metric-label">练习正确率</p>
          <p class="metric-value">{{ study.correctRate }}<small>%</small></p>
          <p class="muted small">累计 {{ study.state.attempts.length }} 次作答</p>
        </div>
        <span class="metric-icon green"><AppIcon name="target" /></span>
      </div>
      <div class="card metric">
        <div>
          <p class="metric-label">累计打卡</p>
          <p class="metric-value">{{ study.state.checkins.length }}<small>天</small></p>
          <p class="muted small">
            {{ study.checkedIn ? '今日已打卡，继续保持' : '今天的努力还待记录' }}
          </p>
        </div>
        <span class="metric-icon orange"><AppIcon name="calendar" /></span>
      </div>
      <div class="card metric">
        <div>
          <p class="metric-label">专注学习占比</p>
          <p class="metric-value">{{ focusRate }}<small>%</small></p>
          <p class="muted small">{{ study.wrongIds.length }} 道错题待复习</p>
        </div>
        <span class="metric-icon purple"><AppIcon name="trend" /></span>
      </div>
    </div>
    <div class="split section-gap">
      <div class="stack">
        <section class="card">
          <div class="card-heading">
            <h2>学习时长趋势</h2>
            <span class="tag">近 7 天</span>
          </div>
          <ChartPanel
            :option="trendOption"
            :summary="`近7天共学习${weekMinutes}分钟。统计仅来自本浏览器演示行为。`"
          />
        </section>
        <section class="card">
          <div class="card-heading">
            <h2>今日学习任务</h2>
            <RouterLink to="/planning" class="text-btn"
              >全部计划<AppIcon name="chevron" :size="14"
            /></RouterLink>
          </div>
          <div v-if="!tasks.length" class="empty-hint">
            还没有今天的任务。<br />先生成并确认一份学习计划吧。
          </div>
          <div v-for="task in tasks" :key="task.id" class="task-row" :class="{ done: task.done }">
            <input
              v-model="task.done"
              class="task-check"
              type="checkbox"
              :aria-label="`完成${task.title}`"
            />
            <div class="task-info">
              <h3>{{ task.title }}</h3>
              <p>{{ task.subject }} · {{ task.minutes }} 分钟</p>
            </div>
            <span class="tag" :class="task.done ? 'green' : ''">{{
              task.done ? '已完成' : '待完成'
            }}</span>
          </div>
        </section>
      </div>
      <div class="stack">
        <section class="card">
          <div class="card-heading">
            <h2>你的陪伴伙伴</h2>
            <span class="tag green">今天也在</span>
          </div>
          <AvatarGuide compact />
          <p class="muted small space-top">
            “先把注意力放到眼前的一道题上，进步就在每一次认真里。”
          </p>
          <RouterLink to="/companion" class="btn secondary stretch space-top"
            >和研小途聊一聊<AppIcon name="arrow" :size="15"
          /></RouterLink>
        </section>
        <section class="card">
          <div class="card-heading">
            <h2>知识掌握概览</h2>
            <span class="muted small">练习正确率</span>
          </div>
          <ChartPanel
            :option="radarOption"
            :summary="`四科累计学习分钟数：数学${subjectMinutes[0]}、英语${subjectMinutes[1]}、政治${subjectMinutes[2]}、专业课${subjectMinutes[3]}。雷达表示练习正确率，无样本显示0，不代表实际能力。`"
          />
        </section>
      </div>
    </div>
    <div class="grid two section-gap">
      <section class="card">
        <div class="card-heading">
          <h2>学习快捷入口</h2>
          <span class="muted small">从这里开始</span>
        </div>
        <div class="grid two">
          <RouterLink
            v-for="item in [
              { path: '/qa', icon: 'chat', title: '问懂一道题', text: 'AI 引导式答疑' },
              { path: '/practice', icon: 'pen', title: '每天练一点', text: '刷题与错题巩固' },
              { path: '/exams', icon: 'exam', title: '检验学习成果', text: '限时模考与复盘' },
              { path: '/schools', icon: 'school', title: '找到备考目标', text: '院校筛选与对比' },
            ]"
            :key="item.path"
            :to="item.path"
            class="quick-link"
            ><span class="module-icon"><AppIcon :name="item.icon" :size="17" /></span>
            <div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.text }}</p>
            </div>
            <AppIcon name="chevron" :size="15"
          /></RouterLink>
        </div>
      </section>
      <section class="card">
        <div class="card-heading">
          <h2>本周学习报告</h2>
          <button class="text-btn" @click="reportOpen = !reportOpen">
            {{ reportOpen ? '收起报告' : '查看报告' }}
          </button>
        </div>
        <p class="muted small">
          本周累计学习 <strong>{{ weekMinutes }}</strong> 分钟。以实际记录为起点，制定下一周的节奏。
        </p>
        <div v-if="reportOpen" class="week-report space-top">
          学习记录：{{
            study.state.events.filter((e) => weekDays.includes(e.date)).length
          }}
          次。<br />累计作答正确率：{{ study.correctRate }}%（包含重复练习）。<br />建议：{{
            study.wrongIds.length
              ? '优先复习错题对应知识点，理解错误原因后再练同类题。'
              : '先建立稳定的学习记录，再结合练习反馈调整计划。'
          }}<br /><span class="muted small"
            >此报告为本地规则总结；真实周报、排名和估分待后端接入。</span
          >
        </div>
      </section>
    </div>
  </template>
  <template v-else
    ><section class="card">
      <div class="card-heading">
        <h2>真实学习数据</h2>
        <button class="btn secondary" :disabled="status === 'loading'" @click="load">
          刷新数据
        </button>
      </div>
      <AsyncState :status="status" :error="error" @retry="load" />
      <div v-if="liveData" class="notice">
        当前统计 DTO 尚未定义，以下展示接口原始数据供联调核验，不用于计算学习指标。
      </div>
      <pre v-if="liveData" class="reading">{{ liveData }}</pre>
    </section></template
  >
</template>

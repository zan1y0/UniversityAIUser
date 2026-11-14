<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { Volume2 } from 'lucide-vue-next';
import {
  previewCard,
  missingStudyEndpoint,
  shanghaiInput,
  studyModes,
  studySupervision,
  supervisionPreview,
  type StudyAction,
  type StudyMode,
  type StudySupervisionState,
  type TaskAction,
} from '../api/study-supervision';

const props = defineProps<{ demo: boolean; settings: boolean; open: boolean }>();
const emit = defineEmits<{
  status: [mode: StudyMode, count: number];
  speak: [text: string];
  motion: [mode: StudyMode];
  showTasks: [];
}>();
const demoKey = 'kaoyan-demo-supervision-mode';
const mode = ref<StudyMode>('GENTLE');
const modeKnown = ref(false);
const state = ref<StudySupervisionState>();
const pending = ref(false);
const loadError = ref('');
const actionError = ref('');
const preview = ref(false);
const previewNote = ref('');
const manage = ref(false);
const day = ref(shanghaiInput().slice(0, 10));
const clock = ref(Date.now());
const schedule = ref<{ id: string; title: string; at: string }>();
const hiddenFeedback = ref('');
const cancel = new AbortController();
let revision = 0;
let refreshing = false;
let heartbeat: ReturnType<typeof setInterval>;
let ticker: ReturnType<typeof setInterval>;

const labels: Record<StudyAction, string> = {
  START_RECOMMENDED: studyModes.SCHOLAR.primary,
  START_FIVE: studyModes.GENTLE.primary,
  START_TIMER: studyModes.STRICT.primary,
};
const modes = Object.keys(studyModes) as StudyMode[];
const sample = computed(() => previewCard(mode.value));
const count = computed(() => state.value?.reminders.length ?? 0);
watch(
  [mode, count],
  ([selected, count], previous) => {
    emit('status', selected, count);
    if (count > (previous?.[1] ?? 0)) emit('motion', selected);
  },
  { immediate: true },
);
watch(day, () => void refresh());
watch(
  () => props.open,
  (open) => {
    if (open) void refresh();
  },
);

async function refresh(force = false) {
  if (props.demo || cancel.signal.aborted || (!force && (refreshing || pending.value))) return;
  refreshing = true;
  const version = ++revision;
  const date = day.value;
  try {
    const result = await studySupervision.state(date, cancel.signal);
    if (version !== revision || cancel.signal.aborted) return;
    if (date !== day.value) {
      void refresh(true);
      return;
    }
    state.value = result;
    mode.value = result.mode;
    modeKnown.value = true;
    loadError.value = '';
  } catch (error) {
    if (version !== revision || cancel.signal.aborted) return;
    loadError.value = missingStudyEndpoint(error)
      ? '当前后端版本支持切换模式；任务提醒和专注计时需更新后端后使用。'
      : '暂时无法加载任务和计时数据，仍可切换模式。';
    if (!modeKnown.value) {
      try {
        const saved = await studySupervision.mode(cancel.signal);
        if (version === revision && !cancel.signal.aborted) {
          mode.value = saved;
          modeKnown.value = true;
        }
      } catch {
        /* 用户仍可尝试保存模式。 */
      }
    }
  } finally {
    if (version === revision) refreshing = false;
  }
}

function invalidate() {
  revision++;
  refreshing = false;
}
async function choose(selected: StudyMode) {
  if (pending.value || (selected === mode.value && (props.demo || modeKnown.value))) return;
  pending.value = true;
  invalidate();
  actionError.value = '';
  previewNote.value = '';
  try {
    if (props.demo) {
      try {
        localStorage.setItem(demoKey, selected);
      } catch {
        /* 演示仍可使用。 */
      }
      mode.value = selected;
    } else {
      mode.value = (await studySupervision.save(selected, cancel.signal)).mode;
      modeKnown.value = true;
    }
    emit('motion', selected);
    void refresh(true);
  } catch {
    if (!cancel.signal.aborted) actionError.value = '模式保存失败，请再次选择重试。';
  } finally {
    pending.value = false;
  }
}
async function act(id: string, action: TaskAction) {
  if (pending.value) return;
  pending.value = true;
  invalidate();
  actionError.value = '';
  try {
    await studySupervision.act(id, action, cancel.signal);
    if (action.action === 'SCHEDULE') schedule.value = undefined;
    if (action.action === 'COMPLETE') hiddenFeedback.value = '';
    await refresh(true);
  } catch (error) {
    if (!cancel.signal.aborted)
      actionError.value = error instanceof Error ? error.message : '操作失败，请重试。';
    await refresh(true);
  } finally {
    pending.value = false;
  }
}
function edit(id: string, title: string, at: string | null) {
  schedule.value = { id, title, at: at ? shanghaiInput(at) : day.value + 'T19:00' };
}
function saveSchedule() {
  const form = schedule.value;
  if (!form?.at) return;
  void act(form.id, { action: 'SCHEDULE', scheduledAt: form.at + ':00+08:00' });
}
function showPreview() {
  preview.value = !preview.value;
  previewNote.value = '';
  if (preview.value) emit('motion', mode.value);
}
function demoAction(action: string) {
  previewNote.value = action;
  emit('motion', mode.value);
}
function remaining(end: string) {
  return Math.max(0, Math.ceil((Date.parse(end) - clock.value) / 1000));
}
function countdown(end: string) {
  const seconds = remaining(end);
  return Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0');
}
onMounted(() => {
  if (props.demo) {
    try {
      const saved = localStorage.getItem(demoKey);
      if (modes.includes(saved as StudyMode)) mode.value = saved as StudyMode;
    } catch {
      /* 演示仍可使用。 */
    }
  } else {
    void refresh();
    heartbeat = setInterval(() => void refresh(), 15_000);
    ticker = setInterval(() => {
      clock.value = Date.now();
    }, 500);
  }
});
onBeforeUnmount(() => {
  cancel.abort();
  clearInterval(heartbeat);
  clearInterval(ticker);
});
</script>

<template>
  <div class="supervision" :data-mode="mode">
    <section v-if="settings" class="study-card" aria-label="督学设置">
      <div class="study-head">
        <strong>模式切换</strong><span class="study-badge">{{ studyModes[mode].label }}</span>
      </div>
      <div class="mode-options" role="group" aria-label="模式切换">
        <button
          v-for="option in modes"
          :key="option"
          type="button"
          :aria-pressed="mode === option"
          :disabled="pending"
          @click="choose(option)"
        >
          {{ studyModes[option].label }}
        </button>
      </div>
      <p>{{ studyModes[mode].description }}</p>
      <div class="study-actions">
        <button type="button" :aria-expanded="preview" @click="showPreview">预览督学反馈</button>
        <button v-if="!demo" type="button" :aria-expanded="manage" @click="manage = !manage">
          管理任务时间
        </button>
      </div>
    </section>
    <p v-if="actionError" class="study-error" role="alert">{{ actionError }}</p>
    <div v-if="loadError && settings" class="study-card study-error" role="status">
      {{ loadError }} <button type="button" @click="refresh()">重试加载</button>
    </div>
    <section v-if="settings && preview" class="study-card" aria-label="答辩预览">
      <div class="study-head">
        <strong>答辩预览 · 模拟数据</strong
        ><span class="study-badge">{{ studyModes[mode].label }}</span>
      </div>
      <p>{{ sample.title }} · 计划 10:00 · 现在 10:10 · 尚未开始</p>
      <p v-if="mode === 'SCHOLAR'">优先级：高 · 预计 {{ sample.minutes }} 分钟</p>
      <p>{{ sample.text }}</p>
      <div class="study-actions">
        <button
          class="primary"
          type="button"
          @click="demoAction(mode === 'GENTLE' ? '已演示启动 5 分钟专注。' : '已演示开始任务。')"
        >
          {{ labels[sample.action] }}
        </button>
        <button v-if="mode === 'STRICT'" type="button" @click="demoAction('已演示打开调整时间。')">
          调整时间
        </button>
        <button type="button" @click="emit('speak', sample.text)">
          <Volume2 :size="15" />朗读提醒
        </button>
      </div>
      <p v-if="previewNote" role="status">{{ previewNote }} 预览不会修改正式数据。</p>
      <strong>完成反馈</strong>
      <p>{{ supervisionPreview[mode].feedback }}</p>
      <div class="study-actions">
        <button v-if="mode === 'GENTLE'" type="button" @click="demoAction('已演示继续学习。')">
          继续学习
        </button>
        <button v-if="mode === 'GENTLE'" type="button" @click="demoAction('已演示休息。')">
          先休息
        </button>
        <button type="button" @click="emit('speak', supervisionPreview[mode].feedback)">
          <Volume2 :size="15" />朗读反馈
        </button>
      </div>
    </section>

    <section v-if="settings && manage && !demo" class="study-card" aria-label="管理任务时间">
      <label>计划日期 <input v-model="day" type="date" /></label>
      <p v-if="!state?.tasks.length">
        这一天没有待办任务。可在「学习计划」确认计划，再为任务设置开始时间。
      </p>
      <ul v-else>
        <li v-for="task in state.tasks" :key="task.id">
          <strong>{{ task.title }}</strong>
          <span>{{
            task.started
              ? '已开始'
              : task.scheduledAt
                ? shanghaiInput(task.scheduledAt).replace('T', ' ')
                : '尚未设置开始时间'
          }}</span>
          <div v-if="!task.started" class="study-actions">
            <button
              type="button"
              :disabled="pending"
              @click="edit(task.id, task.title, task.scheduledAt)"
            >
              {{ task.scheduledAt ? '调整时间' : '设置开始时间' }}
            </button>
            <button type="button" :disabled="pending" @click="act(task.id, { action: 'START' })">
              {{ studyModes[mode].primary }}
            </button>
          </div>
        </li>
      </ul>
    </section>

    <section v-if="schedule" class="study-card" aria-label="调整任务时间">
      <strong>{{ schedule.title }}</strong>
      <label>计划开始时间（北京时间） <input v-model="schedule.at" type="datetime-local" /></label>
      <div class="study-actions">
        <button
          class="primary"
          type="button"
          :disabled="pending || !schedule.at"
          @click="saveSchedule"
        >
          保存时间
        </button>
        <button type="button" :disabled="pending" @click="schedule = undefined">取消</button>
      </div>
    </section>

    <section
      v-for="card in state?.reminders"
      :key="card.sourceTaskId + card.scheduledAt"
      class="study-card"
      aria-label="正式督学提醒"
    >
      <div class="study-head">
        <strong>{{ card.title }}</strong
        ><span class="study-badge">{{ studyModes[mode].label }}</span>
      </div>
      <p v-if="mode === 'STRICT'">
        计划 {{ card.scheduledLabel }} · 尚未开始 · 第 {{ card.round }} 次提醒
      </p>
      <p v-if="mode === 'SCHOLAR' && (card.priority != null || card.minutes)">
        <span v-if="card.priority != null">优先级 {{ card.priority }}　</span
        ><span v-if="card.minutes">预计 {{ card.minutes }} 分钟</span>
      </p>
      <p>{{ card.text }}</p>
      <div class="study-actions">
        <button
          class="primary"
          type="button"
          :disabled="pending"
          @click="act(card.taskId, { action: 'START' })"
        >
          {{ labels[card.action] }}
        </button>
        <button
          v-if="mode === 'STRICT'"
          type="button"
          :disabled="pending"
          @click="edit(card.sourceTaskId, card.title, card.scheduledAt)"
        >
          调整时间
        </button>
        <button type="button" @click="emit('speak', card.text)">
          <Volume2 :size="15" />朗读提醒
        </button>
        <button
          type="button"
          :disabled="pending"
          @click="
            act(card.sourceTaskId, {
              action: 'DISMISS',
              scheduledAt: card.scheduledAt,
              round: card.round,
            })
          "
        >
          知道了
        </button>
      </div>
    </section>

    <section
      v-for="timer in state?.timers"
      :key="timer.id"
      class="study-card"
      aria-label="任务专注计时"
    >
      <div class="study-head">
        <strong>{{ timer.title }}</strong
        ><span class="study-badge">专注学习</span>
      </div>
      <p v-if="timer.focusEnd && remaining(timer.focusEnd)" role="timer">
        剩余 {{ countdown(timer.focusEnd) }}
      </p>
      <p v-else-if="timer.focusEnd">本次计时已结束。可以继续学习，或结束本次专注。</p>
      <p v-else>本次专注已结束，任务仍待完成。</p>
      <div class="study-actions">
        <button
          v-if="!timer.focusEnd || !remaining(timer.focusEnd)"
          class="primary"
          type="button"
          :disabled="pending"
          @click="act(timer.id, { action: 'CONTINUE' })"
        >
          继续学习
        </button>
        <button
          v-if="timer.focusEnd"
          type="button"
          :disabled="pending"
          @click="act(timer.id, { action: 'STOP' })"
        >
          结束本次专注
        </button>
        <button type="button" :disabled="pending" @click="act(timer.id, { action: 'COMPLETE' })">
          标记任务完成
        </button>
      </div>
    </section>

    <section
      v-if="state?.feedback && state.feedback.id !== hiddenFeedback"
      class="study-card"
      aria-label="学习完成反馈"
    >
      <div class="study-head">
        <strong>学习完成反馈</strong><span class="study-badge">{{ studyModes[mode].label }}</span>
      </div>
      <p>{{ state.feedback.text }}</p>
      <div class="study-actions">
        <button
          v-if="mode === 'GENTLE'"
          type="button"
          @click="
            manage = true;
            hiddenFeedback = state.feedback.id;
            emit('showTasks');
          "
        >
          继续学习
        </button>
        <button type="button" @click="hiddenFeedback = state.feedback.id">
          {{ mode === 'GENTLE' ? '先休息' : '收起' }}
        </button>
        <button type="button" @click="emit('speak', state.feedback.text)">
          <Volume2 :size="15" />朗读反馈
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.supervision {
  display: grid;
  gap: 10px;
}
.supervision:empty {
  display: none;
}
.supervision {
  --accent: #31634f;
  --tint: #e5f2e9;
}
.supervision[data-mode='SCHOLAR'] {
  --accent: #355e82;
  --tint: #e7eff8;
}
.supervision[data-mode='STRICT'] {
  --accent: #785321;
  --tint: #f7eddb;
}
.study-card {
  display: grid;
  gap: 8px;
  padding: 12px;
  background: #f9fbfa;
  border: 1px solid #dce8e3;
  border-radius: 14px;
}
.study-card p {
  margin: 0;
  overflow-wrap: anywhere;
}
.study-head,
.study-actions,
.mode-options {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.study-head {
  justify-content: space-between;
}
.study-badge,
.study-card button[aria-pressed='true'],
.study-card button.primary {
  background: var(--tint);
  color: var(--accent);
  font-weight: 700;
}
.study-badge {
  padding: 3px 9px;
  border-radius: 12px;
  font-size: 12px;
}
.study-card button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  min-height: 32px;
  border: 1px solid #cbdad4;
  border-radius: 8px;
  background: white;
  color: #233c46;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.study-card button:disabled {
  opacity: 0.55;
  cursor: default;
}
.study-card label,
.study-card li {
  display: grid;
  gap: 5px;
}
.study-card ul {
  margin: 0;
  padding: 0;
  list-style: none;
}
.study-card li {
  padding: 7px 0;
  border-top: 1px solid #dce8e3;
}
.study-card input {
  max-width: 100%;
  padding: 4px 6px;
}
.study-error {
  margin: 0;
  color: #8a3d34;
}
</style>

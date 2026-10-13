import { defineStore } from 'pinia';
import { computed, ref, watch } from 'vue';
import type { Attempt, ExamSession, LearningEvent, Plan, Subject } from '../domain/types';
import { localDate } from '../domain/logic';
import { isRecord } from '../api/client';

interface DemoState {
  version: 1;
  checkins: string[];
  completedUnits: string[];
  attempts: Attempt[];
  plan: Plan | null;
  exam: ExamSession | null;
  events: LearningEvent[];
  followed: string[];
  reminders: boolean;
}
const defaults = (): DemoState => ({
  version: 1,
  checkins: [],
  completedUnits: [],
  attempts: [],
  plan: null,
  exam: null,
  events: [],
  followed: [],
  reminders: true,
});
export function parseDemoState(raw: string | null): DemoState {
  try {
    if (!raw || raw.length > 2_000_000) return defaults();
    const data: unknown = JSON.parse(raw);
    if (!isRecord(data) || data.version !== 1) return defaults();
    const v = data as Partial<DemoState>;
    if (
      !Array.isArray(v.checkins) ||
      !v.checkins.every((x) => typeof x === 'string') ||
      !Array.isArray(v.completedUnits) ||
      !v.completedUnits.every((x) => typeof x === 'string') ||
      !Array.isArray(v.attempts) ||
      !v.attempts.every(
        (a) =>
          isRecord(a) &&
          typeof a.questionId === 'string' &&
          Number.isInteger(a.selected) &&
          typeof a.correct === 'boolean' &&
          typeof a.time === 'string' &&
          ['practice', 'exam'].includes(String(a.kind)),
      ) ||
      !Array.isArray(v.events) ||
      !v.events.every(
        (e) =>
          isRecord(e) &&
          ['数学', '英语', '政治', '专业课'].includes(String(e.subject)) &&
          typeof e.minutes === 'number' &&
          Number.isFinite(e.minutes) &&
          e.minutes >= 0 &&
          typeof e.date === 'string' &&
          typeof e.focused === 'boolean',
      ) ||
      !Array.isArray(v.followed) ||
      !v.followed.every((x) => typeof x === 'string') ||
      typeof v.reminders !== 'boolean'
    )
      return defaults();
    // 用户可清空演示数据；只持久化本地示例学习行为，不存密码、会话凭据、对话或附件。
    if (
      v.plan &&
      (!isRecord(v.plan) ||
        typeof v.plan.id !== 'string' ||
        !Number.isInteger(v.plan.version) ||
        typeof v.plan.cycle !== 'string' ||
        typeof v.plan.target !== 'string' ||
        !Array.isArray(v.plan.tasks) ||
        v.plan.tasks.some(
          (t) =>
            !isRecord(t) ||
            typeof t.id !== 'string' ||
            typeof t.done !== 'boolean' ||
            typeof t.title !== 'string' ||
            typeof t.date !== 'string' ||
            typeof t.minutes !== 'number' ||
            !Number.isFinite(t.minutes) ||
            t.minutes <= 0 ||
            !['数学', '英语', '政治', '专业课'].includes(String(t.subject)),
        ))
    )
      return defaults();
    if (
      v.exam &&
      (!isRecord(v.exam) ||
        !Array.isArray(v.exam.questionIds) ||
        !v.exam.questionIds.length ||
        !v.exam.questionIds.every((x) => typeof x === 'string') ||
        typeof v.exam.startedAt !== 'number' ||
        !Number.isFinite(v.exam.startedAt) ||
        typeof v.exam.durationSeconds !== 'number' ||
        !Number.isFinite(v.exam.durationSeconds) ||
        v.exam.durationSeconds <= 0 ||
        !isRecord(v.exam.answers) ||
        !Object.values(v.exam.answers).every(
          (x) => typeof x === 'number' && Number.isInteger(x) && x >= 0 && x < 10,
        ) ||
        (v.exam.submittedAt !== undefined &&
          (typeof v.exam.submittedAt !== 'number' || !Number.isFinite(v.exam.submittedAt))))
    )
      return defaults();
    return { ...defaults(), ...v } as DemoState;
  } catch {
    return defaults();
  }
}
function restore(): DemoState {
  try {
    return parseDemoState(localStorage.getItem('kaoyan-demo-v1'));
  } catch {
    return defaults();
  }
}
export const useStudyStore = defineStore('study', () => {
  const state = ref<DemoState>(restore());
  const storageError = ref(false);
  watch(
    state,
    (value) => {
      try {
        localStorage.setItem('kaoyan-demo-v1', JSON.stringify(value));
        storageError.value = false;
      } catch {
        storageError.value = true;
      }
    },
    { deep: true },
  );
  const today = computed(() => localDate());
  const checkedIn = computed(() => state.value.checkins.includes(today.value));
  const wrongIds = computed(() => [
    ...new Set(state.value.attempts.filter((a) => !a.correct).map((a) => a.questionId)),
  ]);
  const correctRate = computed(() =>
    state.value.attempts.length
      ? Math.round(
          (state.value.attempts.filter((a) => a.correct).length / state.value.attempts.length) *
            100,
        )
      : 0,
  );
  const totalMinutes = computed(() => state.value.events.reduce((n, e) => n + e.minutes, 0));
  function checkin() {
    const date = localDate();
    if (!state.value.checkins.includes(date)) state.value.checkins.push(date);
  }
  function completeUnit(id: string, subject: Subject, minutes: number) {
    if (!state.value.completedUnits.includes(id)) {
      state.value.completedUnits.push(id);
      state.value.events.push({ subject, minutes, date: localDate(), focused: false });
    }
  }
  function reset() {
    state.value = defaults();
  }
  return {
    state,
    today,
    checkedIn,
    wrongIds,
    correctRate,
    totalMinutes,
    storageError,
    checkin,
    completeUnit,
    reset,
  };
});

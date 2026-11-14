import { execute } from './contract';
import { ApiError } from './client';
import type {
  StudyMode,
  StudyReminderCard,
  StudyReminderAction,
  StudySupervisionTaskAction,
} from './generated';
export type { StudyMode, StudySupervisionState } from './generated';
export type StudyAction = StudyReminderAction;
export type TaskAction = StudySupervisionTaskAction;

export const studyModes = {
  SCHOLAR: {
    label: '学霸模式',
    primary: '开始推荐任务',
    description: '晚 5 分钟提醒，给出清晰的下一步。',
  },
  GENTLE: {
    label: '温柔模式',
    primary: '先学 5 分钟',
    description: '晚 10 分钟提醒，从一个小步骤开始。',
  },
  STRICT: {
    label: '严管模式',
    primary: '立即开始计时',
    description: '到点提醒，5 分钟后可追加提醒。',
  },
} as const;

export const studySupervision = {
  async mode(signal: AbortSignal) {
    try {
      return (await execute('op097', {}, { signal })).mode;
    } catch (error) {
      if (!canTryLegacySettings(error)) throw error;
      const preferences = await execute('op012', {}, { signal });
      return preferences.mode.toUpperCase() as StudyMode;
    }
  },
  state: (date: string, signal: AbortSignal) => execute('op099', { query: { date } }, { signal }),
  async save(mode: StudyMode, signal: AbortSignal) {
    try {
      return await execute('op098', { body: { mode } }, { signal });
    } catch (error) {
      if (!canTryLegacySettings(error)) throw error;
      const preferences = await execute('op012', {}, { signal });
      const legacyMode = mode.toLowerCase() as typeof preferences.mode;
      if (preferences.mode === legacyMode) return { mode };
      const updated = await execute(
        'op013',
        {
          body: { ...preferences, mode: legacyMode },
        },
        { signal },
      );
      return { mode: updated.mode.toUpperCase() as StudyMode };
    }
  },
  act: (taskId: string, body: TaskAction, signal: AbortSignal) =>
    execute('op100', { path: { taskId }, body }, { signal }),
};

export function missingStudyEndpoint(error: unknown) {
  return error instanceof ApiError && (error.status === 404 || error.status === 405);
}

function canTryLegacySettings(error: unknown) {
  // Older servers route unknown endpoints through their generic error handler.
  return missingStudyEndpoint(error) ||
    (error instanceof ApiError && error.status === 500 && error.code === 'INTERNAL_ERROR');
}

export function shanghaiInput(at: string | number = Date.now()) {
  const millis = typeof at === 'string' ? Date.parse(at) : at;
  return new Date(millis + 8 * 3600_000).toISOString().slice(0, 16);
}

const sampleTitle = '英语长难句';
export const supervisionPreview = {
  SCHOLAR: {
    action: 'START_RECOMMENDED',
    text: '接下来学「英语长难句」。先确定一个可验收的小目标。',
    feedback: '已完成「英语长难句」。实际学习 20 分钟。',
  },
  GENTLE: {
    action: 'START_FIVE',
    text: '「英语长难句」可以从一小步开始：先读一句，找出主谓宾。先试 5 分钟。',
    feedback: '你完成了「英语长难句」，又推进了一项学习计划。可以继续学一小步，也可以先休息。',
  },
  STRICT: {
    action: 'START_TIMER',
    text: '「英语长难句」计划 10:00 开始，当前尚未开始。请立即计时或调整时间。',
    feedback:
      '「英语长难句」已完成。剩余 2 项任务。待办：数学练习、政治知识点复习。最近计划时间：11:00。',
  },
} as const;
export function previewCard(mode: StudyMode): StudyReminderCard {
  const sample = supervisionPreview[mode];
  return {
    taskId: 'preview',
    sourceTaskId: 'preview',
    title: sampleTitle,
    scheduledAt: '',
    scheduledLabel: '10:00',
    round: 1,
    action: sample.action,
    text: sample.text,
    priority: 2,
    minutes: 20,
  };
}

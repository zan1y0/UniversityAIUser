import { describe, expect, it } from 'vitest';
import { createTasks, gradeQuestions, remainingSeconds } from '../../src/domain/logic';
import { unwrapResult } from '../../src/api/client';
import { questions } from '../../src/data/demo';
import type { ExamSession } from '../../src/domain/types';
import { parseDemoState } from '../../src/stores/study';

describe('模考截止与评分规则', () => {
  const session: ExamSession = {
    id: 'demo',
    questionIds: ['q1', 'q2'],
    answers: { q1: 1 },
    startedAt: 1000,
    durationSeconds: 60,
  };
  it('以绝对截止时间计算，切换页面不延长考试', () => {
    expect(remainingSeconds(session, 31000)).toBe(30);
    expect(remainingSeconds(session, 80000)).toBe(0);
  });
  it('交卷后不可继续计时', () => {
    expect(remainingSeconds({ ...session, submittedAt: 32000 }, 33000)).toBe(0);
  });
  it('遗漏题不计为正确，选项0被正确识别', () => {
    expect(
      gradeQuestions(
        questions.filter((q) => q.id === 'q8'),
        { q8: 0 },
      ),
    ).toEqual({ correct: 1, total: 1, score: 100 });
    expect(gradeQuestions(questions, {}).score).toBe(0);
  });
});
describe('学习计划日历与容量', () => {
  it('周计划跨月保持当地日期，每天四科', () => {
    const tasks = createTasks('周计划', 4, '2026-10-29');
    expect(tasks).toHaveLength(28);
    expect(tasks[0]?.date).toBe('2026-10-29');
    expect(tasks.at(-1)?.date).toBe('2026-11-04');
    expect(tasks.slice(0, 4).reduce((n, t) => n + t.minutes, 0)).toBe(240);
    expect(new Set(tasks.map((t) => t.id)).size).toBe(28);
  });
  it('月计划生成30天', () => {
    expect(createTasks('月计划', 2, '2026-10-01')).toHaveLength(120);
  });
});
describe('后端 Result 边界', () => {
  it('拒绝尚未实现接口返回的空成功结果', () => {
    expect(() => unwrapResult({ code: 200, message: 'success', data: null })).toThrow(
      '后端尚未返回业务数据',
    );
  });
  it('业务错误不能作为成功读取', () => {
    expect(() => unwrapResult({ code: 400, message: '参数错误', data: null })).toThrow('参数错误');
  });
  it('兼容当前后端和设计契约', () => {
    expect(unwrapResult({ code: 200, data: '文本' })).toBe('文本');
    expect(unwrapResult({ code: 'OK', data: [] })).toEqual([]);
  });
});
describe('演示缓存恢复', () => {
  it('损坏 JSON 与非法嵌套条目都回退为空状态，不导致页面崩溃', () => {
    expect(parseDemoState('{bad').events).toEqual([]);
    const base = parseDemoState(null);
    expect(parseDemoState(JSON.stringify({ ...base, events: [null] })).events).toEqual([]);
    expect(
      parseDemoState(
        JSON.stringify({
          ...base,
          exam: { id: 'e', questionIds: ['q1'], answers: {}, startedAt: 1, durationSeconds: -1 },
        }),
      ).exam,
    ).toBeNull();
  });
});

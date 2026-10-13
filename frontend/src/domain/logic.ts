import type { ExamSession, PlanTask, Question, Subject } from './types';

export function localDate(date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
export function remainingSeconds(session: ExamSession, now = Date.now()): number {
  return session.submittedAt
    ? 0
    : Math.max(0, Math.ceil((session.startedAt + session.durationSeconds * 1000 - now) / 1000));
}
export function gradeQuestions(questions: Question[], answers: Record<string, number>) {
  const correct = questions.filter((q) => answers[q.id] === q.answer).length;
  return {
    correct,
    total: questions.length,
    score: questions.length ? Math.round((correct / questions.length) * 100) : 0,
  };
}
export function createTasks(cycle: string, hours: number, start: string): PlanTask[] {
  const days = cycle === '日计划' ? 1 : cycle === '周计划' ? 7 : 30;
  const subjects: Subject[] = ['数学', '英语', '政治', '专业课'];
  const titles = [
    '专题练习与错题复盘',
    '词汇记忆与长难句',
    '知识框架与考点回顾',
    '参考书与真题考点',
  ];
  return Array.from({ length: days * 4 }, (_, i) => {
    const date = new Date(`${start}T12:00:00`);
    date.setDate(date.getDate() + Math.floor(i / 4));
    return {
      id: crypto.randomUUID(),
      title: titles[i % 4]!,
      subject: subjects[i % 4]!,
      minutes: Math.round((hours * 60) / 4),
      date: localDate(date),
      done: false,
    };
  });
}

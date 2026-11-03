import { execute, waitJob } from './contract';
import { clearCsrf, ApiError, isRecord, request } from './client';
import type { User, School, Notice } from '../domain/types';

export const backend = {
  async login(loginName: string, password: string): Promise<User> {
    const user = authUser(
      await request('/auth/login', { method: 'POST', body: { loginName, password } }),
    );
    clearCsrf();
    return user;
  },
  async register(loginName: string, password: string, displayName: string): Promise<User> {
    const user = authUser(
      await request('/auth/register', {
        method: 'POST',
        body: { loginName, password, displayName },
      }),
    );
    clearCsrf();
    return user;
  },
  async logout() {
    await request('/auth/logout', { method: 'POST' });
    clearCsrf();
  },
  async currentUser(): Promise<User> {
    return authUser(await request('/auth/me'));
  },
  async chat(
    text: string,
    _context: string,
    signal = new AbortController().signal,
  ): Promise<string> {
    const knowledge = await execute(
      'op025',
      { query: { subject: 'math', examType: 'math1', year: new Date().getFullYear() + 1 } },
      { signal },
    );
    const conversation = await execute(
      'op037',
      { body: { subjectId: knowledge.subjectId } },
      { key: crypto.randomUUID(), signal },
    );
    const key = crypto.randomUUID();
    const accepted = await execute(
      'op039',
      {
        path: { id: conversation.id },
        body: {
          text,
          subjectId: knowledge.subjectId,
          requestId: key,
          contextVersion: conversation.contextVersion,
        },
      },
      { key, signal },
    );
    const result = await waitJob(accepted.jobId, signal);
    if (!result || result.kind !== 'qa' || !result.message.guidance)
      throw new ApiError('答疑结果暂不可用');
    return [
      ...result.message.guidance.steps,
      ...result.message.guidance.hints,
      result.message.guidance.method,
    ].join('\n');
  },
  async schools(major: string, score: number): Promise<School[]> {
    const result = await execute('op019', {
      query: { major, score, year: new Date().getFullYear() + 1 },
    });
    return result.items.map((s) => ({
      id: s.id,
      name: s.schoolName,
      region: s.region,
      major: s.major,
      difficulty: { easy: '保底', medium: '稳妥', hard: '冲刺' }[s.difficulty],
      line: s.scoreLine ?? 0,
      enrollment: s.enrollment ?? 0,
      ratio: s.admissionRatio?.toString() || '暂无资料',
      scoreRange: '请参考历史指标',
      risk: '需要结合本人评估',
      source: s.sources.map((v) => v.publisher).join('、'),
    }));
  },
  async notices(_category: string): Promise<Notice[]> {
    const result = await execute('op075', { query: { year: new Date().getFullYear() + 1 } });
    return result.items.map((n) => ({
      id: n.id,
      category: n.category,
      title: n.title,
      date: n.publishedAt.slice(0, 10),
      source: n.sources.map((v) => v.publisher).join('、'),
      content: n.content,
      important: n.category === 'policy',
    }));
  },
  async questions(_userId: string): Promise<never> {
    throw new ApiError('请在练习页创建练习会话后获取题目。');
  },
  async plan(_userId: string) {
    return execute('op004', {});
  },
  async dashboard(_userId: string) {
    const to = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' });
    const start = new Date();
    start.setDate(start.getDate() - 6);
    return execute('op072', {
      query: {
        from: start.toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' }),
        to,
        timezone: 'Asia/Shanghai',
      },
    });
  },
};

function authUser(value: unknown): User {
  if (!isRecord(value) || typeof value.id !== 'string' || typeof value.displayName !== 'string')
    throw new ApiError('登录响应缺少账号信息', 'INVALID_RESPONSE');
  return { id: value.id, nickname: value.displayName };
}

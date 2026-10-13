import { ApiError, isRecord, request } from './client';
import type { Notice, Question, School, User } from '../domain/types';

export const backend = {
  async login(phone: string, password: string): Promise<User> {
    const data = await request('/user/login', { method: 'POST', body: { phone, password } });
    if (
      !isRecord(data) ||
      (typeof data.id !== 'string' && typeof data.id !== 'number') ||
      typeof data.nickname !== 'string'
    )
      throw new ApiError('登录响应缺少用户 id 或昵称，请补全后端 UserVO。');
    if (typeof data.id === 'number' && !Number.isSafeInteger(data.id))
      throw new ApiError('用户 ID 超出 JavaScript 安全整数范围，请后端将 Long ID 序列化为字符串。');
    return {
      id: String(data.id),
      nickname: data.nickname,
      targetSchool: typeof data.targetSchool === 'string' ? data.targetSchool : undefined,
      targetMajor: typeof data.targetMajor === 'string' ? data.targetMajor : undefined,
    };
  },
  async chat(message: string, sessionId: string, signal?: AbortSignal): Promise<string> {
    const data = await request('/ai-chat/chat', {
      method: 'POST',
      body: { message, sessionId },
      signal,
    });
    if (typeof data !== 'string' || !data.trim()) throw new ApiError('AI 接口没有返回有效文本。');
    return data;
  },
  async schools(major: string, score: number): Promise<School[]> {
    const data = await request(
      `/school/recommend?${new URLSearchParams({ major, score: String(score) })}`,
    );
    return requireArray<School>(data, [
      'id',
      'name',
      'region',
      'major',
      'difficulty',
      'line',
      'enrollment',
      'ratio',
      'scoreRange',
      'risk',
      'source',
    ]);
  },
  async notices(category: string): Promise<Notice[]> {
    const data = await request(
      `/news/list?${new URLSearchParams(category === '全部' ? {} : { category })}`,
    );
    return requireArray<Notice>(data, [
      'id',
      'category',
      'title',
      'date',
      'source',
      'content',
      'important',
    ]);
  },
  async questions(userId: string): Promise<Question[]> {
    const data = await request(`/exam/question?userId=${encodeURIComponent(userId)}`);
    return requireArray<Question>(data, [
      'id',
      'subject',
      'topic',
      'stem',
      'options',
      'answer',
      'explanation',
    ]);
  },
  plan(userId: string) {
    return request(`/study-plan/generate?userId=${encodeURIComponent(userId)}`);
  },
  dashboard(userId: string) {
    return request(`/dashboard/overview?userId=${encodeURIComponent(userId)}`);
  },
};
function requireArray<T>(data: unknown, fields: string[]): T[] {
  const values = Array.isArray(data)
    ? data
    : isRecord(data) && Array.isArray(data.items)
      ? data.items
      : null;
  if (!values || values.some((item) => !isRecord(item) || fields.some((field) => !(field in item))))
    throw new ApiError('后端字段尚未对齐前端契约，详见 frontend/README.md。', 'SCHEMA_MISMATCH');
  // 进一步验证字段类型，避免空架构接口或异常数据进入页面。
  for (const item of values) {
    if (!isRecord(item) || typeof item.id !== 'string')
      throw new ApiError('业务 ID 应为字符串。', 'SCHEMA_MISMATCH');
    for (const field of fields.filter(
      (v) => !['id', 'line', 'enrollment', 'options', 'answer', 'important'].includes(v),
    ))
      if (typeof item[field] !== 'string')
        throw new ApiError(`字段 ${field} 应为字符串。`, 'SCHEMA_MISMATCH');
    for (const field of ['line', 'enrollment', 'answer'].filter((v) => fields.includes(v)))
      if (typeof item[field] !== 'number' || !Number.isFinite(item[field]))
        throw new ApiError(`字段 ${field} 应为有效数值。`, 'SCHEMA_MISMATCH');
    if (fields.includes('important') && typeof item.important !== 'boolean')
      throw new ApiError('important 应为布尔值。', 'SCHEMA_MISMATCH');
    if (
      fields.includes('options') &&
      (!Array.isArray(item.options) ||
        item.options.some((v) => typeof v !== 'string') ||
        !Number.isInteger(item.answer) ||
        Number(item.answer) < 0 ||
        Number(item.answer) >= item.options.length)
    )
      throw new ApiError('题目选项或答案格式错误。', 'SCHEMA_MISMATCH');
  }
  return values as T[];
}

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearCsrf, request, unwrapResult } from '../../src/api/client';
import { execute } from '../../src/api/contract';
import { schemaIssues, schemas, operations } from '../../src/api/schema';
import { sessionValue, saveSessionValue } from '../../src/domain/session-storage';
const uuid = '11111111-1111-4111-8111-111111111111';
const ok = (data: unknown) =>
  new Response(JSON.stringify({ code: 'OK', message: 'ok', traceId: 'trace-test', data }), {
    headers: { 'Content-Type': 'application/json' },
  });
beforeEach(() => clearCsrf());
afterEach(() => {
  vi.unstubAllGlobals();
  clearCsrf();
});
describe('正式接口契约边界', () => {
  it('覆盖81个接口与11个业务模块，禁止旧路径', () => {
    expect(Object.keys(operations)).toHaveLength(81);
    expect(
      new Set(
        Object.values(operations)
          .filter((o) => o.tag.startsWith('M'))
          .map((o) => o.tag),
      ).size,
    ).toBe(11);
    expect(Object.values(operations).every((o) => o.path.startsWith('/api/v1/'))).toBe(true);
  });
  it('未知字段、越界概率与无效日期被拒绝', () => {
    expect(
      schemaIssues(schemas.LoginInput!, {
        loginName: 'student',
        password: '12345678',
        userId: uuid,
      }).length,
    ).toBeGreaterThan(0);
    expect(schemaIssues({ type: 'number', minimum: 0, maximum: 1 }, 1.1).length).toBeGreaterThan(0);
    expect(schemaIssues({ type: 'string', format: 'date' }, '2026-99-99').length).toBeGreaterThan(
      0,
    );
  });
  it('拍照答疑不能混用文字分支，不接受最终答案字段', () => {
    expect(
      schemaIssues(schemas.QaMessageInput!, {
        subjectId: uuid,
        text: '请讲解',
        recognitionId: uuid,
        confirmedText: '已核对题干',
        requestId: uuid,
        contextVersion: 1,
      }).length,
    ).toBeGreaterThan(0);
    expect(
      schemaIssues(schemas.Question!, {
        id: uuid,
        versionId: uuid,
        subjectId: uuid,
        knowledgeId: uuid,
        kind: 'singleChoice',
        stem: '示例题',
        options: [],
        difficulty: 'easy',
        maxScore: 1,
        sources: [],
        correctAnswer: 'A',
      }).length,
    ).toBeGreaterThan(0);
  });
  it('登录先取CSRF，账号字段严格为loginName，Cookie由浏览器携带', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(ok({ token: 'csrf-test', headerName: 'X-CSRF-Token' }))
      .mockResolvedValueOnce(ok({ id: uuid, loginName: 'student', displayName: '同学' }));
    vi.stubGlobal('fetch', fetchMock);
    await execute('op002', { body: { loginName: 'student', password: '12345678' } });
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/session/csrf');
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/session/login');
    const options = fetchMock.mock.calls[1]?.[1] as RequestInit;
    expect(options.credentials).toBe('include');
    expect(options.headers).toMatchObject({ 'X-CSRF-Token': 'csrf-test' });
    expect(JSON.parse(options.body as string)).toEqual({
      loginName: 'student',
      password: '12345678',
    });
  });
  it('读取HTTP错误的业务code、message和traceId，不吞掉版本冲突', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 'VERSION_CONFLICT',
            message: '记录已更新',
            data: null,
            traceId: 'conflict-1',
            details: [],
          }),
          { status: 409 },
        ),
      ),
    );
    await expect(request('/profile')).rejects.toMatchObject({
      code: 'VERSION_CONFLICT',
      traceId: 'conflict-1',
      status: 409,
      message: '记录已更新',
    });
  });
  it('204退出成功，无需读取JSON响应', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(ok({ token: 'csrf', headerName: 'X-CSRF-Token' }))
        .mockResolvedValueOnce(new Response(null, { status: 204 })),
    );
    await expect(execute('op003', {})).resolves.toBeUndefined();
  });
  it('发送幂等键，学习事件标识不会传成userId', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(ok({ token: 'csrf', headerName: 'X-CSRF-Token' }))
      .mockResolvedValueOnce(
        ok({ id: uuid, localDate: '2026-10-13', streakDays: 1, createdAt: '2026-10-13T08:00:00Z' }),
      );
    vi.stubGlobal('fetch', fetchMock);
    await execute('op015', { body: { localDate: '2026-10-13' } }, { key: uuid });
    expect(fetchMock.mock.calls[1]?.[1].headers['Idempotency-Key']).toBe(uuid);
    expect(fetchMock.mock.calls[1]?.[1].body).not.toContain('userId');
  });
  it('已取消请求不会被当作成功或继续提交', async () => {
    const controller = new AbortController();
    controller.abort();
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new DOMException('aborted', 'AbortError')));
    await expect(request('/profile', { signal: controller.signal })).rejects.toMatchObject({
      code: 'CANCELLED',
    });
  });
  it('旧数字成功码与空成功数据不能进入页面', () => {
    expect(() => unwrapResult({ code: 200, data: {} })).toThrow();
    expect(() => unwrapResult({ code: 'OK', message: 'ok', traceId: 'test', data: null })).toThrow(
      '后端尚未返回业务数据',
    );
  });
  it('浏览器禁用sessionStorage仍能进入演示页面', () => {
    vi.stubGlobal('sessionStorage', {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
    });
    expect(sessionValue('mode')).toBeNull();
    expect(() => saveSessionValue('mode', 'demo')).not.toThrow();
  });
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { backend } from '../../src/api/backend';
import { clearCsrf } from '../../src/api/client';
import fixtures from '../fixtures/responses.json';
import results from '../fixtures/job-results.json';

const contextId = '11111111-1111-4111-8111-111111111111';
const ok = (data: unknown, status = 200) =>
  new Response(JSON.stringify({ code: 'OK', message: 'ok', traceId: 'avatar-test', data }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
function mockChat(jobOverrides: Record<string, unknown> = {}) {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(ok({ token: 'csrf-test', headerName: 'X-CSRF-Token' }))
    .mockResolvedValueOnce(ok(fixtures.op014, 202))
    .mockResolvedValueOnce(
      ok({
        ...fixtures.op009,
        status: 'completed',
        progress: 100,
        result: results.avatarInteraction,
        ...jobOverrides,
      }),
    );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}
beforeEach(() => clearCsrf());
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  clearCsrf();
});

describe('数字人聊天真实接口适配', () => {
  it('提交数字人任务并读取回复，携带Cookie、CSRF和幂等键', async () => {
    const fetchMock = mockChat();
    await expect(backend.avatarChat('我有点紧张', contextId)).resolves.toBe(
      results.avatarInteraction.text,
    );
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/v1/session/csrf',
      '/api/v1/avatar/interactions',
      `/api/v1/jobs/${fixtures.op014.jobId}`,
    ]);
    const options = fetchMock.mock.calls[1]![1] as RequestInit;
    expect(JSON.parse(options.body as string)).toEqual({ text: '我有点紧张', contextId });
    expect(options.credentials).toBe('include');
    expect(options.headers).toMatchObject({
      'X-CSRF-Token': 'csrf-test',
      'Idempotency-Key': expect.any(String),
    });
  });
  it('模型未配置时保留后端失败原因，不返回演示回复', async () => {
    mockChat({
      status: 'failed_retryable',
      progress: 0,
      result: null,
      error: { code: 'DEPENDENCY_UNAVAILABLE', message: '尚未配置模型服务', retryable: true },
    });
    await expect(backend.avatarChat('你好', contextId)).rejects.toMatchObject({
      code: 'DEPENDENCY_UNAVAILABLE',
      message: '尚未配置模型服务',
    });
  });
  it('执行中继续轮询，完成后才返回', async () => {
    vi.useFakeTimers();
    const fetchMock = mockChat({ status: 'running', progress: 20, result: null });
    fetchMock.mockResolvedValueOnce(
      ok({
        ...fixtures.op009,
        status: 'completed',
        progress: 100,
        result: results.avatarInteraction,
      }),
    );
    const response = backend.avatarChat('你好', contextId);
    await vi.advanceTimersByTimeAsync(1100);
    await expect(response).resolves.toBe(results.avatarInteraction.text);
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });
  it('停止等待后不再轮询', async () => {
    const fetchMock = mockChat({ status: 'running', progress: 20, result: null });
    const controller = new AbortController();
    const response = backend.avatarChat('你好', contextId, controller.signal);
    const assertion = expect(response).rejects.toMatchObject({ code: 'CANCELLED' });
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    controller.abort();
    await assertion;
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
  it('超过契约长度的文字在发出请求前拒绝', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(backend.avatarChat('字'.repeat(4001), contextId)).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

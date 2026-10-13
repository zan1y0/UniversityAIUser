export class ApiError extends Error {
  constructor(
    message: string,
    public code: string = 'REQUEST_ERROR',
  ) {
    super(message);
  }
}
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
export function unwrapResult(value: unknown): unknown {
  if (!isRecord(value)) throw new ApiError('后端响应格式不符合 Result 契约', 'INVALID_RESPONSE');
  if (value.code !== 200 && value.code !== 'OK')
    throw new ApiError(
      typeof value.message === 'string' ? value.message : '请求失败',
      String(value.code),
    );
  if (value.data === null || value.data === undefined)
    throw new ApiError('后端尚未返回业务数据，请先实现对应服务。', 'EMPTY_BACKEND');
  return value.data;
}
export async function request(
  path: string,
  options: { method?: string; body?: unknown; signal?: AbortSignal } = {},
): Promise<unknown> {
  const controller = new AbortController();
  const abort = () => controller.abort();
  if (options.signal?.aborted) abort();
  options.signal?.addEventListener('abort', abort, { once: true });
  const timeout = window.setTimeout(abort, 60000);
  try {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      'X-Request-ID': crypto.randomUUID(),
    };
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    const csrf = document.cookie
      .split('; ')
      .find((v) => v.startsWith('XSRF-TOKEN='))
      ?.slice(11);
    if (csrf && options.method && options.method !== 'GET')
      headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf);
    const response = await fetch(`${import.meta.env.VITE_API_BASE || '/api'}${path}`, {
      method: options.method || 'GET',
      credentials: 'include',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
    });
    if (!response.ok)
      throw new ApiError(`请求失败（HTTP ${response.status}）`, String(response.status));
    const raw: unknown = await response.json();
    return unwrapResult(raw);
  } catch (error) {
    if (controller.signal.aborted)
      throw new ApiError(
        options.signal?.aborted ? '请求已取消' : '请求超时，请重试',
        options.signal?.aborted ? 'CANCELLED' : 'TIMEOUT',
      );
    if (error instanceof ApiError) throw error;
    throw new ApiError('无法连接后端或响应不是有效 JSON，请检查服务和代理配置。', 'NETWORK');
  } finally {
    clearTimeout(timeout);
    options.signal?.removeEventListener('abort', abort);
  }
}

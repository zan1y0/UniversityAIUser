import { ApiError, isRecord } from './errors';
export { ApiError, isRecord } from './errors';

const base = (import.meta.env.VITE_API_BASE || '/api/v1').replace(/\/$/, '');
let csrfToken: string | undefined;
let csrfRequest: Promise<string> | undefined;
export function clearCsrf() {
  csrfToken = undefined;
  csrfRequest = undefined;
}
export function unwrapResult(value: unknown): unknown {
  if (
    !isRecord(value) ||
    typeof value.code !== 'string' ||
    typeof value.message !== 'string' ||
    typeof value.traceId !== 'string'
  )
    throw new ApiError('服务响应不符合接口约定', 'INVALID_RESPONSE');
  if (value.code !== 'OK') throw new ApiError(value.message, value.code, value.traceId);
  if (value.data === null || value.data === undefined)
    throw new ApiError('后端尚未返回业务数据，请检查对应服务。', 'EMPTY_BACKEND', value.traceId);
  return value.data;
}
async function csrf(signal?: AbortSignal): Promise<string> {
  if (csrfToken) return csrfToken;
  csrfRequest ??= (async () => {
    const response = await fetch(`${base}/session/csrf`, {
      credentials: 'include',
      signal,
      headers: { Accept: 'application/json' },
    });
    const raw: unknown = await response.json();
    if (!response.ok)
      throw new ApiError('无法获取登录与操作凭证', 'CSRF_REJECTED', undefined, response.status);
    const data = unwrapResult(raw);
    if (
      !isRecord(data) ||
      typeof data.token !== 'string' ||
      !data.token ||
      data.headerName !== 'X-CSRF-Token'
    )
      throw new ApiError('安全凭证格式不正确', 'INVALID_RESPONSE');
    csrfToken = data.token;
    return csrfToken;
  })();
  try {
    return await csrfRequest;
  } finally {
    csrfRequest = undefined;
  }
}
export interface RequestOptions {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
  idempotencyKey?: string;
  timeoutMs?: number;
  responseSchema?: (value: unknown) => void;
  binary?: boolean;
}
export async function request(path: string, options: RequestOptions = {}): Promise<unknown> {
  if (!path.startsWith('/') || path.startsWith('//'))
    throw new ApiError('无效请求地址', 'INVALID_REQUEST');
  const controller = new AbortController();
  const abort = () => controller.abort();
  if (options.signal?.aborted) abort();
  options.signal?.addEventListener('abort', abort, { once: true });
  const timeout = globalThis.setTimeout(abort, options.timeoutMs ?? 15000);
  try {
    const method = options.method || 'GET';
    const multipart = options.body instanceof FormData;
    const headers: Record<string, string> = { Accept: options.binary ? '*/*' : 'application/json' };
    if (options.body !== undefined && !multipart) headers['Content-Type'] = 'application/json';
    if (!['GET', 'HEAD'].includes(method)) headers['X-CSRF-Token'] = await csrf(controller.signal);
    if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey;
    const response = await fetch(`${base}${path}`, {
      method,
      credentials: 'include',
      headers,
      body:
        options.body === undefined
          ? undefined
          : multipart
            ? (options.body as FormData)
            : JSON.stringify(options.body),
      signal: controller.signal,
    });
    if (response.status === 204) return undefined;
    if (response.ok && options.binary) return response.blob();
    let raw: unknown;
    try {
      raw = await response.json();
    } catch {
      throw new ApiError(
        `服务没有返回有效数据（HTTP ${response.status}）`,
        'INVALID_RESPONSE',
        undefined,
        response.status,
      );
    }
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) clearCsrf();
      if (response.status === 401 && isRecord(raw) && raw.code === 'UNAUTHENTICATED')
        globalThis.dispatchEvent?.(new Event('auth-expired'));
      throw new ApiError(
        isRecord(raw) && typeof raw.message === 'string'
          ? raw.message
          : `请求失败（HTTP ${response.status}）`,
        isRecord(raw) && typeof raw.code === 'string' ? raw.code : 'HTTP_ERROR',
        isRecord(raw) && typeof raw.traceId === 'string' ? raw.traceId : undefined,
        response.status,
      );
    }
    options.responseSchema?.(raw);
    return unwrapResult(raw);
  } catch (error) {
    if (controller.signal.aborted)
      throw new ApiError(
        options.signal?.aborted ? '操作已停止' : '请求超时，请重试',
        options.signal?.aborted ? 'CANCELLED' : 'TIMEOUT',
      );
    if (error instanceof ApiError) throw error;
    throw new ApiError('无法连接服务，请检查后端连接或稍后重试。', 'NETWORK');
  } finally {
    globalThis.clearTimeout(timeout);
    options.signal?.removeEventListener('abort', abort);
  }
}

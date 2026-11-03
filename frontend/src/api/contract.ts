import { operations, assertSchema, schemas, type Schema } from './schema';
import { request, ApiError } from './client';
import type { Operations, Job, EventPayload } from './generated';

export type OperationId = keyof Operations;
export interface Input {
  path?: Record<string, unknown>;
  query?: Record<string, unknown>;
  body?: unknown;
}
export async function execute<K extends OperationId>(
  id: K,
  input: Operations[K]['input'],
  options?: { signal?: AbortSignal; key?: string },
): Promise<Operations[K]['output']>;
export async function execute(
  id: OperationId,
  input: Input,
  options?: { signal?: AbortSignal; key?: string },
): Promise<unknown>;
export async function execute(
  id: OperationId,
  input: Input,
  options: { signal?: AbortSignal; key?: string } = {},
): Promise<unknown> {
  const op = operations[id]!;
  if (op.idempotent && !options.key) throw new ApiError('缺少操作去重凭证', 'INVALID_REQUEST');
  for (const where of ['path', 'query'] as const) {
    for (const field of Object.keys(input[where] || {}))
      if (!op.parameters.some((p) => p.in === where && p.name === field))
        throw new ApiError('操作包含未约定的参数', 'INVALID_REQUEST');
  }
  let path = op.path.replace('/api/v1', '');
  const query = new URLSearchParams();
  for (const p of op.parameters.filter((p) => ['query', 'path'].includes(p.in))) {
    const value = (p.in === 'path' ? input.path : input.query)?.[p.name];
    if (value === undefined) {
      if (p.required) throw new ApiError('请选择或填写所需的关联资料', 'VALIDATION_FAILED');
      continue;
    }
    assertSchema(p.schema, value);
    if (p.in === 'path') path = path.replace(`{${p.name}}`, encodeURIComponent(String(value)));
    else query.set(p.name, Array.isArray(value) ? value.join(',') : String(value));
  }
  if (op.body && !op.multipart) assertSchema(op.body, input.body);
  if (query.size) path += `?${query}`;
  return request(path, {
    method: op.method,
    body: input.body,
    signal: options.signal,
    idempotencyKey: options.key,
    binary: id === 'op007',
    timeoutMs: op.multipart ? 60000 : 15000,
    responseSchema: op.response ? (raw) => assertSchema(op.response!, raw, true) : undefined,
  });
}
function pause(milliseconds: number, signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new ApiError('操作已停止', 'CANCELLED'));
      return;
    }
    const cancel = () => {
      clearTimeout(timer);
      reject(new ApiError('操作已停止', 'CANCELLED'));
    };
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', cancel);
      resolve();
    }, milliseconds);
    signal?.addEventListener('abort', cancel, { once: true });
  });
}
export async function waitJob(id: string, signal: AbortSignal, onUpdate?: (job: Job) => void) {
  const deadline = Date.now() + 120000;
  for (let count = 0; count < 90 && Date.now() < deadline; count++) {
    const job = await execute('op009', { path: { id } }, { signal });
    onUpdate?.(job);
    if (job.status === 'completed') return job.result;
    if (['failed', 'failed_retryable', 'cancelled'].includes(job.status))
      throw new ApiError(
        job.error?.message || '生成未完成，请稍后重试',
        job.error?.code || job.status,
      );
    await pause(Math.min(1000 + count * 100, 3000), signal);
  }
  throw new ApiError('生成仍在进行，可稍后查询结果', 'TIMEOUT');
}
export function subscribeEvents(onEvent: (event: EventPayload) => void, onReconnect?: () => void) {
  const source = new EventSource(
    `${(import.meta.env.VITE_API_BASE || '/api/v1').replace(/\/$/, '')}/events`,
    { withCredentials: true },
  );
  const received = new Set<string>();
  for (const name of [
    'ai.delta',
    'ai.completed',
    'job.failed',
    'task.reminder',
    'exam.scored',
    'policy.updated',
  ])
    source.addEventListener(name, (message) => {
      try {
        const raw: unknown = JSON.parse((message as MessageEvent).data);
        assertSchema(schemas.EventPayload as Schema, raw, true);
        const event = raw as EventPayload;
        if (event.type !== name || received.has(event.eventId)) return;
        received.add(event.eventId);
        if (received.size > 2000) received.delete(received.values().next().value!);
        onEvent(event);
      } catch {
        /* 不符合契约的流式事件不进入业务页面。 */
      }
    });
  let opened = false;
  source.onopen = () => {
    if (opened) onReconnect?.();
    opened = true;
  };
  return () => source.close();
}

export class ApiError extends Error {
  constructor(
    message: string,
    public code = 'REQUEST_ERROR',
    public traceId?: string,
    public status?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

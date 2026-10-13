import { ref } from 'vue';
import { ApiError } from '../api/client';
import type { LoadState } from '../domain/types';
export function useLoad() {
  const status = ref<LoadState>('idle');
  const error = ref('');
  async function run<T>(operation: () => Promise<T>): Promise<T | undefined> {
    status.value = 'loading';
    error.value = '';
    try {
      const result = await operation();
      status.value = Array.isArray(result) && !result.length ? 'empty' : 'success';
      return result;
    } catch (e) {
      status.value = e instanceof ApiError && e.code === 'CANCELLED' ? 'cancelled' : 'error';
      error.value = e instanceof Error ? e.message : '操作失败';
      return undefined;
    }
  }
  return { status, error, run };
}

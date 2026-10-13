import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { AppMode, User } from '../domain/types';

export const useAppStore = defineStore('app', () => {
  const mode = ref<AppMode>(
    sessionStorage.getItem('kaoyan-mode') === 'live' ||
      (!sessionStorage.getItem('kaoyan-mode') && import.meta.env.VITE_APP_MODE === 'live')
      ? 'live'
      : 'demo',
  );
  const user = ref<User | null>(null);
  const toast = ref('');
  const displayName = computed(() =>
    mode.value === 'demo' ? '研途同学' : user.value?.nickname || '访客',
  );
  let toastTimer: ReturnType<typeof setTimeout>;
  function notify(message: string) {
    toast.value = message;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => (toast.value = ''), 4500);
  }
  function setMode(value: AppMode) {
    mode.value = value;
    user.value = null;
    sessionStorage.setItem('kaoyan-mode', value);
  }
  return { mode, user, toast, displayName, notify, setMode };
});

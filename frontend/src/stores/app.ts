import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { AppMode, User } from '../domain/types';
import { useCatalogStore } from './catalog';
import { clearCsrf } from '../api/client';
import { sessionValue, saveSessionValue } from '../domain/session-storage';

export const useAppStore = defineStore('app', () => {
  const mode = ref<AppMode>(sessionValue('kaoyan-mode') === 'demo' ? 'demo' : 'live');
  const user = ref<User | null>(null);
  const sessionChecked = ref(false);
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
    sessionChecked.value = false;
    useCatalogStore().reset();
    clearCsrf();
    saveSessionValue('kaoyan-mode', value);
  }
  return { mode, user, sessionChecked, toast, displayName, notify, setMode };
});

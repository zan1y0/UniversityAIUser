<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ApiError } from '../api/client';
import { execute, subscribeEvents } from '../api/contract';
import type { Notification } from '../api/generated';
import AppIcon from './AppIcon.vue';

const router = useRouter();
const inbox = ref<HTMLDialogElement | null>(null);
const messages = ref<Notification[]>([]);
const unread = ref(0);
const page = ref(1);
const total = ref(0);
const pageSize = 20;
const loading = ref(false);
const openingMessage = ref(false);
const error = ref('');
let timer: number;
let closeEvents: (() => void) | undefined;

const timeFormat = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  dateStyle: 'medium',
  timeStyle: 'short',
});

async function loadPage(number: number) {
  loading.value = true;
  error.value = '';
  try {
    const result = await execute('op080', { query: { page: number, pageSize } });
    messages.value = result.items;
    page.value = result.page;
    total.value = result.total;
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '消息加载失败，请重试。';
  } finally {
    loading.value = false;
  }
}

async function refreshUnread() {
  if (document.hidden) return;
  try {
    const result = await execute('op080', { query: { unreadOnly: true, pageSize: 1 } });
    const changed = unread.value !== result.total;
    unread.value = result.total;
    if (changed && inbox.value?.open && page.value === 1 && !loading.value && !openingMessage.value)
      await loadPage(1);
  } catch {
    // The next refresh or an explicit inbox open can retry.
  }
}

function openInbox() {
  inbox.value?.showModal();
  messages.value = [];
  total.value = 0;
  page.value = 1;
  void loadPage(1);
  void refreshUnread();
}

async function openMessage(message: Notification) {
  if (openingMessage.value) return;
  openingMessage.value = true;
  error.value = '';
  try {
    if (!message.read)
      await execute('op081', { path: { id: message.id }, body: { version: message.version } });
  } catch (failure) {
    // Another tab may have read the notification since this list was loaded.
    if (!(failure instanceof ApiError) || failure.status !== 409) {
      error.value = failure instanceof Error ? failure.message : '无法更新消息状态，请重试。';
      openingMessage.value = false;
      return;
    }
  }
  openingMessage.value = false;
  inbox.value?.close();
  void refreshUnread();
  const destination = {
    'teaching.task.assigned': '/teaching',
    'policy.updated': '/news',
    'task.reminder': '/planning',
    'exam.scored': '/exams',
  }[message.type];
  await router.push({
    path: destination,
    query: message.type === 'teaching.task.assigned' ? { task: message.resourceId } : {},
  });
}

onMounted(() => {
  void refreshUnread();
  closeEvents = subscribeEvents((event) => {
    if (event.type === 'teaching.task.assigned') void refreshUnread();
  }, refreshUnread);
  timer = window.setInterval(refreshUnread, 10000);
  window.addEventListener('focus', refreshUnread);
  document.addEventListener('visibilitychange', refreshUnread);
});
onUnmounted(() => {
  closeEvents?.();
  window.clearInterval(timer);
  window.removeEventListener('focus', refreshUnread);
  document.removeEventListener('visibilitychange', refreshUnread);
});
</script>

<template>
  <button class="icon-btn bell-button" type="button"
    :aria-label="unread ? `消息通知，${unread} 条未读` : '消息通知'" @click="openInbox">
    <AppIcon name="bell" />
    <span v-if="unread" class="bell-badge" aria-hidden="true">{{ unread > 99 ? '99+' : unread }}</span>
  </button>
  <dialog ref="inbox" class="inbox" aria-labelledby="inbox-title">
    <header>
      <h2 id="inbox-title">消息通知</h2>
      <button class="btn" type="button" @click="inbox?.close()">关闭</button>
    </header>
    <div v-if="error" role="alert" class="inbox-error">
      <p>{{ error }}</p>
      <button class="btn" type="button" @click="loadPage(page)">刷新消息</button>
    </div>
    <p v-if="loading" role="status">正在加载消息…</p>
    <p v-else-if="!messages.length && !error" class="muted">暂无消息。</p>
    <ul v-if="messages.length" class="inbox-list">
      <li v-for="message in messages" :key="message.id">
        <button class="message" type="button" :disabled="openingMessage" @click="openMessage(message)">
          <strong>{{ message.title }} <small v-if="!message.read">未读</small></strong>
          <span class="message-content">{{ message.content }}</span>
          <time :datetime="message.createdAt">通知时间 {{ timeFormat.format(new Date(message.createdAt)) }}（北京时间）</time>
        </button>
      </li>
    </ul>
    <footer v-if="total > pageSize">
      <button class="btn" type="button" :disabled="loading || page <= 1" @click="loadPage(page - 1)">上一页</button>
      <span>第 {{ page }} 页</span>
      <button class="btn" type="button" :disabled="loading || page * pageSize >= total" @click="loadPage(page + 1)">下一页</button>
    </footer>
  </dialog>
</template>

<style scoped>
button.icon-btn.bell-button { position: relative; display: inline-flex; }
.bell-badge { position: absolute; top: -4px; right: -5px; min-width: 17px; padding: 1px 4px; border-radius: 20px; background: #a33a38; color: white; font-size: 10px; text-align: center; }
.inbox { box-sizing: border-box; position: fixed; inset: 64px 16px auto auto; margin: 0; width: min(420px, calc(100% - 32px)); max-height: calc(100dvh - 82px); padding: 18px; overflow: auto; border: 1px solid #d3dfd8; border-radius: 16px; color: #223d33; }
.inbox::backdrop { background: rgb(16 37 30 / 26%); }
.inbox header, .inbox footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.inbox h2 { margin: 0; font-size: 1.15rem; }
.inbox-list { list-style: none; padding: 0; margin: 12px 0; }
.inbox-list li + li { border-top: 1px solid #e3e9e6; }
.message { display: grid; gap: 5px; width: 100%; padding: 12px 8px; border: 0; border-radius: 8px; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; overflow-wrap: anywhere; }
.message:hover, .message:focus-visible { background: #edf5f0; }
.message small { color: #126f5c; }
.message time { font-size: .8rem; color: #4c6459; }
.message-content { white-space: pre-wrap; }
.inbox-error { color: #a33a38; }
</style>

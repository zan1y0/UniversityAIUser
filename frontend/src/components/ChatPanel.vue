<script setup lang="ts">
import { onBeforeUnmount, ref, nextTick, watch } from 'vue';
import { useAppStore } from '../stores/app';
import { backend } from '../api/backend';
import { useSpeech } from '../composables/useSpeech';
import type { Message } from '../domain/types';
import AppIcon from './AppIcon.vue';
const props = defineProps<{
  purpose: string;
  greeting: string;
  prompts?: string[];
  context?: string;
  english?: boolean;
}>();
const app = useAppStore();
const messages = ref<Message[]>([
  { id: crypto.randomUUID(), role: 'assistant', content: props.greeting },
]);
const input = ref('');
const loading = ref(false);
const error = ref('');
const list = ref<HTMLDivElement>();
let controller: AbortController | undefined;
let sessionId = crypto.randomUUID();
const speech = useSpeech((text) => (input.value += `${input.value ? ' ' : ''}${text}`));
function sampleReply(text: string): string {
  if (props.purpose === '复试训练')
    return `【演示反馈】已收到你的回答。建议用“结论—依据—实例”组织表达，并补充一个具体经历。\n下一问：如果研究过程中遇到与预期相反的结果，你会怎样分析？\n这是固定示例反馈，不是实际 AI 评分。`;
  if (props.purpose === '陪伴督学')
    return `【演示陪伴】先给自己一个可完成的小目标：用 25 分钟完成一个知识点，再休息 5 分钟。遇到疲惫可以适当缩短任务。\n你刚才提到“${text.slice(0, 50)}”，可以把它拆成“读材料、做一题、复述思路”三步。`;
  return `【演示解题思路】先明确题目条件和要求，再选择可用的知识点。\n1. 列出已知条件，检查适用范围。\n2. 写出中间步骤，并解释为什么使用这个方法。\n3. 用边界或简单例子检验结果。\n你可以补充自己的第一步推导，我会继续引导。演示回复为固定模板，不代表真实模型分析。`;
}
async function send(text = input.value) {
  if (!text.trim() || loading.value) return;
  const value = text.trim().slice(0, 4000);
  input.value = '';
  error.value = '';
  messages.value.push({ id: crypto.randomUUID(), role: 'user', content: value });
  loading.value = true;
  controller = new AbortController();
  try {
    const history = messages.value
      .slice(0, -1)
      .slice(-8)
      .map((m) => `${m.role === 'user' ? '用户' : '助手'}：${m.content.slice(0, 1500)}`)
      .join('\n')
      .slice(-6000);
    const content =
      app.mode === 'demo'
        ? sampleReply(value)
        : await backend.chat(
            `[${props.purpose}] ${(props.context || '').slice(0, 4000)}\n以下是此前对话的上下文资料：\n${history}\n当前用户问题：${value}`,
            sessionId,
            controller.signal,
          );
    if (!controller.signal.aborted)
      messages.value.push({ id: crypto.randomUUID(), role: 'assistant', content });
  } catch (e) {
    error.value = e instanceof Error ? e.message : '对话失败';
    input.value = value;
  } finally {
    loading.value = false;
    await nextTick();
    list.value?.scrollTo({ top: list.value.scrollHeight, behavior: 'smooth' });
  }
}
function cancel() {
  controller?.abort();
  speech.stop();
}
function clear() {
  cancel();
  messages.value = [{ id: crypto.randomUUID(), role: 'assistant', content: props.greeting }];
  sessionId = crypto.randomUUID();
  error.value = '';
}
watch(() => props.purpose, clear);
onBeforeUnmount(cancel);
</script>
<template>
  <section class="card chat-panel">
    <div class="card-heading">
      <h2><AppIcon name="sparkles" />{{ purpose }}</h2>
      <button class="text-btn" @click="clear">新对话</button>
    </div>
    <p v-if="app.mode === 'live'" class="muted small">
      近期对话随本次问题发送，离开页面即清除。当前暂不提供知识引用和流式回答。
    </p>
    <div ref="list" class="messages" aria-live="polite" aria-relevant="additions">
      <div v-for="m in messages" :key="m.id" class="message" :class="m.role">
        <span class="message-avatar"
          ><AppIcon :name="m.role === 'assistant' ? 'sparkles' : 'user'" :size="17"
        /></span>
        <div>
          <p>{{ m.content }}</p>
          <button
            v-if="m.role === 'assistant'"
            class="text-btn small"
            @click="speech.speak(m.content, english ? 'en-US' : 'zh-CN')"
          >
            <AppIcon name="volume" :size="14" />朗读
          </button>
        </div>
      </div>
      <p v-if="loading" class="muted" role="status">正在思考…</p>
    </div>
    <div v-if="prompts?.length" class="chips">
      <button v-for="p in prompts" :key="p" class="chip" :disabled="loading" @click="send(p)">
        {{ p }}
      </button>
    </div>
    <p v-if="error || speech.error.value" class="inline-error" role="alert">
      {{ error || speech.error.value }}
    </p>
    <form class="chat-input" @submit.prevent="send()">
      <textarea
        v-model="input"
        :disabled="loading"
        maxlength="4000"
        aria-label="输入消息"
        placeholder="写下你的问题，或说说今天的学习状态…"
        rows="2"
        @keydown.ctrl.enter.prevent="send()"
      ></textarea>
      <div class="chat-actions">
        <button
          type="button"
          class="icon-btn"
          :aria-label="speech.listening.value ? '停止录音' : '语音输入'"
          :class="{ recording: speech.listening.value }"
          @click="speech.listen(english ? 'en-US' : 'zh-CN')"
        >
          <AppIcon name="mic" /></button
        ><span class="muted small">Ctrl + Enter 发送</span
        ><button
          v-if="loading || speech.speaking.value || speech.listening.value"
          type="button"
          class="btn secondary"
          @click="cancel"
        >
          停止</button
        ><button type="submit" class="btn" :disabled="!input.trim() || loading">
          <AppIcon name="send" :size="16" />发送
        </button>
      </div>
    </form>
    <p class="muted small">
      语音识别由浏览器提供，可能使用浏览器服务商网络；点击后才请求麦克风权限。对话与录音不存入本地缓存。
    </p>
  </section>
</template>

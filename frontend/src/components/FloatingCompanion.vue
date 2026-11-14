<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, nextTick, ref } from 'vue';
import { Mic, Camera, Mail, ArrowUp, Volume2, X, Aperture, Settings } from 'lucide-vue-next';
import { backend } from '../api/backend';
import { execute } from '../api/contract';
import { useAppStore } from '../stores/app';
import { useSpeech } from '../composables/useSpeech';
import { studyModes, type StudyMode } from '../api/study-supervision';
import SupervisionPanel from './SupervisionPanel.vue';
const app = useAppStore(),
  open = ref(false),
  blink = ref(false),
  busy = ref(false),
  draft = ref(''),
  error = ref('');
const size = 82,
  x = ref(0),
  y = ref(0),
  width = ref(innerWidth),
  height = ref(innerHeight);
const panel = computed(() => ({
  left:
    Math.max(8, Math.min(x.value + size - 540, width.value - Math.min(540, width.value - 16) - 8)) +
    'px',
  ...(y.value > height.value / 2
    ? { bottom: height.value - y.value + 6 + 'px' }
    : { top: y.value + size + 6 + 'px' }),
  maxHeight:
    Math.max(100, (y.value > height.value / 2 ? y.value : height.value - y.value - size) - 16) +
    'px',
}));
let positioned = false;
function position(a: number, b: number) {
  x.value = Math.max(4, Math.min(a, width.value - size - 4));
  y.value = Math.max(4, Math.min(b, height.value - size - 4));
}
function resize() {
  width.value = innerWidth;
  height.value = innerHeight;
  position(
    positioned ? x.value : innerWidth - size - 20,
    positioned ? y.value : innerHeight - size - 22,
  );
}
let gesture: { id: number; a: number; b: number; sx: number; sy: number } | undefined,
  dragged = false;
function down(e: PointerEvent) {
  if (!e.isPrimary || e.button !== 0) return;
  dragged = false;
  gesture = { id: e.pointerId, a: e.clientX, b: e.clientY, sx: x.value, sy: y.value };
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
}
function move(e: PointerEvent) {
  if (gesture?.id !== e.pointerId || !gesture) return;
  const dx = e.clientX - gesture.a,
    dy = e.clientY - gesture.b;
  if (Math.hypot(dx, dy) > 5) dragged = true;
  if (dragged) {
    positioned = true;
    position(gesture.sx + dx, gesture.sy + dy);
  }
}
let timer: ReturnType<typeof setTimeout>;
function toggle(e: MouseEvent) {
  if (dragged && e.detail) {
    dragged = false;
    return;
  }
  open.value = !open.value;
  blink.value = true;
  clearTimeout(timer);
  timer = setTimeout(() => (blink.value = false), 300);
  if (!open.value) {
    speech.stop();
    stopCamera();
  }
}
type Attachment = { file: File; url: string; id?: string };
type Message = { who: string; text: string; attachments?: Attachment[] };
const contextId = crypto.randomUUID(),
  cancel = new AbortController(),
  messages = ref<Message[]>([]),
  log = ref<HTMLElement>();
const speech = useSpeech((text) => {
  draft.value = (draft.value + ' ' + text).trim().slice(0, 4000);
});
const settingsOpen = ref(false);
const studyMode = ref<StudyMode>('GENTLE');
const reminderCount = ref(0);
const studyMotion = ref<StudyMode>();
let motionTimer: ReturnType<typeof setTimeout>;
function supervisionStatus(mode: StudyMode, count: number) {
  studyMode.value = mode;
  if (count > reminderCount.value && open.value) log.value?.scrollTo(0, 0);
  reminderCount.value = count;
}
function animateStudy(mode: StudyMode) {
  clearTimeout(motionTimer);
  studyMotion.value = mode;
  motionTimer = setTimeout(() => (studyMotion.value = undefined), 560);
}
function showSettings(show: boolean) {
  settingsOpen.value = show;
  if (show) void nextTick(() => log.value?.scrollTo(0, 0));
}
const files = ref<Attachment[]>([]),
  video = ref<HTMLVideoElement>(),
  cameraOpen = ref(false);
let stream: MediaStream | undefined,
  cameraVersion = 0;
function stopCamera() {
  cameraVersion++;
  stream?.getTracks().forEach((track) => track.stop());
  stream = undefined;
  cameraOpen.value = false;
}
function add(file: File) {
  if (!file.size || file.size > 10485760) {
    error.value = '请选择非空且不超过 10 MiB 的文件';
    return;
  }
  files.value.push({ file, url: URL.createObjectURL(file) });
}
function remove(i: number) {
  const item = files.value.splice(i, 1)[0];
  if (item) URL.revokeObjectURL(item.url);
}
function select(e: Event) {
  error.value = '';
  const el = e.target as HTMLInputElement;
  Array.from(el.files || []).forEach(add);
  el.value = '';
}
async function startCamera() {
  if (cameraOpen.value) {
    stopCamera();
    return;
  }
  error.value = '';
  const version = ++cameraVersion;
  cameraOpen.value = true;
  try {
    const media = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment' },
      audio: false,
    });
    if (version !== cameraVersion) {
      media.getTracks().forEach((t) => t.stop());
      return;
    }
    stream = media;
    await nextTick();
    if (video.value) video.value.srcObject = media;
  } catch {
    if (version === cameraVersion) {
      stopCamera();
      error.value = '未能打开相机，请检查摄像头权限，或用信封选择照片。';
    }
  }
}
function capture() {
  const v = video.value;
  if (!v?.videoWidth) return;
  const c = document.createElement('canvas');
  c.width = v.videoWidth;
  c.height = v.videoHeight;
  c.getContext('2d')?.drawImage(v, 0, 0);
  c.toBlob(
    (blob) => {
      if (blob && !cancel.signal.aborted)
        add(new File([blob], '拍照-' + Date.now() + '.jpg', { type: 'image/jpeg' }));
    },
    'image/jpeg',
    0.88,
  );
  stopCamera();
}
async function send() {
  const text = draft.value.trim(),
    attachments = [...files.value];
  if ((!text && !attachments.length) || busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    if (app.mode === 'live')
      for (const item of attachments) {
        if (item.id) continue;
        const body = new FormData();
        body.append('file', item.file);
        const data = await execute('op086', { body }, { signal: cancel.signal });
        item.id = data.id;
      }
    const history = messages.value
      .slice(-6)
      .map((m) => m.who + '：' + m.text)
      .join('\n');
    messages.value.push({ who: '我', text, attachments });
    files.value = [];
    draft.value = '';
    await nextTick();
    log.value?.scrollTo(0, log.value.scrollHeight);
    if (!text) return;
    let reply = '演示回复：我们先选一个今天能完成的小目标吧。';
    if (app.mode === 'live') {
      const room = 4000 - text.length - 8;
      const input = history && room > 0 ? history.slice(-room) + '\n当前问题：' + text : text;
      reply = await backend.avatarChat(input, contextId, cancel.signal);
    }
    messages.value.push({ who: '研小途', text: reply });
  } catch (e) {
    if (!cancel.signal.aborted) error.value = e instanceof Error ? e.message : '发送失败';
  } finally {
    busy.value = false;
    await nextTick();
    log.value?.scrollTo(0, log.value.scrollHeight);
  }
}
onBeforeUnmount(() => {
  stopCamera();
  for (const item of [...files.value, ...messages.value.flatMap((m) => m.attachments || [])])
    URL.revokeObjectURL(item.url);
});
onMounted(() => {
  resize();
  window.addEventListener('resize', resize);
});
onBeforeUnmount(() => {
  clearTimeout(timer);
  clearTimeout(motionTimer);
  cancel.abort();
  window.removeEventListener('resize', resize);
});
</script>
<template>
  <button
    class="orb"
    :class="{ awake: open, blink }"
    :data-study-motion="studyMotion"
    :style="{ left: x + 'px', top: y + 'px' }"
    :aria-expanded="open"
    :aria-description="reminderCount ? '有 ' + reminderCount + ' 项督学提醒' : undefined"
    aria-label="研小途，切换对话"
    @pointerdown="down"
    @pointermove="move"
    @pointerup="gesture = undefined"
    @pointercancel="gesture = undefined"
    @click="toggle"
  >
    <svg viewBox="0 0 120 120" aria-hidden="true">
      <defs>
        <radialGradient id="shell" cx=".32" cy=".22" r=".85">
          <stop stop-color="white" />
          <stop offset=".5" stop-color="#e6e9f1" />
          <stop offset=".78" stop-color="#a8a9b4" />
          <stop offset="1" stop-color="#595965" />
        </radialGradient>
        <linearGradient id="visor" x2=".8" y2="1">
          <stop stop-color="#57759b" />
          <stop offset=".4" stop-color="#152944" />
          <stop offset="1" stop-color="#091123" />
        </linearGradient>
      </defs>
      <circle cx="60" cy="60" r="54" fill="url(#shell)" stroke="#9da6b2" />
      <path d="M39 14 Q55 9 73 15 L66 21 Q53 23 39 20Z" fill="#bcc3cf" stroke="#7de7ec" />
      <path
        d="M13 45 Q22 23 55 25 Q82 26 94 49 Q107 77 81 89 Q53 103 24 85 Q5 73 13 45Z"
        fill="url(#visor)"
        stroke="#676a72"
        stroke-width="2"
      />
      <path d="M20 48 Q28 31 47 31" fill="none" stroke="#b8d4ee" opacity=".45" stroke-width="3" />
      <g class="eyes">
        <ellipse cx="34" cy="53" rx="6" ry="10" />
        <ellipse cx="65" cy="58" rx="8" ry="11" />
      </g>
      <path
        d="M40 72 Q46 78 53 73"
        class="smile"
        fill="none"
        stroke-width="2.5"
        stroke-linecap="round"
      />
      <ellipse cx="101" cy="67" rx="12" ry="20" fill="url(#shell)" stroke="#6e727a" />
      <ellipse cx="102" cy="67" rx="8" ry="15" fill="#8a909a" stroke="#88f5ff" stroke-width="2" />
      <path d="M42 104 Q64 112 84 98" fill="none" stroke="#8af8ff" />
    </svg>
    <span v-if="reminderCount" class="reminder-count" aria-hidden="true">{{ reminderCount }}</span>
  </button>
  <section v-show="open" class="orb-panel" :style="panel" aria-label="研小途对话">
    <header>
      <span class="dot"></span><strong>研小途</strong
      ><small class="mode-badge" :data-mode="studyMode">{{ studyModes[studyMode].label }}</small
      ><button
        aria-label="收起对话"
        @click="
          open = false;
          speech.stop();
          stopCamera();
        "
      >
        <X :size="18" />
      </button>
    </header>
    <div ref="log" class="orb-log" aria-live="polite">
      <SupervisionPanel
        :demo="app.mode === 'demo'"
        :settings="settingsOpen"
        :open="open"
        @status="supervisionStatus"
        @motion="animateStudy"
        @speak="speech.speak"
        @show-tasks="showSettings(true)"
      />
      <p v-if="!messages.length" class="welcome">今天想先完成什么小目标？</p>
      <article v-for="(m, i) in messages" :key="i" :class="{ mine: m.who === '我' }">
        <small>{{ m.who }}</small>
        <div class="bubble">
          {{ m.text
          }}<a
            v-for="item in m.attachments"
            :key="item.url"
            :href="item.url"
            :download="item.file.name"
            ><Mail :size="15" /> {{ item.file.name }}</a
          >
        </div>
        <button v-if="m.who === '研小途'" aria-label="朗读回复" @click="speech.speak(m.text)">
          <Volume2 :size="15" />
        </button>
      </article>
      <p v-if="busy">正在发送…</p>
    </div>
    <div class="composer">
      <div v-if="cameraOpen" class="preview">
        <video ref="video" autoplay playsinline muted /><button @click="capture">
          <Aperture :size="17" /> 拍摄</button
        ><button @click="stopCamera">取消</button>
      </div>
      <small v-if="error || speech.error.value" role="alert">{{
        error || speech.error.value
      }}</small>
      <small v-if="app.mode === 'live' && files.length">附件会单独上传，AI 回复只读取文字。</small>
      <div class="chips">
        <span v-for="(item, i) in files" :key="item.url"
          >{{ item.file.name }}
          <button :disabled="busy" aria-label="移除附件" @click="remove(i)"><X :size="14" /></button
        ></span>
      </div>
      <textarea
        v-model="draft"
        :disabled="busy"
        maxlength="4000"
        rows="2"
        aria-label="给研小途发送消息"
        placeholder="给研小途发送消息…"
        @keydown.ctrl.enter.prevent="send"
      />
      <div class="tools">
        <button
          class="pill"
          :aria-pressed="speech.listening.value"
          :title="speech.listening.value ? '结束录音' : '语音输入'"
          @click="speech.listening.value ? speech.stop() : speech.listen()"
        >
          <Mic :size="18" />{{ speech.listening.value ? '录音中' : '语音' }}</button
        ><button class="pill" :disabled="busy" :aria-pressed="cameraOpen" @click="startCamera">
          <Camera :size="18" />拍照
        </button>
        <button
          class="pill"
          type="button"
          :aria-expanded="settingsOpen"
          @click="showSettings(!settingsOpen)"
        >
          <Settings :size="18" />设置
        </button>
        <label class="mail" title="添加文件或图片"
          ><Mail :size="21" /><input
            type="file"
            aria-label="添加文件或图片"
            multiple
            :disabled="busy"
            @change="select"
        /></label>
        <button
          class="send"
          aria-label="发送"
          title="发送"
          :disabled="busy || (!draft.trim() && !files.length)"
          @click="send"
        >
          <ArrowUp :size="21" />
        </button>
      </div>
    </div>
  </section>
</template>
<style scoped>
.orb {
  position: fixed;
  z-index: 1001;
  width: 82px;
  height: 82px;
  padding: 0;
  border: 0;
  background: transparent;
  touch-action: none;
  user-select: none;
  cursor: grab;
  filter: drop-shadow(0 5px 5px #10233a42);
}
.orb:active {
  cursor: grabbing;
}
.orb svg {
  display: block;
  width: 100%;
  pointer-events: none;
}
.reminder-count {
  position: absolute;
  top: 5px;
  right: 2px;
  min-width: 21px;
  padding: 2px 4px;
  border-radius: 12px;
  background: #f4e7cc;
  color: #715020;
  font-weight: 700;
}
.orb-panel .mode-badge {
  padding: 3px 9px;
  border-radius: 12px;
  color: #31634f;
  background: #e5f2e9;
}
.orb-panel .mode-badge[data-mode='SCHOLAR'] {
  color: #355e82;
  background: #e7eff8;
}
.orb-panel .mode-badge[data-mode='STRICT'] {
  color: #785321;
  background: #f7eddb;
}
@media (prefers-reduced-motion: no-preference) {
  .orb[data-study-motion='SCHOLAR'] svg {
    animation: study-guide 0.55s ease;
  }
  .orb[data-study-motion='GENTLE'] svg {
    animation: study-nod 0.55s ease;
  }
  .orb[data-study-motion='STRICT'] svg {
    animation: study-prompt 0.55s ease;
  }
}
@keyframes study-guide {
  45% {
    transform: translateX(-4px) rotate(-7deg);
  }
}
@keyframes study-nod {
  45% {
    transform: translateY(3px) scaleY(0.95);
  }
}
@keyframes study-prompt {
  35% {
    transform: translateY(-5px);
  }
}
.eyes {
  fill: #69808b;
  transform-box: fill-box;
  transform-origin: center;
}
.smile {
  stroke: #7097a5;
}
.awake .eyes {
  fill: #69fbff;
  filter: drop-shadow(0 0 3px #00dfff);
}
.awake .smile {
  stroke: #66efff;
}
.blink .eyes {
  animation: orb-blink 0.28s ease;
}
@keyframes orb-blink {
  50% {
    transform: scaleY(0.09);
  }
}
.orb-panel {
  position: fixed;
  z-index: 1000;
  box-sizing: border-box;
  width: min(540px, calc(100vw - 16px));
  height: min(570px, 72dvh);
  display: flex;
  flex-direction: column;
  overflow: auto;
  background: #fff;
  color: #22364a;
  border: 1px solid #d7e2e9;
  border-radius: 24px;
  box-shadow: 0 16px 64px #223a5a26;
  font-size: 15px;
}
.orb-panel header,
.tools,
.orb-panel button,
.mail,
.bubble a {
  display: flex;
  align-items: center;
}
.orb-panel header {
  gap: 10px;
  padding: 18px 22px;
  border-bottom: 1px solid #edf1f4;
}
.dot {
  width: 9px;
  height: 9px;
  background: #3abbb7;
  border-radius: 50%;
}
.orb-panel small {
  color: #627382;
}
.orb-panel button,
.mail {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  justify-content: center;
  gap: 6px;
}
.orb-panel header button,
.mail {
  margin-left: auto;
}
.orb-panel header button,
.mail,
.send {
  width: 38px;
  height: 38px;
  border-radius: 50%;
}
.orb-log {
  flex: 1 1 auto;
  min-height: 60px;
  overflow: auto;
  padding: 18px 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.welcome {
  margin: auto;
  color: #667b8c;
}
.orb-log article {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 5px;
  max-width: 83%;
  align-self: flex-start;
}
.orb-log article.mine {
  align-self: flex-end;
  align-items: flex-end;
}
.bubble {
  background: #f3f6f9;
  border-radius: 4px 16px 16px;
  padding: 10px 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-width: 100%;
}
.mine .bubble {
  background: #e8efff;
  border-radius: 16px 4px 16px 16px;
}
.bubble a {
  gap: 6px;
  overflow-wrap: anywhere;
  color: #345889;
}
.composer {
  margin: 8px 16px 16px;
  padding: 14px;
  border: 1px solid #d9e0e7;
  border-radius: 20px;
  box-shadow: 0 3px 12px #1e345709;
  flex: none;
}
.composer textarea {
  box-sizing: border-box;
  width: 100%;
  border: 0;
  background: transparent;
  resize: none;
  font: inherit;
  line-height: 1.55;
  outline: 0;
  padding: 3px 4px 10px;
}
.composer:focus-within {
  border-color: #91acdf;
}
.tools {
  gap: 8px;
  flex-wrap: wrap;
}
.tools .pill {
  flex-shrink: 0;
}
.tools .pill {
  border: 1px solid #d0dcf8;
  color: #4165ae;
  background: #f2f6ff;
  border-radius: 24px;
  padding: 7px 12px;
}
.tools .pill[aria-pressed='true'] {
  background: #d4e3ff;
}
.mail {
  position: relative;
}
.mail input {
  position: absolute;
  inset: 0;
  opacity: 0;
  width: 100%;
  cursor: pointer;
}
.mail:focus-within {
  outline: 2px solid #698bc4;
}
.tools .send {
  background: #587de3;
  color: white;
}
.send:disabled {
  opacity: 0.48;
}
.chips span {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  overflow-wrap: anywhere;
  font-size: 12px;
  padding: 4px;
  background: #f3f6fa;
}
.preview video {
  width: 100%;
  max-height: 125px;
  background: #14202d;
}
.preview button {
  display: inline-flex;
}
.composer small[role='alert'] {
  display: block;
  color: #a14140;
  margin-bottom: 8px;
}
</style>

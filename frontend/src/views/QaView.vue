<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useAppStore } from '../stores/app';
import ChatPanel from '../components/ChatPanel.vue';
import AppIcon from '../components/AppIcon.vue';
const app = useAppStore();
const route = useRoute();
const subject = ref('数学');
const preview = ref('');
const filename = ref('');
const recognized = ref('');
const error = ref('');
const confirmed = ref(false);
const prompts = [
  '遇到极限题该怎样选择方法？',
  '如何拆解一道长难句？',
  '请帮我检查第一步解题思路',
  ...(typeof route.query.question === 'string' ? [route.query.question] : []),
];
function upload(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  error.value = '';
  if (!file) return;
  if (
    !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
    file.size > 5 * 1024 * 1024
  ) {
    error.value = '请选择不超过 5MB 的 JPG、PNG 或 WebP 图片。';
    input.value = '';
    return;
  }
  if (preview.value) URL.revokeObjectURL(preview.value);
  preview.value = URL.createObjectURL(file);
  filename.value = file.name;
  confirmed.value = false;
  recognized.value = '';
  input.value = '';
}
function remove() {
  if (preview.value) URL.revokeObjectURL(preview.value);
  preview.value = '';
  filename.value = '';
  recognized.value = '';
  confirmed.value = false;
}
onBeforeUnmount(remove);
</script>
<template>
  <div class="split">
    <div class="stack">
      <section class="card">
        <div class="card-heading">
          <h2>把问题说清楚</h2>
          <span class="tag">思路优先</span>
        </div>
        <div class="row wrap">
          <label class="field"
            >答疑科目<select v-model="subject">
              <option v-for="s in ['数学', '英语', '政治', '专业课']" :key="s">{{ s }}</option>
            </select></label
          ><label class="upload-label"
            ><AppIcon name="upload" :size="17" />上传题目图片<input
              class="sr-only"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              aria-label="上传题目图片"
              @change="upload" /></label
          ><span class="muted small">JPG / PNG / WebP · 最大 5MB</span>
        </div>
        <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
        <div v-if="preview" class="space-top">
          <div class="attachment">
            <img :src="preview" alt="待提问题目的本地预览" />
            <div>
              <p class="small">{{ filename }}</p>
              <p class="muted small">图片仅在本机预览，尚未上传。</p>
              <button class="text-btn" @click="remove">移除图片</button>
            </div>
          </div>
          <div class="notice">
            OCR
            上传与识别接口尚未提供。请自行录入并核对题目文字，确认后作为文字上下文发送；系统不会将预览当作识别结果。
          </div>
          <label class="field"
            >题目文字<textarea
              v-model="recognized"
              maxlength="4000"
              placeholder="请录入图片中的题目，核对公式、选项与已知条件"
              @input="confirmed = false"
            ></textarea></label
          ><button
            class="btn secondary space-top"
            :disabled="!recognized.trim()"
            @click="
              confirmed = true;
              app.notify('题目文字已确认，可在下方继续提问');
            "
          >
            {{ confirmed ? '题目文字已确认' : '确认题目文字' }}
          </button>
        </div>
      </section>
      <ChatPanel
        purpose="AI 引导式答疑"
        greeting="我们一起先分析题目条件和解题路径。把你的问题或已尝试的步骤告诉我吧。"
        :prompts="prompts"
        :context="`科目：${subject}。不要直接给最终答案，优先提供思路、步骤和方法。${confirmed ? `用户确认的题目文字：${recognized}` : ''}`"
      />
    </div>
    <div class="stack">
      <section class="card">
        <span class="module-icon"><AppIcon name="sparkles" /></span>
        <h2>从思路到理解</h2>
        <ul class="point-list">
          <li>描述题目条件与目标</li>
          <li>说出你已经尝试的方法</li>
          <li>追问卡住的具体步骤</li>
          <li>用类似问题检验理解</li>
        </ul>
        <p class="muted small">
          真实 AI 内容可能出错，请核对公式与依据；当前后端未提供知识引用，页面不会伪造资料来源。
        </p>
      </section>
      <section class="card">
        <h2>答疑后的下一步</h2>
        <p class="muted small space-top">
          理解一个方法，再用练习巩固。把易错点沉淀下来，下一次就更有把握。
        </p>
        <RouterLink to="/practice" class="btn secondary stretch space-top"
          >前往专项训练<AppIcon name="arrow" :size="15"
        /></RouterLink>
      </section>
    </div>
  </div>
</template>

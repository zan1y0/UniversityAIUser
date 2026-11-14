<script setup lang="ts">
import { ref } from 'vue';
import { teaching } from '../api/teaching';
import type { TeachingTask } from '../api/generated';
import { useAppStore } from '../stores/app';

const props = defineProps<{
  task: TeachingTask; teacher: boolean; person?: string; className?: string; linked?: boolean;
}>();
const emit = defineEmits<{ updated: [task: TeachingTask] }>();
const app = useAppStore();
const editing = ref(false);
const busy = ref(false);
const answer = ref('');
const score = ref<number | ''>('');
const feedback = ref('');
const file = ref<File>();
const error = ref('');
const has = (permission: string) => app.user?.permissions?.includes(permission) === true;
const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN');
const statuses = { pending: '待完成', completed: '已完成', cancelled: '已取消', submitted: '待批改', graded: '已批改' };

function openEditor() {
  error.value = ''; answer.value = ''; score.value = ''; feedback.value = ''; file.value = undefined;
  editing.value = true;
}

async function run(action: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try { await action(); }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '操作未完成，请重试'; }
  finally { busy.value = false; }
}

function saveHomework() {
  return run(async () => {
    const { id, version } = props.task;
    if (props.teacher) {
      if (typeof score.value !== 'number' || !Number.isInteger(score.value) || score.value < 0 || score.value > 100)
        throw new Error('请输入 0–100 的整数分数');
      emit('updated', await teaching.grade(id, version, score.value, feedback.value.trim()));
      app.notify('成绩已发布');
    } else {
      if (!answer.value.trim() && !file.value) throw new Error('请输入作业内容或选择一个附件');
      if (file.value && (file.value.size === 0 || file.value.size > 10 * 1024 * 1024))
        throw new Error('附件不能为空且不能超过 10 MiB');
      const result = file.value
        ? await teaching.submitAttachment(id, version, answer.value.trim(), file.value)
        : await teaching.submit(id, version, answer.value.trim());
      emit('updated', result);
      app.notify('作业已提交');
    }
    editing.value = false;
  });
}

function download() {
  return run(async () => {
    const metadata = props.task.submission?.attachment;
    if (!metadata) return;
    const blob = await teaching.downloadAttachment(props.task.id, props.teacher);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = metadata.fileName;
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
}

function transition() {
  return run(async () => {
    const { id, version } = props.task;
    emit('updated', await (props.teacher ? teaching.cancel(id, version) : teaching.complete(id, version)));
  });
}
</script>

<template>
  <article :id="`teaching-task-${task.id}`" class="task-card" :class="{ linked }" :tabindex="linked ? -1 : undefined">
    <div class="task-heading">
      <h3>{{ teacher ? '完成情况' : task.title }}</h3>
      <span>{{ task.status === 'pending' && task.requiresSubmission ? '待提交' : statuses[task.status] }}</span>
    </div>
    <template v-if="!teacher">
      <p>{{ task.description || '暂无任务说明' }}</p>
      <p class="muted">老师：{{ person || '指导老师' }} · 截止：{{ formatTime(task.deadline) }}</p>
      <p v-if="className" class="muted">班级：{{ className }}</p>
    </template>
    <p v-if="task.completedAt && task.status === 'completed'" class="muted">完成于 {{ formatTime(task.completedAt) }}</p>
    <p v-if="task.status === 'pending' && new Date(task.deadline).getTime() < Date.now()" class="inline-error">任务已逾期。</p>
    <template v-if="task.submission">
      <p class="muted">提交于 {{ formatTime(task.submission.submittedAt) }}{{ new Date(task.submission.submittedAt) > new Date(task.deadline) ? ' · 逾期提交' : '' }}</p>
      <details v-if="task.submission.content" :open="editing">
        <summary>查看作业内容</summary>
        <p class="homework-text">{{ task.submission.content }}</p>
      </details>
      <button v-if="task.submission.attachment" class="btn" type="button" :disabled="busy" @click="download">
        下载附件 {{ task.submission.attachment.fileName }}
      </button>
      <template v-if="task.submission.score != null">
        <p><strong>成绩 {{ task.submission.score }} / 100</strong></p>
        <p v-if="task.submission.feedback" class="homework-text">评语：{{ task.submission.feedback }}</p>
      </template>
    </template>
    <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
    <form v-if="editing" @submit.prevent="saveHomework">
      <template v-if="teacher">
        <label>分数（0–100）<input v-model.number="score" type="number" required min="0" max="100" step="1" :disabled="busy" /></label>
        <label>评语<textarea v-model="feedback" maxlength="2000" rows="3" :disabled="busy"></textarea></label>
      </template>
      <template v-else>
        <label>作业内容<textarea v-model="answer" maxlength="5000" rows="5" :disabled="busy"></textarea></label>
        <label>作业附件（一个文件或图片，最大 10 MiB）
          <input type="file" :disabled="busy" @change="file = ($event.target as HTMLInputElement).files?.[0]" />
        </label>
      </template>
      <div class="task-actions">
        <button class="btn primary" type="submit" :disabled="busy">{{ teacher ? '发布成绩' : '确认提交' }}</button>
        <button class="btn" type="button" :disabled="busy" @click="editing = false">取消</button>
      </div>
    </form>
    <button v-else-if="teacher && task.status === 'pending' && has('teacher:task:cancel')" class="btn" type="button" :disabled="busy" @click="transition">取消任务</button>
    <button v-else-if="teacher && task.status === 'submitted' && has('teacher:task:grade')" class="btn primary" type="button" @click="openEditor">批改作业</button>
    <button v-else-if="!teacher && task.status === 'pending' && task.requiresSubmission && has('student:task:submit')" class="btn primary" type="button" @click="openEditor">提交作业</button>
    <button v-else-if="!teacher && task.status === 'pending' && !task.requiresSubmission && has('student:task:complete')" class="btn primary" type="button" :disabled="busy" @click="transition">标记完成</button>
  </article>
</template>

<style scoped>
.task-card { padding: 16px 0; border-bottom: 1px solid #edf1ee; overflow-wrap: anywhere; }
.task-card.linked { border-radius: 8px; box-shadow: 0 0 0 2px #83b7a3; padding-inline: 12px; }
.task-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.task-heading h3 { margin: 0; }
.task-card label { display: grid; gap: 8px; margin: 14px 0; }
.task-card input, .task-card textarea { width: 100%; padding: 10px 12px; border: 1px solid #d3dfd8; border-radius: 9px; font: inherit; }
.task-card details { margin: 10px 0; }
.task-card summary { cursor: pointer; }
.task-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.homework-text { white-space: pre-wrap; }
</style>


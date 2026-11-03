<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { isRecord } from '../api/client';
import { execute } from '../api/contract';
import { label, enumLabel } from '../domain/contract-ui';
const props = defineProps<{ value: unknown; name?: string }>();
const audioUrl = ref('');
const audioError = ref('');
const entries = computed(() =>
  isRecord(props.value)
    ? Object.entries(props.value).filter(
        ([k]) =>
          ![
            'id',
            'versionId',
            'parentId',
            'resourceId',
            'jobId',
            'resultUrl',
            'user',
            'version',
            'contextVersion',
            'profileVersion',
            'requestId',
            'eventId',
            'contextId',
          ].includes(k) &&
          !/Id$/.test(k) &&
          !/(Ids)$/.test(k),
      )
    : [],
);
const visible = computed(() => (isRecord(props.value) ? entries.value : []));
const heading = computed(() =>
  isRecord(props.value)
    ? props.name === 'options' && typeof props.value.text === 'string'
      ? `${props.value.id || ''}. ${props.value.text}`
      : String(
          props.value.title || props.value.schoolName || props.value.name || props.value.stem || '',
        )
    : '',
);
const safeUrl = (value: unknown) =>
  typeof value === 'string' && /^https?:\/\//i.test(value) ? value : undefined;
function format(value: unknown, key = props.name || '') {
  if (value === null || value === undefined) return '暂无可用数据';
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (
    typeof value === 'number' &&
    [
      'probability',
      'mastery',
      'focus',
      'correctRate',
      'completionRate',
      'confidence',
      'frequency',
    ].includes(key)
  )
    return `${(value * 100).toFixed(1)}%`;
  if (typeof value === 'string' && /At$/.test(key) && Number.isFinite(Date.parse(value)))
    return new Date(value).toLocaleString('zh-CN');
  return enumLabel(value);
}
async function playAudio() {
  if (!isRecord(props.value) || typeof props.value.audioFileId !== 'string') return;
  try {
    if (audioUrl.value) URL.revokeObjectURL(audioUrl.value);
    const blob = await execute('op007', { path: { id: props.value.audioFileId } });
    audioUrl.value = URL.createObjectURL(blob);
  } catch (e) {
    audioError.value = e instanceof Error ? e.message : '无法读取音频';
  }
}
onBeforeUnmount(() => {
  if (audioUrl.value) URL.revokeObjectURL(audioUrl.value);
});
</script>
<template>
  <div v-if="Array.isArray(value)" class="contract-list">
    <p v-if="!value.length" class="muted">暂无记录。你可以调整筛选条件或开始新的学习任务。</p>
    <div
      v-for="(item, i) in value"
      :key="isRecord(item) && typeof item.id === 'string' ? item.id : i"
      class="contract-list-item"
    >
      <ContractResult :value="item" :name="name" />
    </div>
  </div>
  <section v-else-if="isRecord(value)" class="contract-result">
    <h3 v-if="heading" class="space-top">{{ heading }}</h3>
    <div v-for="[key, item] in visible" :key="key" class="contract-result-field">
      <template
        v-if="
          (!['title', 'schoolName', 'name', 'stem'].includes(key) &&
            !(name === 'options' && key === 'text')) ||
          !heading
        "
      >
        <h4 v-if="typeof item === 'object' && item !== null">
          {{ key === 'kind' && name !== 'answer' ? '内容类型' : label(key) }}
        </h4>
        <span v-else class="muted small"
          >{{ key === 'kind' && name !== 'answer' ? '内容类型' : label(key) }}
        </span>
        <ContractResult
          v-if="typeof item === 'object' && item !== null"
          :value="item"
          :name="key"
        />
        <a
          v-else-if="['url', 'previewUrl', 'assetUrl'].includes(key) && safeUrl(item)"
          :href="safeUrl(item)"
          target="_blank"
          rel="noopener noreferrer"
          class="text-btn"
          >{{ key === 'url' ? '查看原始资料' : '查看形象素材' }}</a
        >
        <p v-else class="reading">{{ format(item, key) }}</p>
      </template>
    </div>
    <template v-if="typeof value.audioFileId === 'string'"
      ><button class="btn secondary space-top" @click="playAudio">读取语音讲解</button
      ><audio v-if="audioUrl" :src="audioUrl" controls class="space-top" />
      <p v-if="audioError" class="inline-error">{{ audioError }}</p></template
    >
  </section>
  <p v-else class="reading">{{ format(value) }}</p>
</template>

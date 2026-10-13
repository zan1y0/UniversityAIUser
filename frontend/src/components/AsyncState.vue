<script setup lang="ts">
import type { LoadState } from '../domain/types';
defineProps<{ status: LoadState; error?: string; empty?: string }>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div v-if="status === 'loading'" class="state-box" role="status">
    <span class="spinner"></span>正在加载，请稍候…
  </div>
  <div v-else-if="status === 'error'" class="state-box error" role="alert">
    <p>{{ error }}</p>
    <button class="btn secondary" @click="$emit('retry')">重新加载</button>
  </div>
  <div v-else-if="status === 'empty'" class="state-box">{{ empty || '暂时没有数据。' }}</div>
  <div v-else-if="status === 'cancelled'" class="state-box">请求已取消，可以重新开始。</div>
</template>

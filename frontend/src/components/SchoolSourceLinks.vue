<script setup lang="ts">
import type { Source } from '../api/generated';
defineProps<{ sources: Source[] }>();
</script>

<template>
  <span v-if="!sources.length" class="muted">无可核验来源</span>
  <div v-for="source in sources" :key="source.url">
    <a
      v-if="/^https?:\/\//i.test(source.url)"
      :href="source.url"
      target="_blank"
      rel="noopener noreferrer"
      >{{ source.title }}</a
    >
    <span v-else>{{ source.title }}</span>
  </div>
</template>

<style scoped>
a {
  color: #176751;
  text-decoration: underline;
}
</style>

<script setup lang="ts">
defineProps<{ capability: string }>();
</script>
<template>
  <div class="notice" role="status">
    <strong>{{ capability }}待接入</strong>
    <p>当前后端尚未提供这项业务能力。可切换至演示模式体验页面流程；真实模式不会生成示例结果。</p>
  </div>
</template>

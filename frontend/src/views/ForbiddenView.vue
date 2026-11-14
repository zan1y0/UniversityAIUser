<script setup lang="ts">
import { roleHome } from '../domain/auth';
import { useAppStore } from '../stores/app';
const app = useAppStore();
</script>
<template>
  <section class="identity-card">
    <h1>无访问权限</h1>
    <p>当前工作空间没有访问此页面的权限。</p>
    <RouterLink :to="app.user ? roleHome(app.user) : '/login'">返回首页</RouterLink>
  </section>
</template>

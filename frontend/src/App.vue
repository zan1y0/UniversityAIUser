<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { modules } from './router';
import { useAppStore } from './stores/app';
import { useStudyStore } from './stores/study';
import AppIcon from './components/AppIcon.vue';
const app = useAppStore();
const study = useStudyStore();
const route = useRoute();
const router = useRouter();
const menuOpen = ref(false);
const current = computed(() => modules.find((m) => m.path === route.path));
function switchMode() {
  app.setMode(app.mode === 'demo' ? 'live' : 'demo');
  router.push(app.mode === 'live' ? '/login' : '/dashboard');
}
function logout() {
  app.user = null;
  router.push('/login');
}
function resetDemo() {
  if (window.confirm('清空所有本地演示进度、计划和模考记录？')) {
    study.reset();
    app.notify('演示数据已清空');
  }
}
</script>
<template>
  <a class="skip-link" href="#main-content">跳至主要内容</a>
  <div v-if="route.path === '/login'"><RouterView /></div>
  <div v-else class="app-shell">
    <button
      v-if="menuOpen"
      class="sidebar-backdrop"
      aria-label="关闭导航"
      @click="menuOpen = false"
    ></button>
    <aside class="sidebar" :class="{ open: menuOpen }">
      <RouterLink to="/dashboard" class="brand" @click="menuOpen = false"
        ><span class="brand-mark"><AppIcon name="sparkles" :size="25" /></span
        ><span>研途 <b>AI</b><small>你的考研成长伙伴</small></span></RouterLink
      >
      <nav aria-label="模块导航">
        <template v-for="group in ['概览', '学习', '提升', '资讯']" :key="group"
          ><p class="nav-label">{{ group }}</p>
          <RouterLink
            v-for="m in modules.filter((v) => v.group === group)"
            :key="m.path"
            :to="m.path"
            class="nav-item"
            @click="menuOpen = false"
            ><AppIcon :name="m.icon" :size="19" /><span>{{ m.short }}</span
            ><span v-if="m.module === 'M05'" class="nav-badge">AI</span></RouterLink
          ></template
        >
      </nav>
      <div class="sidebar-bottom">
        <span class="status-dot"></span><span>一步一个脚印，向目标靠近。</span>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <div class="breadcrumb">
          <button class="icon-btn mobile-menu" aria-label="打开导航" @click="menuOpen = !menuOpen">
            <AppIcon name="menu" /></button
          ><span>我的学习空间</span><AppIcon name="chevron" :size="14" /><strong>{{
            current?.short || '页面'
          }}</strong>
        </div>
        <div class="topbar-actions">
          <button class="mode-toggle" @click="switchMode">
            <span class="status-dot" :class="{ live: app.mode === 'live' }"></span
            >{{ app.mode === 'demo' ? '演示模式' : '真实接口模式'
            }}<span class="small">切换</span></button
          ><RouterLink class="icon-btn" to="/news" aria-label="查看资讯提醒"
            ><AppIcon name="bell" /></RouterLink
          ><span class="user-avatar">{{ app.displayName[0] }}</span
          ><span class="user-name">{{ app.displayName }}</span
          ><button v-if="app.user" class="icon-btn" aria-label="退出登录" @click="logout">
            <AppIcon name="logout" :size="17" />
          </button>
        </div>
      </header>
      <main id="main-content" tabindex="-1">
        <div class="page-heading">
          <div>
            <p class="eyebrow">{{ current?.module }} · KAОYAN LEARNING SPACE</p>
            <h1>{{ current?.title || '页面未找到' }}</h1>
            <p class="muted">{{ current?.subtitle }}</p>
          </div>
          <span class="date-label">{{
            new Date().toLocaleDateString('zh-CN', {
              month: 'long',
              day: 'numeric',
              weekday: 'long',
            })
          }}</span>
        </div>
        <div v-if="app.mode === 'demo'" class="demo-banner">
          <AppIcon name="alert" :size="16" /><span
            >当前为前端演示。示例院校、学习资料与 AI
            回复均不代表真实业务结果；本地学习操作仅保存在本浏览器。</span
          >
        </div>
        <div v-if="study.storageError" class="notice">
          浏览器存储不可用，演示进度仅保留在当前页面会话。
        </div>
        <RouterView :key="`${app.mode}-${route.path}`" />
        <footer class="page-footer">
          <span>研途 AI · 把每一次努力，变成看得见的进步</span
          ><button v-if="app.mode === 'demo'" class="text-btn small" @click="resetDemo">
            清空演示数据
          </button>
        </footer>
      </main>
    </div>
  </div>
  <div v-if="app.toast" class="toast" role="status">
    <AppIcon name="success" :size="18" />{{ app.toast }}
  </div>
</template>

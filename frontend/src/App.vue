<script setup lang="ts">
import { computed, ref, defineAsyncComponent } from 'vue';
import { roleHome } from './domain/auth';
import { backend } from './api/backend';
const ContractWorkspace = defineAsyncComponent(() => import('./components/ContractWorkspace.vue'));
import { useRoute, useRouter } from 'vue-router';
import { modules } from './router';
import { useAppStore } from './stores/app';
import { useStudyStore } from './stores/study';
import AppIcon from './components/AppIcon.vue';
import NotificationBell from './components/NotificationBell.vue';
import FloatingCompanion from './components/FloatingCompanion.vue';
const app = useAppStore();
const study = useStudyStore();
const route = useRoute();
const router = useRouter();
const menuOpen = ref(false);
const visibleModules = computed(() =>
  modules.filter((module) => {
    if (app.user?.activeRole === 'ADMIN') return false;
    if (app.user?.activeRole === 'TEACHER') return module.path === '/teacher';
    if (module.path === '/teacher' || module.path === '/companion') return false;
    return app.mode === 'live' || module.path !== '/teaching';
  }),
);
const current = computed(() => modules.find((m) => m.path === route.path));
function switchMode() {
  app.setMode(app.mode === 'demo' ? 'live' : 'demo');
  router.push(app.mode === 'live' ? '/login' : '/dashboard');
}
async function switchWorkspace(workspace: string) {
  try {
    const user = await backend.switchWorkspace(workspace);
    app.setMode('live');
    app.user = user;
    app.sessionChecked = true;
    await router.replace(roleHome(user));
  } catch (error) { app.notify(error instanceof Error ? error.message : '切换失败'); }
}
async function logout() {
  try {
    await backend.logout();
    app.setMode('live');
    router.push('/login');
  } catch (error) {
    app.notify(error instanceof Error ? error.message : '退出未完成，请重试');
  }
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
  <div v-if="['/login', '/activate'].includes(route.path)"><RouterView /></div>
  <div v-else class="app-shell">
    <button
      v-if="menuOpen"
      class="sidebar-backdrop"
      aria-label="关闭导航"
      @click="menuOpen = false"
    ></button>
    <aside class="sidebar" :class="{ open: menuOpen }">
      <RouterLink :to="app.user ? roleHome(app.user) : '/dashboard'" class="brand" @click="menuOpen = false"
        ><span class="brand-mark"><AppIcon name="sparkles" :size="25" /></span
        ><span>研途 <b>AI</b><small>你的考研成长伙伴</small></span></RouterLink
      >
      <nav aria-label="模块导航">
        <RouterLink v-if="app.user?.activeRole === 'ADMIN'" to="/admin" class="nav-item" @click="menuOpen = false">
          <AppIcon name="user" :size="19" /><span>平台管理</span>
        </RouterLink>
        <template v-for="group in ['概览', '学习', '提升', '资讯', '教学'].filter(g => visibleModules.some(m => m.group === g))" :key="group"
          ><p class="nav-label">{{ group }}</p>
          <RouterLink
            v-for="m in visibleModules.filter((v) => v.group === group)"
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
            current?.short || route.meta.title || '页面'
          }}</strong>
        </div>
        <div class="topbar-actions">
          <button class="mode-toggle" @click="switchMode">
            <span class="status-dot" :class="{ live: app.mode === 'live' }"></span
            >{{ app.mode === 'demo' ? '演示模式' : '真实接口模式'
            }}<span class="small">切换</span></button
          >
          <NotificationBell v-if="app.mode === 'live' && app.user && app.user.activeRole === 'STUDENT'" :key="app.user.id" />
          <RouterLink v-if="app.user" to="/account">账号设置</RouterLink>
          <select v-if="app.user?.roles && app.user.roles.length > 1" aria-label="切换工作空间"
            :value="app.user.activeRole" @change="switchWorkspace(($event.target as HTMLSelectElement).value)">
            <option v-for="role in app.user.roles" :key="role" :value="role">
              {{ ({ STUDENT: '学生', TEACHER: '老师', ADMIN: '管理员' } as Record<string, string>)[role] }}
            </option>
          </select>
          <span class="user-avatar">{{ app.displayName[0] }}</span
          ><span class="user-name">{{ app.displayName }}</span
          ><button v-if="app.user" class="icon-btn" aria-label="退出登录" @click="logout">
            <AppIcon name="logout" :size="17" />
          </button>
        </div>
      </header>
      <main id="main-content" tabindex="-1">
        <div v-if="!route.meta.title" class="page-heading">
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
        <ContractWorkspace v-if="app.mode === 'live' && current?.module.startsWith('M')" :key="route.path" /><RouterView
          v-else
          :key="`${app.mode}-${route.path}`"
        />
        <footer class="page-footer">
          <span>研途 AI · 把每一次努力，变成看得见的进步</span
          ><button v-if="app.mode === 'demo'" class="text-btn small" @click="resetDemo">
            清空演示数据
          </button>
        </footer>
      </main>
    </div>
  </div>
  <FloatingCompanion
    v-if="route.path !== '/login' && (app.mode === 'demo' || app.user?.activeRole === 'STUDENT')"
    :key="`${app.mode}-${app.user?.id ?? ''}`"
  />
  <div v-if="app.toast" class="toast" role="status">
    <AppIcon name="success" :size="18" />{{ app.toast }}
  </div>
</template>

<style scoped>
/* 移动布局仍需要提供退出入口。 */
.topbar-actions .icon-btn[aria-label='退出登录'] {
  display: inline-flex;
}
</style>

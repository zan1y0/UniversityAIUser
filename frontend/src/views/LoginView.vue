<script setup lang="ts">
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { backend } from '../api/backend';
import { useAppStore } from '../stores/app';
import { useLoad } from '../composables/useLoad';
import AvatarGuide from '../components/AvatarGuide.vue';
import AppIcon from '../components/AppIcon.vue';
const app = useAppStore();
const router = useRouter();
const route = useRoute();
const { status, error, run } = useLoad();
const phone = ref('');
const password = ref('');
async function login() {
  if (!/^1\d{10}$/.test(phone.value)) {
    app.notify('请输入 11 位手机号');
    return;
  }
  const user = await run(() => backend.login(phone.value, password.value));
  password.value = '';
  if (user) {
    app.setMode('live');
    app.user = user;
    const redirect =
      typeof route.query.redirect === 'string' &&
      route.query.redirect.startsWith('/') &&
      !route.query.redirect.startsWith('//') &&
      route.query.redirect !== '/login'
        ? route.query.redirect
        : '/dashboard';
    router.push(redirect);
  }
}
function demo() {
  app.setMode('demo');
  router.push('/dashboard');
}
</script>
<template>
  <main id="main-content" class="login-page" style="padding: 0; max-width: none">
    <section class="login-story">
      <div class="login-brand"><AppIcon name="sparkles" :size="31" />研途 AI</div>
      <h1>让考研路上的每一步，<br />都有方向。</h1>
      <p>从择校到复试，把学习、练习与反馈连接起来。今天的你，也在向理想靠近。</p>
      <AvatarGuide />
    </section>
    <section class="login-form">
      <div class="login-box">
        <h2>欢迎回到学习空间</h2>
        <p class="muted">登录真实服务，或先体验完整前端演示。</p>
        <form @submit.prevent="login">
          <label class="field"
            >手机号<input
              v-model="phone"
              type="tel"
              inputmode="numeric"
              autocomplete="username"
              maxlength="11"
              placeholder="请输入手机号"
              required /></label
          ><label class="field"
            >密码<input
              v-model="password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              required
              minlength="1"
              maxlength="128"
          /></label>
          <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
          <button class="btn" :disabled="status === 'loading'">
            {{ status === 'loading' ? '登录中…' : '登录真实服务'
            }}<AppIcon name="arrow" :size="16" />
          </button>
        </form>
        <p class="login-divider">或</p>
        <button class="btn secondary" @click="demo">进入前端演示</button>
        <p class="muted small">
          当前后端登录方法仍为空实现。完成认证、会话和安全配置后才能使用真实账户；前端不会伪造登录成功。
        </p>
      </div>
    </section>
  </main>
</template>

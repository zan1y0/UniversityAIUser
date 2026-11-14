<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { request, clearCsrf } from '../api/client';
import { useLoad } from '../composables/useLoad';
import { useAppStore } from '../stores/app';

const router = useRouter();
const app = useAppStore();
const { error, run, status } = useLoad();
const email = ref('');
const currentPassword = ref('');
const code = ref('');
const sent = ref(false);
async function send() {
  sent.value = Boolean(await run(() => request('/auth/email/change-code', {
    method: 'POST', body: { email: email.value, currentPassword: currentPassword.value },
  })));
}
async function change() {
  const done = await run(async () => {
    await request('/auth/email/change', { method: 'POST', body: {
      email: email.value, currentPassword: currentPassword.value, code: code.value,
    } });
    return true;
  });
  if (done) {
    clearCsrf();
    app.setMode('live');
    app.notify('邮箱已更新，请重新登录');
    await router.replace('/login');
  }
}
</script>

<template>
  <section class="identity-card">
    <h1>更换绑定邮箱</h1>
    <p>当前邮箱 {{ app.user?.email }}</p>
    <form @submit.prevent="change">
      <label>新邮箱<input v-model="email" type="email" required /></label>
      <label>当前密码<input v-model="currentPassword" type="password" autocomplete="current-password" required /></label>
      <button class="btn" type="button" :disabled="!email || !currentPassword || status === 'loading'" @click="send">获取验证码</button>
      <p v-if="sent" role="status">请查看新邮箱。</p>
      <label>邮箱验证码<input v-model="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required /></label>
      <p v-if="error" role="alert">{{ error }}</p>
      <button class="btn primary" :disabled="status === 'loading'">确认更换</button>
    </form>
  </section>
</template>

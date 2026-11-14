<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { request } from '../api/client';
import { useLoad } from '../composables/useLoad';

const token = new URLSearchParams(window.location.hash.slice(1)).get('token') || '';
const { error, run, status } = useLoad();
type Invitation = { email: string; legacy: boolean; bootstrap: boolean };
const invite = ref<Invitation>();
const code = ref('');
const password = ref('');
const confirmPassword = ref('');
const legacyPassword = ref('');
const notice = ref('');
const complete = ref(false);

onMounted(async () => {
  window.history.replaceState(window.history.state, '', window.location.pathname);
  if (!token) { error.value = '邀请链接缺少验证信息。'; return; }
  invite.value = await run(async () => await request('/auth/invitation/check', {
    method: 'POST', body: { token },
  }) as Invitation);
});
async function send() {
  const result = await run(() => request('/auth/email/code', { method: 'POST', body: { token, purpose: 'ACTIVATE' } }));
  if (result) notice.value = '验证码已发到邀请邮箱。';
}
async function activate() {
  const result = await run(async () => {
    await request('/auth/invitation/activate', { method: 'POST', body: {
      token, code: code.value, password: password.value, confirmPassword: confirmPassword.value,
      ...(invite.value?.legacy || invite.value?.bootstrap ? { legacyPassword: legacyPassword.value } : {}),
    } });
    return true;
  });
  if (result) { complete.value = true; notice.value = ''; }
}
</script>

<template>
  <main class="identity-page">
    <section class="identity-card">
      <h1>{{ invite?.legacy ? '绑定历史账号' : '激活账号' }}</h1>
      <p v-if="complete" role="status">账号已启用，请使用邮箱和新密码登录。</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <form v-if="invite && !complete" @submit.prevent="activate">
        <label>邀请邮箱<input :value="invite.email" type="email" readonly /></label>
        <label v-if="invite.legacy || invite.bootstrap">{{ invite.bootstrap ? '运维初始化凭据' : '旧账号密码' }}<input v-model="legacyPassword" type="password" required /></label>
        <label>邮箱验证码<input v-model="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required /></label>
        <button class="btn" type="button" :disabled="status === 'loading'" @click="send">获取验证码</button>
        <p v-if="notice" role="status">{{ notice }}</p>
        <label>设置密码<input v-model="password" type="password" autocomplete="new-password" minlength="8" required /></label>
        <label>确认密码<input v-model="confirmPassword" type="password" autocomplete="new-password" minlength="8" required /></label>
        <button class="btn primary" :disabled="status === 'loading'">激活账号</button>
      </form>
      <RouterLink to="/login">返回登录</RouterLink>
    </section>
  </main>
</template>

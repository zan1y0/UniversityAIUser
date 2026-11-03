<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { backend } from '../api/backend';
import { useAppStore } from '../stores/app';
import { useLoad } from '../composables/useLoad';
import AppIcon from '../components/AppIcon.vue';

const app = useAppStore();
const router = useRouter();
const route = useRoute();
const { status, error, run } = useLoad();
const registering = ref(false);
const loginName = ref('');
const displayName = ref('');
const password = ref('');
const confirmPassword = ref('');
const reveal = ref(false);
const busy = computed(() => status.value === 'loading');
const fireflies = Array.from({ length: 32 }, (_, i) => ({
  left: `${(i * 47 + 9) % 100}%`,
  top: `${(i * 31 + 7) % 100}%`,
  '--delay': `${-(i * 1.7)}s`,
  '--duration': `${12 + (i % 7) * 3}s`,
  '--size': `${3 + (i % 4) * 2}px`,
  '--drift': `${(i % 2 ? 1 : -1) * (35 + (i % 5) * 20)}px`,
}));
function changeTab(value: boolean) {
  registering.value = value;
  password.value = '';
  confirmPassword.value = '';
  error.value = '';
}
async function submit() {
  if (busy.value) return;
  error.value = '';
  if (!/^[a-zA-Z0-9_]{3,32}$/.test(loginName.value.trim())) {
    error.value = '账号需为3～32位字母、数字或下划线';
    return;
  }
  if (new TextEncoder().encode(password.value).length > 72) {
    error.value = '密码长度不能超过72字节';
    return;
  }
  if (registering.value && (!/[a-zA-Z]/.test(password.value) || !/\d/.test(password.value))) {
    error.value = '密码必须同时包含字母和数字';
    return;
  }
  if (registering.value && password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致';
    return;
  }
  const user = await run(() =>
    registering.value
      ? backend.register(loginName.value.trim(), password.value, displayName.value.trim())
      : backend.login(loginName.value.trim(), password.value),
  );
  if (!user) return;
  password.value = '';
  confirmPassword.value = '';
  app.setMode('live');
  app.user = user;
  app.sessionChecked = true;
  const target = route.query.redirect;
  const redirect =
    typeof target === 'string' &&
    /^\/(?!\/)/.test(target) &&
    !target.includes('\\') &&
    !target.startsWith('/login')
      ? target
      : '/dashboard';
  await router.replace(redirect);
}
function demo() {
  app.setMode('demo');
  router.push('/dashboard');
}
</script>

<template>
  <main id="main-content" class="auth-page">
    <div class="auth-glow glow-one" aria-hidden="true"></div>
    <div class="auth-glow glow-two" aria-hidden="true"></div>
    <div class="firefly-field" aria-hidden="true">
      <span v-for="(style, i) in fireflies" :key="i" class="firefly" :style="style"></span>
    </div>
    <a class="auth-brand" href="/login"
      ><span class="auth-brand-icon"><AppIcon name="sparkles" :size="23" /></span>研途 AI<span
        class="brand-caption"
        >陪你，向理想生长</span
      ></a
    >
    <section class="auth-card" aria-labelledby="auth-title">
      <div class="auth-emblem"><AppIcon name="sparkles" :size="29" /></div>
      <p class="auth-eyebrow">A LITTLE LIGHT, A LONG WAY</p>
      <h1 id="auth-title">{{ registering ? '开启你的研途' : '每一份努力，都有微光' }}</h1>
      <p class="auth-subtitle">
        {{ registering ? '种下一个目标，让未来慢慢发芽。' : '欢迎回来，继续向心中的理想靠近。' }}
      </p>
      <div class="auth-tabs" role="group" aria-label="登录或注册">
        <button
          type="button"
          :class="{ active: !registering }"
          :aria-pressed="!registering"
          :disabled="busy"
          @click="changeTab(false)"
        >
          登录
        </button>
        <button
          type="button"
          :class="{ active: registering }"
          :aria-pressed="registering"
          :disabled="busy"
          @click="changeTab(true)"
        >
          注册
        </button>
      </div>
      <form class="auth-form" :aria-busy="busy" @submit.prevent="submit">
        <label v-if="registering" class="auth-field" for="display-name"
          >昵称<input
            id="display-name"
            v-model="displayName"
            autocomplete="nickname"
            placeholder="我们该怎么称呼你"
            maxlength="32"
            required
            :disabled="busy"
        /></label>
        <label class="auth-field" for="login-name"
          >账号<input
            id="login-name"
            v-model="loginName"
            autocomplete="username"
            autocapitalize="none"
            spellcheck="false"
            placeholder="3～32位字母、数字或下划线"
            minlength="3"
            maxlength="32"
            required
            :disabled="busy"
        /></label>
        <label class="auth-field" for="password"
          >密码<span class="password-wrap"
            ><input
              id="password"
              aria-label="密码"
              v-model="password"
              :type="reveal ? 'text' : 'password'"
              :autocomplete="registering ? 'new-password' : 'current-password'"
              :placeholder="registering ? '至少8位，包含字母和数字' : '请输入你的密码'"
              minlength="8"
              maxlength="72"
              required
              :disabled="busy"
            /><button
              type="button"
              :aria-label="reveal ? '隐藏密码' : '显示密码'"
              :aria-pressed="reveal"
              @click="reveal = !reveal"
            >
              {{ reveal ? '隐藏' : '显示' }}
            </button></span
          ></label
        >
        <label v-if="registering" class="auth-field" for="confirm-password"
          >确认密码<input
            id="confirm-password"
            v-model="confirmPassword"
            :type="reveal ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            minlength="8"
            maxlength="72"
            required
            :disabled="busy"
        /></label>
        <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
        <button class="auth-submit" :disabled="busy" type="submit">
          <span>{{ busy ? '正在为你准备…' : registering ? '注册并开启学习' : '登录学习空间' }}</span
          ><AppIcon v-if="!busy" name="arrow" :size="18" /><span
            v-else
            class="auth-spinner"
            aria-hidden="true"
          ></span>
        </button>
      </form>
      <p class="auth-security"><AppIcon name="success" :size="14" />安心连接，守护每一次成长</p>
      <button type="button" class="auth-demo" :disabled="busy" @click="demo">
        先逛逛学习空间 <span aria-hidden="true">↗</span>
      </button>
    </section>
    <footer class="auth-footer">
      <span class="auth-footer-dot"></span>不必光芒万丈，只要一直向前。<span
        >研途 AI · 你的考研成长伙伴</span
      >
    </footer>
  </main>
</template>

<style scoped>
.auth-page {
  --leaf: #397956;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100svh;
  overflow: hidden;
  isolation: isolate;
  padding: 32px 24px !important;
  max-width: none !important;
  color: #244a37;
  background:
    radial-gradient(ellipse at 15% 18%, #dcecd8 0, transparent 48%),
    radial-gradient(ellipse at 87% 86%, #d6e9cb 0, transparent 48%), #f0f5e9;
}
.auth-page::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  opacity: 0.18;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 180 180' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Cpath fill='%23849370' opacity='.15' filter='url(%23n)' d='M0 0h180v180H0z'/%3E%3C/svg%3E");
  pointer-events: none;
}
.auth-brand {
  position: absolute;
  top: 34px;
  left: 42px;
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
}
.auth-brand-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  color: #f5f8eb;
  border-radius: 14px;
  background: #427954;
}
.brand-caption {
  margin-left: 8px;
  border-left: 1px solid #b8ccb2;
  padding-left: 18px;
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 2px;
  color: #6d856c;
}
.auth-card {
  z-index: 1;
  width: min(100%, 450px);
  border: 1px solid #ffffffdc;
  border-radius: 28px;
  padding: 36px 42px 27px;
  background: #ffffffe0;
  backdrop-filter: blur(20px);
  box-shadow:
    0 25px 80px #496a3620,
    0 3px 10px #72916408;
  text-align: center;
}
.auth-emblem {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  margin: 0 auto 18px;
  color: #608657;
  background: linear-gradient(140deg, #f0f5df, #deebd8);
  border: 1px solid #e1eacb;
  border-radius: 19px;
  transform: rotate(-6deg);
}
.auth-eyebrow {
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 2.4px;
  color: #8a9d7c;
  margin-bottom: 8px;
}
.auth-card h1 {
  font-size: 25px;
  font-weight: 600;
  letter-spacing: -1px;
  line-height: 1.5;
}
.auth-subtitle {
  font-size: 12px;
  color: #7d8d7b;
  margin-top: 9px;
}
.auth-tabs {
  display: flex;
  margin: 27px 0 24px;
  padding: 4px;
  border: 1px solid #e7ecdf;
  background: #f0f3e9;
  border-radius: 12px;
}
.auth-tabs button {
  flex: 1;
  padding: 10px;
  border-radius: 8px;
  color: #7d8d75;
  background: transparent;
  font-size: 13px;
  transition:
    background 0.2s,
    box-shadow 0.2s;
}
.auth-tabs button.active {
  color: #397956;
  background: #fff;
  box-shadow: 0 2px 5px #46633312;
  font-weight: 600;
}
.auth-form {
  display: grid;
  gap: 17px;
  text-align: left;
}
.auth-field {
  display: grid;
  gap: 8px;
  font-size: 12px;
  font-weight: 500;
  color: #4b6249;
}
.auth-field input {
  width: 100%;
  min-width: 0;
  height: 47px;
  padding: 0 14px;
  border: 1px solid #dfe6d8;
  border-radius: 10px;
  background: #fcfdf9;
  color: #284b35;
  outline: none;
  font-size: 13px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}
.auth-field input::placeholder {
  color: #a4af9b;
  font-size: 12px;
}
.auth-field input:focus {
  border-color: #84ac73;
  box-shadow: 0 0 0 3px #8cad7920;
}
.password-wrap {
  position: relative;
}
.password-wrap input {
  padding-right: 60px;
}
.password-wrap button {
  position: absolute;
  right: 12px;
  top: 15px;
  background: transparent;
  font-size: 11px;
  color: #71876a;
  padding: 0;
}
.auth-submit {
  margin-top: 5px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
  min-height: 48px;
  border-radius: 11px;
  background: #427c56;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 6px 15px #467a4920;
  transition:
    background 0.2s,
    transform 0.2s;
}
.auth-submit:hover:not(:disabled) {
  background: #326944;
  transform: translateY(-1px);
}
.auth-page button:focus-visible,
.auth-page a:focus-visible {
  outline: 3px solid #7da36c;
  outline-offset: 4px;
}
.auth-error {
  color: #a33b3b;
  background: #fff1ed;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 12px;
}
.auth-security {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  color: #96a18e;
  font-size: 10px;
  margin: 16px 0 19px;
}
.auth-demo {
  background: transparent;
  color: #7a8d6c;
  font-size: 11px;
  padding: 8px 16px;
  border-top: 1px solid #edf0e7;
  width: 100%;
}
.auth-demo span {
  margin-left: 7px;
}
.auth-footer {
  position: absolute;
  bottom: 20px;
  right: 30px;
  max-width: 290px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 7px;
  color: #7d9171;
  font-size: 11px;
  letter-spacing: 1px;
}
.auth-footer > span:last-child {
  flex-basis: 100%;
  text-align: center;
  color: #9ba990;
  font-size: 9px;
  margin-top: 4px;
  letter-spacing: 1.6px;
}
.auth-footer-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #8ba568;
}
.auth-glow {
  position: absolute;
  z-index: -1;
  width: 48vw;
  height: 48vw;
  border-radius: 50%;
  filter: blur(65px);
  pointer-events: none;
}
.glow-one {
  left: -22vw;
  bottom: -12vw;
  background: #abc88c30;
}
.glow-two {
  right: -20vw;
  top: -12vw;
  background: #d7e4aa55;
}
.firefly-field {
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
}
.firefly {
  position: absolute;
  width: var(--size);
  height: var(--size);
  border-radius: 50%;
  background: #f7ffd0;
  box-shadow:
    0 0 12px 4px #d5ed8b90,
    0 0 30px 8px #c1dd7040;
  filter: blur(1.4px);
  opacity: 0;
  animation: firefly-drift var(--duration) var(--delay) infinite ease-in-out;
}
@keyframes firefly-drift {
  0%,
  100% {
    opacity: 0.08;
    transform: translate3d(0, 30px, 0) scale(0.7);
  }
  30% {
    opacity: 0.85;
  }
  50% {
    opacity: 0.3;
    transform: translate3d(var(--drift), -65px, 0) scale(1.15);
  }
  75% {
    opacity: 0.9;
  }
}
.auth-spinner {
  width: 15px;
  height: 15px;
  border: 2px solid #ffffff50;
  border-top-color: white;
  border-radius: 50%;
  animation: auth-spin 0.8s linear infinite;
}
@keyframes auth-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 520px) {
  .auth-page {
    padding: 54px 18px !important;
  }
  .auth-brand {
    left: 24px;
    top: 12px;
    font-size: 18px;
  }
  .brand-caption {
    font-size: 10px;
    padding-left: 12px;
    margin-left: 2px;
    letter-spacing: 1px;
  }
  .auth-card {
    padding: 22px 25px 16px;
    border-radius: 23px;
  }
  .auth-card h1 {
    font-size: 23px;
  }
  .auth-brand-icon {
    width: 32px;
    height: 32px;
    border-radius: 11px;
  }
  .auth-emblem {
    width: 44px;
    height: 44px;
    margin-bottom: 12px;
  }
  .auth-tabs {
    margin: 20px 0 18px;
  }
  .auth-form {
    gap: 14px;
  }
  .auth-security {
    margin: 12px 0;
  }
  .auth-footer {
    bottom: 12px;
    right: auto;
    max-width: calc(100% - 36px);
    font-size: 10px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .firefly,
  .auth-spinner {
    animation: none;
    opacity: 0.45;
  }
  .auth-submit {
    transition: none;
  }
}
</style>

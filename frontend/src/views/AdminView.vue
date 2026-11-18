<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { request } from '../api/client';
import { useAppStore } from '../stores/app';

type UserRow = { id: string; email?: string; displayName: string; status: string; roles: string[]; deliveryStatus?: string;
  legacyLogin?: string; teacherLogin?: string; verified?: boolean };
type Role = { code: string; name: string; permissions: string[] };
type Permission = { code: string; name: string; category: string; description: string };
type Audit = { id: string; actorId?: string; action: string; target: string; occurredAt: string;
  reason: string; traceId: string; before?: string; after?: string };
const app = useAppStore();
const has = (permission: string) => app.user?.permissions?.includes(permission) === true;
const roles = ['STUDENT', 'TEACHER', 'ADMIN'];
const users = ref<UserRow[]>([]);
const selected = ref<UserRow>();
const catalog = ref<Role[]>([]);
const permissions = ref<Permission[]>([]);
const logs = ref<Audit[]>([]);
const panel = ref('users');
const reason = ref('');
const reasonInput = ref<HTMLInputElement | null>(null);
const search = ref('');
const roleFilter = ref('');
const name = ref('');
const email = ref('');
const candidate = ref('');
const announcement = ref('');
const page = ref(0);
const error = ref('');
const notice = ref('');
const busy = ref(false);
const items = async <T,>(path: string): Promise<T[]> => (await request(path) as { items: T[] }).items;
const mutate = (path: string, method: string, body: object = {}) =>
  request(path, { method, body: { ...body, reason: reason.value.trim() } });
const userPath = (id: string) => '/admin/users/' + encodeURIComponent(id);
const invitationLabels: Record<string, string> = {
  PENDING: '邀请正在发送',
  SENT: '邮件服务已接收',
  FAILED: '邀请发送失败，可重发',
};
async function load() {
  if (panel.value === 'users' && has('user:list')) users.value = await items('/admin/users?' +
    new URLSearchParams({ email: search.value, role: roleFilter.value, page: String(page.value) }));
  if (panel.value === 'legacy') users.value = await items('/admin/users/legacy');
  if (panel.value === 'roles') {
    if (has('role:list')) catalog.value = await items('/admin/roles');
    if (has('permission:list')) permissions.value = await items('/admin/permissions');
  }
  if (panel.value === 'audit') logs.value = await items('/admin/audit?page=' + page.value);
  if (panel.value === 'settings') announcement.value = (await request('/admin/settings') as { announcement: string }).announcement;
}
async function run(operation: () => Promise<unknown>, mutation = false, success = '操作已完成。') {
  if (busy.value) return;
  error.value = '';
  notice.value = '';
  if (mutation && !reason.value.trim()) {
    error.value = '请填写操作原因后再提交。';
    reasonInput.value?.focus();
    return;
  }
  busy.value = true;
  try {
    await operation();
    if (mutation) { notice.value = success; await load(); }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败';
    if (mutation && (panel.value === 'users' || panel.value === 'legacy')) {
      try { await load(); } catch { /* keep original error */ }
    }
  } finally { busy.value = false; }
}
function sendTeacherInvitation() {
  return run(async () => {
    await mutate('/admin/teachers', 'POST', { email: email.value, displayName: name.value });
    email.value = '';
    name.value = '';
  }, true, '老师账号已创建，邮件服务已接收邀请。请老师查收邮件并激活账号。');
}
function selectPanel(value: string) {
  panel.value = value;
  page.value = 0;
  selected.value = undefined;
  void run(load);
}
function detail(user: UserRow) { return run(async () => { selected.value = await request(userPath(user.id)) as UserRow; }); }
function findRole(role: string) {
  roleFilter.value = role;
  selectPanel('users');
}
onMounted(() => {
  const initial = has('user:list') || has('teacher:create') ? 'users' : has('user:update') ? 'legacy'
    : has('role:list') || has('permission:list') ? 'roles' : has('system:log:read') ? 'audit' : 'settings';
  if (initial !== 'settings' || has('system:config:manage')) selectPanel(initial);
});
</script>

<template>
  <section class="admin-workspace">
    <h1>平台管理</h1>
    <nav class="admin-actions" aria-label="管理模块">
      <button v-if="has('user:list') || has('teacher:create')" class="btn" type="button" @click="selectPanel('users')">用户与老师</button>
      <button v-if="has('user:update')" class="btn" type="button" @click="selectPanel('legacy')">历史账号</button>
      <button v-if="has('role:list') || has('permission:list')" class="btn" type="button" @click="selectPanel('roles')">角色与权限</button>
      <button v-if="has('system:log:read')" class="btn" type="button" @click="selectPanel('audit')">操作日志</button>
      <button v-if="has('system:config:manage')" class="btn" type="button" @click="selectPanel('settings')">系统配置</button>
    </nav>
    <label v-if="panel !== 'audit'">操作原因
      <input ref="reasonInput" v-model="reason" maxlength="250" placeholder="例如：新增考研指导老师" aria-required="true" />
    </label>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <template v-if="panel === 'users' || panel === 'legacy'">
      <form v-if="panel === 'users' && has('teacher:create')" class="identity-card" @submit.prevent="sendTeacherInvitation">
        <h2>发放老师账号</h2>
        <label>老师姓名<input v-model="name" required maxlength="32" /></label>
        <label>老师邮箱<input v-model="email" type="email" required /></label>
        <button class="btn primary" :disabled="busy">创建并发送邀请</button>
      </form>
      <form v-if="panel === 'users' && has('user:list')" class="admin-actions" @submit.prevent="page = 0; run(load)">
        <label>邮箱搜索 <input v-model="search" /></label>
        <label>角色 <select v-model="roleFilter"><option value="">全部</option><option v-for="role in roles" :key="role">{{ role }}</option></select></label>
        <button class="btn" :disabled="busy">查询</button>
      </form>
      <article v-for="user in users" :key="user.id" class="identity-card">
        <strong>{{ user.displayName || user.teacherLogin || user.legacyLogin || user.id }}</strong>
        <span>{{ user.email }} {{ user.status === 'PENDING_ACTIVATION' ? '待激活' : user.status }} {{ user.roles.join('、') }}</span>
        <span v-if="user.deliveryStatus">{{ invitationLabels[user.deliveryStatus] ?? user.deliveryStatus }}</span>
        <span v-else-if="user.status === 'PENDING_ACTIVATION' && !user.email">尚未绑定邮箱，可在「历史账号」发起认领。</span>
        <div class="admin-actions">
          <button v-if="has('user:list')" class="btn" @click="detail(user)">详情</button>
          <button v-if="has('user:disable') && user.status === 'ENABLED'" class="btn" @click="run(() =>
            mutate(userPath(user.id) + '/disable', 'POST'), true)">禁用</button>
          <button v-if="has('user:enable') && user.status === 'DISABLED' && user.verified" class="btn" @click="run(() =>
            mutate(userPath(user.id) + '/enable', 'POST'), true)">恢复</button>
          <template v-if="user.deliveryStatus && user.status === 'PENDING_ACTIVATION'">
            <button v-if="has('teacher:invite') && has('user:update')" class="btn" @click="run(() =>
              mutate(userPath(user.id) + '/invitation/resend', 'POST'), true, '邮件服务已接收重发的邀请，请使用最新邮件中的链接。')">重发邀请</button>
            <button v-if="has('teacher:manage') && has('user:update')" class="btn" @click="run(() =>
              mutate(userPath(user.id) + '/invitation/cancel', 'POST'), true)">取消邀请</button>
          </template>
          <button v-if="panel === 'legacy'" class="btn" @click="selected = user; candidate = ''">发起邮箱认领</button>
        </div>
      </article>
      <div v-if="panel === 'users' && has('user:list')" class="admin-actions">
        <button class="btn" :disabled="busy || page === 0" @click="page--; run(load)">上一页</button>
        <button class="btn" :disabled="busy || users.length < 50" @click="page++; run(load)">下一页</button>
      </div>
      <article v-if="selected" class="identity-card">
        <h2>{{ selected.displayName || selected.legacyLogin || selected.id }}</h2>
        <p>用户 ID {{ selected.id }} · {{ selected.email || '待绑定邮箱' }} · {{ selected.status }}</p>
        <form v-if="panel === 'legacy'" @submit.prevent="run(() =>
          mutate(userPath(selected!.id) + '/legacy-binding', 'POST', { email: candidate }), true)">
          <label>待验证邮箱<input v-model="candidate" type="email" required /></label><button class="btn primary">发送认领邀请</button>
        </form>
        <template v-else>
          <form v-if="has('user:update')" @submit.prevent="run(() =>
            mutate(userPath(selected!.id), 'PATCH', { displayName: selected!.displayName }), true)">
            <label>姓名<input v-model="selected.displayName" required maxlength="32" /></label><button class="btn">保存姓名</button>
          </form>
          <form v-if="has('role:assign') && selected.verified" @submit.prevent="run(() =>
            mutate(userPath(selected!.id) + '/roles', 'PUT', { roles: selected!.roles }), true)">
            <fieldset><legend>用户角色</legend>
              <label v-for="role in roles" :key="role"><input v-model="selected.roles" type="checkbox" :value="role" /> {{ role }}</label>
            </fieldset><button class="btn">保存角色</button>
          </form>
        </template>
      </article>
    </template>
    <template v-if="panel === 'roles'">
      <article v-for="role in catalog" :key="role.code" class="identity-card">
        <h2>{{ role.code }}</h2>
        <label>角色名称<input v-model="role.name" :readonly="!has('role:update')" /></label>
        <div class="admin-actions">
          <button v-if="has('role:update')" class="btn" @click="run(() => mutate('/admin/roles/' + role.code, 'PATCH', { name: role.name }), true)">保存名称</button>
          <button v-if="has('user:list')" class="btn" @click="findRole(role.code)">查看角色成员</button>
        </div>
        <fieldset><legend>角色权限</legend>
          <label v-for="permission in permissions" :key="permission.code" :title="permission.description">
            <input v-model="role.permissions" type="checkbox" :value="permission.code" :disabled="!has('permission:assign')" />
            {{ permission.name }} · {{ permission.code }}
          </label>
        </fieldset>
        <button v-if="has('permission:assign') && permissions.length" class="btn primary" @click="run(() =>
          mutate('/admin/roles/' + role.code + '/permissions', 'PUT', { permissions: role.permissions }), true)">保存权限</button>
      </article>
      <details><summary>权限目录</summary><p v-for="permission in permissions" :key="permission.code">
        {{ permission.category }} · {{ permission.name }} · {{ permission.code }} · {{ permission.description }}
      </p></details>
    </template>
    <template v-if="panel === 'audit'">
      <article v-for="entry in logs" :key="entry.id" class="identity-card">
        <strong>{{ entry.action }} · {{ entry.occurredAt }}</strong>
        <span>操作人 {{ entry.actorId || '初始化' }} · 对象 {{ entry.target }} · 请求 {{ entry.traceId }}</span>
        <span>原因 {{ entry.reason }} · {{ entry.before || '空' }} → {{ entry.after || '空' }}</span>
      </article>
      <div class="admin-actions"><button class="btn" :disabled="busy || page === 0" @click="page--; run(load)">上一页</button>
        <button class="btn" :disabled="busy || logs.length < 50" @click="page++; run(load)">下一页</button></div>
    </template>
    <form v-if="panel === 'settings' && has('system:config:manage')" class="identity-card" @submit.prevent="run(() =>
      mutate('/admin/settings','PUT',{ announcement }),true)">
      <label>平台公告<textarea v-model="announcement" maxlength="500" /></label><button class="btn primary">保存配置</button>
    </form>
  </section>
</template>

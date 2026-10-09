<script setup lang="ts">
import { reactive, ref } from 'vue';
import { createIsmClient, type components } from '@ism/api-client';

const form = reactive({ username: '', password: '' });
const TOKEN_KEY = 'ism.console.accessToken';
const REQUIRED_KEY = 'ism.console.passwordChangeRequired';
const message = ref('');
const submitting = ref(false);
const changingPassword = ref(false);
const accessToken = ref<string | undefined>(sessionStorage.getItem(TOKEN_KEY) ?? undefined);
const passwordChangeRequired = ref(sessionStorage.getItem(REQUIRED_KEY) === 'true');
const passwordChangeRecommended = ref(false);
const showPasswordForm = ref(false);
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
const packages = ref<components['schemas']['PackagePlan'][]>([]);
const tenants = ref<components['schemas']['Tenant'][]>([]);
const currentUser = ref<components['schemas']['ConsoleUserSummary'] | null>(null);
const users = ref<components['schemas']['ConsoleUser'][]>([]);
const userForm = reactive({ username: '', displayName: '', initialPassword: '', roleCode: 'PLATFORM_SUPPORT' as 'PLATFORM_ADMIN' | 'PLATFORM_SUPPORT' });
const userBusy = ref(false);
const canManageUsers = () => currentUser.value?.permissions.includes('platform:user:manage') ?? false;
const client = createIsmClient({ getAccessToken: () => accessToken.value });

async function login() {
  submitting.value = true;
  message.value = '';
  try {
    const { data, error } = await client.POST('/console/auth/login', {
      body: { ...form, deviceId: globalThis.crypto.randomUUID() },
    });
    if (error) {
      message.value = error.error.message;
    } else {
      accessToken.value = data.data.accessToken;
      passwordChangeRequired.value = data.data.user.passwordChangeRequired;
      passwordChangeRecommended.value = data.data.user.passwordChangeRecommended;
      currentUser.value = data.data.user;
      showPasswordForm.value = passwordChangeRequired.value;
      sessionStorage.setItem(TOKEN_KEY, data.data.accessToken);
      sessionStorage.setItem(REQUIRED_KEY, String(passwordChangeRequired.value));
      message.value = `欢迎进入平台控制台，${data.data.user.displayName}`;
      if (!passwordChangeRequired.value) await loadWorkspace();
    }
  } catch {
    message.value = '登录失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}

async function loadWorkspace() {
  if (!accessToken.value || passwordChangeRequired.value) return;
  const [meResult, packageResult, tenantResult] = await Promise.all([
    client.GET('/console/auth/me'), client.GET('/console/packages'), client.GET('/console/tenants'),
  ]);
  if (meResult.error || packageResult.error || tenantResult.error) {
    logout('登录状态已失效，请重新登录');
    return;
  }
  packages.value = packageResult.data?.data ?? [];
  tenants.value = tenantResult.data?.data ?? [];
  currentUser.value = meResult.data?.data ?? null;
  if (currentUser.value?.permissions.includes('platform:user:view')) await loadUsers();
}

async function loadUsers() {
  const { data, error } = await client.GET('/console/users');
  if (error) { message.value = error.error.message; return; }
  users.value = data.data;
}

async function createUser() {
  userBusy.value = true;
  try {
    const { error } = await client.POST('/console/users', {
      params: { header: { 'Idempotency-Key': globalThis.crypto.randomUUID() } }, body: { ...userForm },
    });
    if (error) { message.value = error.error.message; return; }
    userForm.username = ''; userForm.displayName = ''; userForm.initialPassword = '';
    message.value = '平台账号已创建，首次登录须修改密码';
    await loadUsers();
  } catch {
    message.value = '创建平台账号失败，请稍后重试';
  } finally { userBusy.value = false; }
}

async function changeUserStatus(user: components['schemas']['ConsoleUser']) {
  const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  if (next === 'DISABLED' && user.id === currentUser.value?.id) return;
  try {
    const { error } = await client.PUT('/console/users/{id}/status', {
      params: { path: { id: user.id }, header: { 'Idempotency-Key': globalThis.crypto.randomUUID() } },
      body: { status: next, version: user.version },
    });
    if (error) { message.value = error.error.message; return; }
    message.value = next === 'ACTIVE' ? '账号已恢复' : '账号已停用';
    await loadUsers();
  } catch { message.value = '修改账号状态失败，请稍后重试'; }
}

async function changePassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    message.value = '两次输入的新密码不一致';
    return;
  }
  changingPassword.value = true;
  try {
    const { error } = await client.POST('/console/auth/change-password', {
      body: { oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword },
    });
    if (error) {
      message.value = error.error.message;
      return;
    }
    logout('密码已修改，请重新登录');
    passwordForm.oldPassword = '';
    passwordForm.newPassword = '';
    passwordForm.confirmPassword = '';
  } catch {
    message.value = '修改密码失败，请稍后重试';
  } finally {
    changingPassword.value = false;
  }
}

function logout(reason = '') {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(REQUIRED_KEY);
  accessToken.value = undefined;
  passwordChangeRequired.value = false;
  passwordChangeRecommended.value = false;
  showPasswordForm.value = false;
  form.password = '';
  passwordForm.oldPassword = '';
  passwordForm.newPassword = '';
  passwordForm.confirmPassword = '';
  packages.value = [];
  tenants.value = [];
  users.value = [];
  currentUser.value = null;
  message.value = reason;
}

if (accessToken.value) {
  message.value = '已恢复当前登录会话';
  showPasswordForm.value = passwordChangeRequired.value;
  if (!passwordChangeRequired.value) void loadWorkspace();
}
</script>

<template>
  <main v-if="!accessToken" class="shell">
    <section class="context">
      <p class="eyebrow">CONTROL PLANE</p>
      <h1>工业供应商管理平台</h1>
      <p>管理租户、套餐和平台能力。Console 不直接展示租户业务正文。</p>
    </section>
    <form class="login-card" @submit.prevent="login">
      <p class="eyebrow">PLATFORM CONSOLE</p>
      <h2>平台人员登录</h2>
      <label>平台用户名<input v-model="form.username" name="username" autocomplete="username" required /></label>
      <label>密码<input v-model="form.password" name="password" type="password" autocomplete="current-password" required /></label>
      <button :disabled="submitting" type="submit">{{ submitting ? '验证中…' : '进入 Console' }}</button>
      <p v-if="message" role="status">{{ message }}</p>
    </form>
  </main>
  <main v-else-if="passwordChangeRequired" class="shell">
    <section class="context"><p class="eyebrow">PLATFORM CONSOLE</p><h1>需要修改密码</h1><p>当前账号必须先修改密码，才能访问平台控制台。</p></section>
    <form class="login-card" @submit.prevent="changePassword">
      <h2>修改登录密码</h2>
      <label>当前密码<input v-model="passwordForm.oldPassword" type="password" autocomplete="current-password" required /></label>
      <label>新密码<input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" required minlength="12" /></label>
      <label>确认新密码<input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" required minlength="12" /></label>
      <p>至少 12 位，包含大小写字母、数字和特殊字符。</p>
      <button :disabled="changingPassword" type="submit">确认修改</button>
      <button class="secondary-button" type="button" @click="logout()">退出登录</button>
      <p v-if="message" role="status">{{ message }}</p>
    </form>
  </main>
  <main v-else class="workspace">
    <header><div><p class="eyebrow">PLATFORM CONSOLE</p><h1>套餐与模块</h1></div><div><span role="status">{{ message }}</span><button type="button" @click="showPasswordForm=!showPasswordForm">修改密码</button><button type="button" @click="logout()">退出登录</button></div></header>
    <section v-if="passwordChangeRecommended" class="password-notice" role="alert">此账号长期未登录，建议修改密码。<button type="button" @click="showPasswordForm=true">立即修改</button><button type="button" @click="passwordChangeRecommended=false">稍后再说</button></section>
    <form v-if="showPasswordForm" class="panel password-form" @submit.prevent="changePassword">
      <h2>修改登录密码</h2>
      <label>当前密码<input v-model="passwordForm.oldPassword" type="password" autocomplete="current-password" required /></label>
      <label>新密码<input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" required minlength="12" /></label>
      <label>确认新密码<input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" required minlength="12" /></label>
      <p>至少 12 位，包含大小写字母、数字和特殊字符。</p>
      <button :disabled="changingPassword" type="submit">确认修改</button>
    </form>
    <section class="panel">
      <div class="panel-title"><div><h2>套餐版本</h2><p>发布后的版本不可覆盖，变更必须创建新版本。</p></div><button type="button">新建套餐</button></div>
      <table><thead><tr><th>套餐编码</th><th>套餐名称</th><th>状态</th><th>版本数</th></tr></thead>
        <tbody><tr v-for="item in packages" :key="item.id"><td>{{ item.code }}</td><td>{{ item.name }}</td><td>{{ item.status }}</td><td>{{ item.versions.length }}</td></tr>
        <tr v-if="packages.length === 0"><td colspan="4" class="empty">尚未创建套餐</td></tr></tbody>
      </table>
    </section>
    <section class="panel">
      <div class="panel-title"><div><h2>租户开通</h2><p>仅显示控制面摘要，不展示租户业务正文。</p></div><button type="button">新建租户</button></div>
      <table><thead><tr><th>租户编码</th><th>租户名称</th><th>运行状态</th><th>初始化状态</th></tr></thead>
        <tbody><tr v-for="tenant in tenants" :key="tenant.id"><td>{{ tenant.code }}</td><td>{{ tenant.name }}</td><td>{{ tenant.status }}</td><td>{{ tenant.initializationStatus }}</td></tr>
        <tr v-if="tenants.length === 0"><td colspan="4" class="empty">尚未创建租户</td></tr></tbody>
      </table>
    </section>
    <section v-if="currentUser?.permissions.includes('platform:user:view')" class="panel">
      <div class="panel-title"><div><h2>平台账号</h2><p>平台账号与租户用户完全隔离；停用会使既有会话失效。</p></div></div>
      <form v-if="canManageUsers()" class="console-user-form" @submit.prevent="createUser">
        <label>平台用户名<input v-model="userForm.username" required pattern="[a-zA-Z][a-zA-Z0-9._-]{2,99}" /></label>
        <label>显示名称<input v-model="userForm.displayName" required maxlength="100" /></label>
        <label>初始密码<input v-model="userForm.initialPassword" required type="password" minlength="12" autocomplete="new-password" /></label>
        <label>平台角色<select v-model="userForm.roleCode"><option value="PLATFORM_SUPPORT">支持人员</option><option value="PLATFORM_ADMIN">运营管理员</option></select></label>
        <button :disabled="userBusy" type="submit">创建平台账号</button>
      </form>
      <table><thead><tr><th>用户名</th><th>显示名称</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="user in users" :key="user.id"><td>{{ user.username }}</td><td>{{ user.displayName }}</td><td>{{ user.roleCodes.join('、') }}</td><td>{{ user.status === 'ACTIVE' ? '启用' : '停用' }}</td>
          <td><button v-if="canManageUsers()" type="button" :disabled="user.id === currentUser?.id" @click="changeUserStatus(user)">{{ user.status === 'ACTIVE' ? '停用' : '恢复' }}</button></td></tr>
        <tr v-if="users.length === 0"><td colspan="5" class="empty">尚无平台账号</td></tr></tbody>
      </table>
    </section>
  </main>
</template>

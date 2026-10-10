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
const roleDrafts = reactive<Record<string, 'PLATFORM_ADMIN' | 'PLATFORM_SUPPORT'>>({});
const resetTarget = ref<components['schemas']['ConsoleUser'] | null>(null);
const lockTarget = ref<components['schemas']['ConsoleUser'] | null>(null);
const lockReason = ref('');
const temporaryPassword = ref('');
const resetBusy = ref(false);
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
  for (const user of users.value) roleDrafts[user.id] = user.roleCodes.includes('PLATFORM_ADMIN') ? 'PLATFORM_ADMIN' : 'PLATFORM_SUPPORT';
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

async function changeUserRole(user: components['schemas']['ConsoleUser']) {
  const roleCode = roleDrafts[user.id];
  if (!roleCode || user.id === currentUser.value?.id || user.roleCodes.includes(roleCode)) return;
  try {
    const { error } = await client.PUT('/console/users/{id}/role', {
      params: { path: { id: user.id }, header: { 'Idempotency-Key': globalThis.crypto.randomUUID() } },
      body: { roleCode, version: user.version },
    });
    if (error) { message.value = error.error.message; return; }
    message.value = '平台角色已调整，目标账号需要重新登录';
    await loadUsers();
  } catch { message.value = '调整平台角色失败，请稍后重试'; }
}

async function resetUserPassword() {
  const target = resetTarget.value;
  if (!target || target.id === currentUser.value?.id) return;
  resetBusy.value = true;
  try {
    const { error } = await client.PUT('/console/users/{id}/password-reset', {
      params: { path: { id: target.id }, header: { 'Idempotency-Key': globalThis.crypto.randomUUID() } },
      body: { temporaryPassword: temporaryPassword.value, version: target.version },
    });
    if (error) { message.value = error.error.message; return; }
    temporaryPassword.value = '';
    resetTarget.value = null;
    message.value = '临时密码已设置；请通过安全渠道交付，目标账号下次登录须改密';
    await loadUsers();
  } catch { message.value = '重置密码失败，请稍后重试'; }
  finally { resetBusy.value = false; }
}

async function changeUserLoginLock() {
  const target = lockTarget.value;
  if (!target || target.id === currentUser.value?.id || !lockReason.value.trim()) return;
  try {
    const { error } = await client.PUT('/console/users/{id}/login-lock', {
      params: { path: { id: target.id }, header: { 'Idempotency-Key': globalThis.crypto.randomUUID() } },
      body: { locked: !target.manualLocked && !target.automaticLockedUntil, reason: lockReason.value.trim(), version: target.version },
    });
    if (error) { message.value = error.error.message; return; }
    message.value = target.manualLocked || target.automaticLockedUntil ? '登录锁定已解除' : '账号登录已锁定，既有会话失效';
    lockTarget.value = null; lockReason.value = '';
    await loadUsers();
  } catch { message.value = '调整登录锁定失败，请稍后重试'; }
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
  resetTarget.value = null;
  temporaryPassword.value = '';
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
        <tbody><tr v-for="user in users" :key="user.id"><td>{{ user.username }}</td><td>{{ user.displayName }}</td>
          <td><div v-if="canManageUsers()" class="account-role"><select v-model="roleDrafts[user.id]" :aria-label="`${user.username} 平台角色`" :disabled="user.id === currentUser?.id"><option value="PLATFORM_SUPPORT">支持人员</option><option value="PLATFORM_ADMIN">运营管理员</option></select>
            <button type="button" :disabled="user.id === currentUser?.id || user.roleCodes.includes(roleDrafts[user.id])" @click="changeUserRole(user)">保存角色</button></div><span v-else>{{ user.roleCodes.join('、') }}</span></td>
          <td>{{ user.status === 'ACTIVE' ? '启用' : '停用' }}<span v-if="user.manualLocked"> · 人工锁定</span><span v-else-if="user.automaticLockedUntil"> · 登录失败暂锁</span></td>
          <td><div v-if="canManageUsers()" class="account-actions"><button type="button" :disabled="user.id === currentUser?.id" @click="changeUserStatus(user)">{{ user.status === 'ACTIVE' ? '停用' : '恢复' }}</button>
            <button type="button" :disabled="user.id === currentUser?.id" @click="resetTarget=user;temporaryPassword=''">重置密码</button>
            <button type="button" :disabled="user.id === currentUser?.id" @click="lockTarget=user;lockReason=''">{{ user.manualLocked || user.automaticLockedUntil ? '解除锁定' : '锁定登录' }}</button></div></td></tr>
        <tr v-if="users.length === 0"><td colspan="5" class="empty">尚无平台账号</td></tr></tbody>
      </table>
      <form v-if="resetTarget" class="password-reset-form" @submit.prevent="resetUserPassword">
        <h3>重置 {{ resetTarget.username }} 的密码</h3><p>至少 12 位，包含大小写字母、数字和特殊字符。页面不会回显保存，须通过安全渠道交付本人。</p>
        <label>临时密码<input v-model="temporaryPassword" type="password" autocomplete="new-password" required minlength="12" maxlength="128" /></label>
        <button :disabled="resetBusy" type="submit">确认重置</button><button type="button" class="secondary-button" @click="resetTarget=null;temporaryPassword=''">取消</button>
      </form>
      <form v-if="lockTarget" class="password-reset-form" @submit.prevent="changeUserLoginLock">
        <h3>{{ lockTarget.manualLocked || lockTarget.automaticLockedUntil ? '解除' : '锁定' }} {{ lockTarget.username }} 的登录</h3>
        <p v-if="lockTarget.manualLockReason">现有原因：{{ lockTarget.manualLockReason }}</p>
        <label>操作原因<input v-model="lockReason" required maxlength="500" /></label>
        <button type="submit">确认</button><button type="button" class="secondary-button" @click="lockTarget=null;lockReason=''">取消</button>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { createIsmClient, type components } from '@ism/api-client';

const form = reactive({ username: '', password: '' });
const TOKEN_KEY = 'ism.console.accessToken';
const message = ref('');
const submitting = ref(false);
const accessToken = ref<string | undefined>(sessionStorage.getItem(TOKEN_KEY) ?? undefined);
const packages = ref<components['schemas']['PackagePlan'][]>([]);
const tenants = ref<components['schemas']['Tenant'][]>([]);
const client = createIsmClient({ getAccessToken: () => accessToken.value });

async function login() {
  submitting.value = true;
  message.value = '';
  const { data, error } = await client.POST('/console/auth/login', {
    body: { ...form, deviceId: globalThis.crypto.randomUUID() },
  });
  if (error) {
    message.value = error.error.message;
  } else {
    accessToken.value = data.data.accessToken;
    sessionStorage.setItem(TOKEN_KEY, data.data.accessToken);
    message.value = `欢迎进入平台控制台，${data.data.user.displayName}`;
    await loadWorkspace();
  }
  submitting.value = false;
}

async function loadWorkspace() {
  const [packageResult, tenantResult] = await Promise.all([
    client.GET('/console/packages'), client.GET('/console/tenants'),
  ]);
  if (packageResult.error || tenantResult.error) {
    logout('登录状态已失效，请重新登录');
    return;
  }
  packages.value = packageResult.data?.data ?? [];
  tenants.value = tenantResult.data?.data ?? [];
}

function logout(reason = '') {
  sessionStorage.removeItem(TOKEN_KEY);
  accessToken.value = undefined;
  packages.value = [];
  tenants.value = [];
  message.value = reason;
}

if (accessToken.value) {
  message.value = '已恢复当前登录会话';
  void loadWorkspace();
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
  <main v-else class="workspace">
    <header><div><p class="eyebrow">PLATFORM CONSOLE</p><h1>套餐与模块</h1></div><div><span role="status">{{ message }}</span><button type="button" @click="logout()">退出登录</button></div></header>
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
  </main>
</template>

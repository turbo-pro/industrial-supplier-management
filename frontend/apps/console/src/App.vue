<script setup lang="ts">
import { reactive, ref } from 'vue';
import { createIsmClient, type components } from '@ism/api-client';

const form = reactive({ username: '', password: '' });
const message = ref('');
const submitting = ref(false);
const accessToken = ref<string>();
const packages = ref<components['schemas']['PackagePlan'][]>([]);
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
    message.value = `欢迎进入平台控制台，${data.data.user.displayName}`;
    const result = await client.GET('/console/packages');
    packages.value = result.data?.data ?? [];
  }
  submitting.value = false;
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
    <header><div><p class="eyebrow">PLATFORM CONSOLE</p><h1>套餐与模块</h1></div><span role="status">{{ message }}</span></header>
    <section class="panel">
      <div class="panel-title"><div><h2>套餐版本</h2><p>发布后的版本不可覆盖，变更必须创建新版本。</p></div><button type="button">新建套餐</button></div>
      <table><thead><tr><th>套餐编码</th><th>套餐名称</th><th>状态</th><th>版本数</th></tr></thead>
        <tbody><tr v-for="item in packages" :key="item.id"><td>{{ item.code }}</td><td>{{ item.name }}</td><td>{{ item.status }}</td><td>{{ item.versions.length }}</td></tr>
        <tr v-if="packages.length === 0"><td colspan="4" class="empty">尚未创建套餐</td></tr></tbody>
      </table>
    </section>
  </main>
</template>

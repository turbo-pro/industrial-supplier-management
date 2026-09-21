<script setup lang="ts">
import { reactive, ref } from 'vue';
import { createIsmClient } from '@ism/api-client';

const form = reactive({ tenantCode: '', username: '', password: '' });
const message = ref('');
const submitting = ref(false);
const client = createIsmClient();

async function login() {
  submitting.value = true;
  message.value = '';
  const { data, error } = await client.POST('/auth/login', {
    body: { ...form, deviceId: globalThis.crypto.randomUUID() },
  });
  message.value = error ? error.error.message : `欢迎，${data.data.user.displayName}`;
  submitting.value = false;
}
</script>

<template>
  <main class="shell">
    <form class="login-card" @submit.prevent="login">
      <p class="eyebrow">INDUSTRIAL SUPPLIER MANAGEMENT</p>
      <h1>供应商管理平台</h1>
      <p class="hint">请输入租户与账号信息</p>
      <label>租户编码<input v-model="form.tenantCode" name="tenantCode" required /></label>
      <label>用户名<input v-model="form.username" name="username" required /></label>
      <label>密码<input v-model="form.password" name="password" type="password" required /></label>
      <button :disabled="submitting" type="submit">{{ submitting ? '登录中…' : '登录' }}</button>
      <p v-if="message" role="status">{{ message }}</p>
    </form>
  </main>
</template>

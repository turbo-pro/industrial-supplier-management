<script setup lang="ts">
import { reactive, ref } from 'vue';
import { createIsmClient } from '@ism/api-client';

const form = reactive({ username: '', password: '' });
const message = ref('');
const submitting = ref(false);
const client = createIsmClient();

async function login() {
  submitting.value = true;
  message.value = '';
  const { data, error } = await client.POST('/console/auth/login', {
    body: { ...form, deviceId: globalThis.crypto.randomUUID() },
  });
  message.value = error ? error.error.message : `欢迎进入平台控制台，${data.data.user.displayName}`;
  submitting.value = false;
}
</script>

<template>
  <main class="shell">
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
</template>

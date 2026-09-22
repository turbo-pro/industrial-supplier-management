<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { createIsmClient, type components } from '@ism/api-client';

const form = reactive({ tenantCode: '', username: '', password: '' });
const message = ref('');
const submitting = ref(false);
const accessToken = ref<string>();
const organizations = ref<components['schemas']['OrganizationNode'][]>([]);
const currentOrganization = ref<components['schemas']['CurrentOrganization']>();
const menus = ref<components['schemas']['MenuNode'][]>([]);
const client = createIsmClient({ getAccessToken: () => accessToken.value });
const organizationOptions = computed(() => {
  const result: { id: string; label: string }[] = [];
  function walk(nodes: components['schemas']['OrganizationNode'][], depth = 0) {
    nodes.forEach(node => {
      if (node.status === 'ACTIVE') result.push({ id: node.id, label: `${'—'.repeat(depth)}${node.name}` });
      walk(node.children, depth + 1);
    });
  }
  walk(organizations.value);
  return result;
});

async function login() {
  submitting.value = true;
  message.value = '';
  const { data, error } = await client.POST('/auth/login', {
    body: { ...form, deviceId: globalThis.crypto.randomUUID() },
  });
  if (error) {
    message.value = error.error.message;
  } else {
    accessToken.value = data.data.accessToken;
    message.value = `欢迎，${data.data.user.displayName}`;
    const [tree, current, navigation] = await Promise.all([
      client.GET('/organizations/tree'), client.GET('/organizations/current'), client.GET('/navigation/menus'),
    ]);
    organizations.value = tree.data?.data ?? [];
    currentOrganization.value = current.data?.data;
    menus.value = navigation.data?.data ?? [];
  }
  submitting.value = false;
}

async function switchOrganization(event: Event) {
  const organizationId = (event.target as HTMLSelectElement).value;
  const result = await client.POST('/organizations/switch', { body: { organizationId } });
  if (result.data) currentOrganization.value = result.data.data;
}
</script>

<template>
  <main v-if="!accessToken" class="shell">
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
  <main v-else class="workspace">
    <header>
      <div><p class="eyebrow">INDUSTRIAL SUPPLIER MANAGEMENT</p><h1>供应商管理平台</h1></div>
      <label class="organization-switcher">当前组织
        <select :value="currentOrganization?.id" @change="switchOrganization">
          <option v-for="item in organizationOptions" :key="item.id" :value="item.id">{{ item.label }}</option>
        </select>
      </label>
    </header>
    <div class="workspace-grid">
      <nav class="navigation" aria-label="主菜单">
        <section v-for="menu in menus" :key="menu.id">
          <strong>{{ menu.name }}</strong>
          <a v-for="child in menu.children" :key="child.id" :href="child.route">{{ child.name }}</a>
        </section>
      </nav>
      <section class="summary-card">
      <p class="eyebrow">OPERATING CONTEXT</p>
      <h2>{{ currentOrganization?.name }}</h2>
      <p>当前数据录入默认归属此组织；实际可见范围仍由后端角色与数据权限决定。</p>
      <span role="status">{{ message }}</span>
      </section>
    </div>
  </main>
</template>

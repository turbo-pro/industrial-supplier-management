<script setup lang="ts">
import { reactive, ref } from 'vue';
import { createIsmClient, type components } from '@ism/api-client';
const props = defineProps<{ tenant: components['schemas']['Tenant']; token?: string }>();
const emit = defineEmits<{ close: []; done: [] }>();
const originalVersion = props.tenant.version;
const originalSite = props.tenant.defaultSiteId;
const form = reactive({ headquartersName: '' });
const busy = ref(false), unknown = ref(false), error = ref('');
let attempt: { key: string; body: components['schemas']['UpgradeTenantToMultiSite'] } | null = null;
const client = createIsmClient({ getAccessToken: () => props.token });
async function submit() {
  if (busy.value) return;
  attempt ??= { key: crypto.randomUUID(), body: { headquartersName: form.headquartersName, version: originalVersion } };
  busy.value = true; error.value = '';
  try {
    const result = await client.POST('/console/tenants/{id}/upgrade-to-multi-site', { params: { path: { id: props.tenant.id }, header: { 'Idempotency-Key': attempt.key } }, body: attempt.body });
    if (result.error) {
      unknown.value = result.response.status >= 500;
      error.value = unknown.value ? '服务响应未确认，请使用原请求重试。' : result.error.error.message;
      if (!unknown.value) attempt = null;
      return;
    }
    attempt = null; unknown.value = false; emit('done');
  } catch { unknown.value = true; error.value = '结果尚未确认，请使用原请求重试；请勿重复升级。'; }
  finally { busy.value = false; }
}
</script>
<template>
  <form class="panel site-upgrade" @submit.prevent="submit" aria-labelledby="upgrade-title">
    <h2 id="upgrade-title">升级多场站：{{ tenant.name }}</h2>
    <p>新增总部并将原场站挂到总部下。原场站、部门、历史业务及账号归属保持不变；此操作不提供多转单入口。</p>
    <p>保留场站 ID：<strong>{{ originalSite }}</strong>；本次租户版本：{{ originalVersion }}</p>
    <p>不新增账号组织归属或角色授权；“租户全部”等既有权限继续按原规则生效。后续新增场站需要单独配置。</p>
    <fieldset :disabled="busy || unknown">
      <label>新总部名称<input v-model="form.headquartersName" required maxlength="200" /></label>
    </fieldset>
    <p v-if="error" role="alert">{{ error }}</p>
    <button type="submit" :disabled="busy">{{ busy ? '升级中…' : unknown ? '使用原请求重试' : '确认升级为多场站' }}</button>
    <button type="button" class="secondary-button" :disabled="busy || unknown" @click="emit('close')">取消并刷新</button>
  </form>
</template>
<style scoped>
.site-upgrade { margin-top:20px; max-width:760px; }
fieldset { border:0; padding:0; min-width:0; }
input { width:100%; box-sizing:border-box; min-height:44px; }
button { width:auto; padding:0 18px; margin-right:12px; }
p { line-height:1.6; overflow-wrap:anywhere; }
[role=alert] { color:#a52828; }
</style>

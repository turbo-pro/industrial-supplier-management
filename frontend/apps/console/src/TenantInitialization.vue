<script setup lang="ts">
import { reactive, ref } from 'vue';
import { createIsmClient, type components } from '@ism/api-client';
const props = defineProps<{ tenant: components['schemas']['Tenant']; token?: string }>();
const emit = defineEmits<{ close: []; done: [] }>();
const form = reactive<components['schemas']['InitializeTenant']>({ adminUsername: '', adminDisplayName: '', initialPassword: '', operationMode: 'SINGLE_SITE', siteLabel: '工厂', siteName: '', headquartersName: '' });
const busy = ref(false), unknown = ref(false), error = ref('');
let attempt: { key: string; body: components['schemas']['InitializeTenant'] } | null = null;
const client = createIsmClient({ getAccessToken: () => props.token });
async function submit() {
  if (busy.value) return;
  attempt ??= { key: crypto.randomUUID(), body: { ...form } };
  busy.value = true; error.value = '';
  try {
    const result = await client.POST('/console/tenants/{id}/initialize', { params: { path: { id: props.tenant.id }, header: { 'Idempotency-Key': attempt.key } }, body: attempt.body });
    if (result.error) {
      error.value = result.error.error.message;
      unknown.value = result.response.status >= 500;
      if (unknown.value) error.value = '服务响应未确认，请使用原请求重试。'; else attempt = null;
      return;
    }
    form.initialPassword = ''; attempt = null; unknown.value = false; emit('done');
  } catch { unknown.value = true; error.value = '结果尚未确认，请使用原请求重试；请勿重复创建租户。'; }
  finally { busy.value = false; }
}
</script>
<template>
  <form class="panel tenant-initialization" @submit.prevent="submit" aria-labelledby="init-title">
    <h2 id="init-title">初始化租户：{{ tenant.name }}</h2>
    <p>选择组织结构并创建首管理员。总部是管理节点，现场业务属于经营场站。</p>
    <fieldset :disabled="busy || unknown">
      <label>运营模式<select v-model="form.operationMode"><option value="SINGLE_SITE">单场站</option><option value="MULTI_SITE">多场站</option></select></label>
      <p>{{ form.operationMode === 'SINGLE_SITE' ? '直接创建唯一根场站，不创建总部。' : '创建总部和首个直属场站，后续可新增场站。' }}</p>
      <label v-if="form.operationMode === 'MULTI_SITE'">总部名称<input v-model="form.headquartersName" required maxlength="200" /></label>
      <label>场站称谓<input v-model="form.siteLabel" required maxlength="20" placeholder="工厂、基地、园区" /></label>
      <label>首个场站名称<input v-model="form.siteName" required maxlength="200" /></label>
      <label>管理员用户名<input v-model="form.adminUsername" required pattern="[a-zA-Z][a-zA-Z0-9._-]{2,99}" autocomplete="off" /></label>
      <label>管理员姓名<input v-model="form.adminDisplayName" required maxlength="100" /></label>
      <label>管理员初始密码<input v-model="form.initialPassword" type="password" required minlength="12" maxlength="128" autocomplete="new-password" /></label>
      <p>至少12位，含大小写字母、数字和符号；UTF-8长度不超过72字节；首次登录必须修改。</p>
    </fieldset>
    <p v-if="error" role="alert">{{ error }}</p>
    <button type="submit" :disabled="busy">{{ busy ? '初始化中…' : unknown ? '使用原请求重试' : '确认初始化' }}</button>
    <button type="button" class="secondary-button" :disabled="busy || unknown" @click="emit('close')">取消</button>
  </form>
</template>
<style scoped>
.tenant-initialization { margin-top:20px; max-width:760px; }
fieldset { border:0; padding:0; min-width:0; }
label { display:block; margin-top:14px; }
input,select { width:100%; box-sizing:border-box; min-height:44px; }
button { width:auto; padding:0 18px; margin-right:12px; }
p { line-height:1.6; overflow-wrap:anywhere; }
[role=alert] { color:#a52828; }
</style>

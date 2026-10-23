<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue';
import type { components, createIsmClient } from '@ism/api-client';
type Change = components['schemas']['ProfileChange'];
type Fields = components['schemas']['ProfileChangeFields'];
const props = defineProps<{ client: ReturnType<typeof createIsmClient>; supplier: components['schemas']['PortalSupplierProfile']['supplier'] }>();
const emit = defineEmits<{ unauthorized: [] }>();
const history = ref<components['schemas']['ProfileChangePageResponse']['data']>();
const page = ref(0), busy = ref(false), loading = ref(false), editing = ref(false), error = ref(''), notice = ref(''), uncertain = ref(false);
const reason = ref(''), key = ref(''), cancelTarget = ref<Change>();
const errorBox = ref<HTMLElement>();
const fields = reactive<Fields>({ countryCode: 'CN' });
const labels = { shortName: '企业简称', industry: '所属行业', countryCode: '国家/地区代码', province: '省份', city: '城市', address: '企业地址', website: '企业网站' };
const states = { SUBMITTED: '待审核', APPROVED: '已批准', REJECTED: '已驳回', CANCELLED: '已撤回' };
const pending = computed(() => history.value?.items.some(item => item.status === 'SUBMITTED'));
let disposed = false;const requests = new Set<AbortController>();
async function fail(text: string) { error.value = text; await nextTick(); if (!disposed) errorBox.value?.focus(); }
function begin() {
  for (const field of Object.keys(labels) as (keyof Fields)[]) fields[field] = props.supplier[field] || '';
  reason.value = ''; key.value = crypto.randomUUID(); editing.value = true; uncertain.value = false; error.value = ''; notice.value = '';
}
async function load() {
  if (loading.value || disposed) return;
  loading.value = true; error.value = ''; history.value = undefined;
  const request = new AbortController(); requests.add(request);
  try {
    const result = await props.client.GET('/portal/profile-changes', { params: { query: { page: page.value, size: 10 } }, signal: request.signal });
    if (disposed) return;
    if (result.data) { history.value = result.data.data; if (uncertain.value && history.value.items.some(item => item.requestKey === key.value)) { editing.value = false; uncertain.value = false; notice.value = '已在记录中确认提交结果。'; } }
    else if (result.response.status === 401) emit('unauthorized');
    else await fail('申请记录读取失败，请重试。');
  } catch { if (!disposed) await fail('网络异常，申请记录未能读取。'); }
  finally { requests.delete(request); if (!disposed) loading.value = false; }
}
async function submit() {
  if (busy.value) return;
  if (!reason.value.trim()) return fail('请填写变更原因。');
  if (fields.website) { try { const url = new URL(fields.website); if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) throw new Error(); } catch { return fail('企业网站须为有效的 http/https 地址，不能包含账号密码。'); } }
  busy.value = true; error.value = ''; notice.value = '';
  try {
    const result = await props.client.POST('/portal/profile-changes', { body: { requestKey: key.value, baseVersion: props.supplier.version, fields: { ...fields }, reason: reason.value.trim() } });
    if (disposed) return;
    if (result.data) { editing.value = false; uncertain.value = false; page.value = 0; notice.value = '申请已提交，审核批准前企业档案保持不变。'; await load(); }
    else if (result.response.status === 401) emit('unauthorized');
    else await fail(result.error?.error.message || '申请未提交，请核查内容后重试。');
  } catch { if (!disposed) { uncertain.value = true; await fail('网络异常，提交结果未确认。请先刷新申请记录核查；再次提交会沿用同一标识和内容。'); } }
  finally { if (!disposed) busy.value = false; }
}
async function cancel() {
  const item = cancelTarget.value; if (!item || busy.value) return; busy.value = true; error.value = '';
  try {
    const result = await props.client.POST('/portal/profile-changes/{id}/cancel', { params: { path: { id: item.id } }, body: { version: item.version } });
    if (disposed) return;
    if (result.response.status === 401) { emit('unauthorized'); return; }
    cancelTarget.value = undefined; await load();
    if (result.error) await fail(result.error.error.message); else notice.value = '申请已撤回，企业档案未修改。';
  } catch { if (!disposed) await fail('撤回结果未确认，请刷新申请记录核查。'); }
  finally { if (!disposed) busy.value = false; }
}
async function move(offset: number) { page.value += offset; await load(); }
onMounted(load);
onUnmounted(() => { disposed = true; requests.forEach(request => request.abort()); });
</script>
<template>
  <section class="changes" aria-labelledby="profile-changes-title" :aria-busy="busy || loading">
    <h3 id="profile-changes-title">企业资料变更申请</h3>
    <p class="help">可申请更正简称、行业、国家省市、地址和网站。企业名称、信用代码及主体归属请联系邀请方。审核批准后才生效；时间为 UTC。</p>
    <div v-if="error" ref="errorBox" class="error" role="alert" tabindex="-1">{{ error }}</div><p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="loading" role="status">正在读取变更申请…</p>
    <div class="actions"><button :disabled="busy || loading" @click="load">刷新申请记录</button><button v-if="!editing" class="secondary" :disabled="busy || loading || !history || pending" @click="begin">申请资料变更</button></div>
    <p v-if="pending" class="help">本企业已有待审核申请，处理或撤回后可再次申请。</p>
    <form v-if="editing" @submit.prevent="submit">
      <fieldset :disabled="busy || uncertain">
        <label v-for="(label, field) in labels" :key="field">{{ label }}<input v-model="fields[field]" :aria-label="'变更' + label" :required="field === 'countryCode'" :pattern="field === 'countryCode' ? '[A-Z]{2}' : undefined" :maxlength="field === 'address' || field === 'website' ? 300 : field === 'countryCode' ? 2 : 100"></label>
        <label>变更原因（必填）<textarea v-model="reason" required maxlength="1000" aria-label="资料变更原因"></textarea></label>
      </fieldset>
      <div class="actions"><button type="submit" :disabled="busy">{{ busy ? '正在提交…' : uncertain ? '重试相同申请' : '确认提交申请' }}</button><button class="secondary" type="button" :disabled="busy || uncertain" @click="editing = false">取消填写</button></div>
    </form>
    <template v-if="history">
      <p v-if="!history.total" class="help">暂无资料变更申请。</p>
      <article v-for="item in history.items" :key="item.id" class="change-item">
        <h4>{{ states[item.status] }} · {{ item.createdAt }}</h4><p>原因：{{ item.reason }}</p>
        <dl><template v-for="(label, field) in labels" :key="field"><template v-if="(item.before[field] || '') !== (item.requested[field] || '')"><dt>{{ label }}</dt><dd>{{ item.before[field] || '未填写' }} → {{ item.requested[field] || '未填写' }}</dd></template></template></dl>
        <p v-if="item.reviewComment">处理说明：{{ item.reviewComment }}</p>
        <button v-if="item.canCancel" class="secondary" :disabled="busy || loading" @click="cancelTarget = item">撤回申请</button>
        <div v-if="cancelTarget?.id === item.id"><p>确认撤回这条待审核申请？</p><button :disabled="busy" @click="cancel">确认撤回申请</button> <button class="secondary" :disabled="busy" @click="cancelTarget = undefined">保留申请</button></div>
      </article>
      <div class="actions"><button class="secondary" :disabled="busy || loading || page === 0" @click="move(-1)">上一页</button><span>第 {{ page + 1 }} 页 / 共 {{ history.total }} 条</span><button class="secondary" :disabled="busy || loading || (page + 1) * 10 >= history.total" @click="move(1)">下一页</button></div>
    </template>
  </section>
</template>
<style scoped>
.changes{border-top:1px solid #dce4ed;margin-top:24px;padding-top:16px}.change-item{border:1px solid #dce4ed;border-radius:6px;padding:12px;margin:16px 0;overflow-wrap:anywhere}h4{margin:0;font-size:14px}.change-item dl{padding:8px;font-size:13px;grid-template-columns:80px minmax(0,1fr)}textarea{font:inherit;width:100%;min-height:90px;padding:10px;border:1px solid #a9b8c8;border-radius:6px;resize:vertical}.actions{align-items:center;margin:12px 0}
</style>

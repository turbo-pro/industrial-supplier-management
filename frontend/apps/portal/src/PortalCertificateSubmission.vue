<script setup lang="ts">
import { nextTick, onUnmounted, reactive, ref } from 'vue';
import { type components, type createIsmClient } from '@ism/api-client';
const props = defineProps<{ client: ReturnType<typeof createIsmClient> }>();
const emit = defineEmits<{ unauthorized: []; changed: [] }>();
type Upload = components['schemas']['PortalCertificateUpload'];
type Submit = components['schemas']['PortalCertificateSubmit'];
type Type = components['schemas']['PortalCertificateTypesResponse']['data'][number];
const opened = ref(false), busy = ref(false), historyBusy = ref(false), paused = ref(false);
const types = ref<Type[]>([]), history = ref<components['schemas']['PortalCertificateSubmissionPageResponse']['data']>();
const file = ref<File>(), upload = ref<Upload>(), progress = ref(0), phase = ref(''), notice = ref(''), error = ref('');
const errorPanel = ref<HTMLElement>();
const form = reactive({ typeId: '', certificateNo: '', issuingAuthority: '', issueDate: '', effectiveDate: '', expiryDate: '', permanent: false, confirmed: false });
const pending = ref<Submit>();
const renewal = ref<components['schemas']['PortalQualification']>();
const formPanel = ref<HTMLElement>();
let uploadKey = '', disposed = false;
const requests = new Set<AbortController>();
const labels = { DRAFT: '待员工核验', VALID: '核验通过', REJECTED: '核验拒绝，请联系邀请方核查原因', EXPIRING: '即将到期', EXPIRED: '已过期', REVOKED: '已撤销', SUPERSEDED: '已换证' };
async function fail(text: string) { if (disposed) return; error.value = text; await nextTick(); errorPanel.value?.focus(); }
function take<T>(result: { data?: { data: T }; response: Response }): T {
  if (result.data) return result.data.data;
  const code = result.response.status;
  if (code === 401) emit('unauthorized');
  throw new Error(code === 403 ? '当前租户未开通资质模块，请联系邀请方。' : code === 410 ? '上传已过期，请重新选择原件并上传。' : code === 429 ? '24小时上传额度已用完，请稍后再试。' : code === 409 ? '数据冲突：请刷新记录和原证照；可能已有待核验换证、原证已变化，或原件/证书编号重复。' : code === 400 || code === 422 ? '提交内容不符合要求，请检查原件格式、必填项及日期。' : '服务暂不可用，请重试；不要更换标识重复提交。');
}
async function load(page = 0) {
  if (historyBusy.value || busy.value || disposed) return;
  opened.value = true; historyBusy.value = true; error.value = ''; history.value = undefined;
  const request = new AbortController(); requests.add(request);
  try {
    const result = await props.client.GET('/portal/certificate-submissions/types', { signal: request.signal });
    if (disposed) return; types.value = take(result);
    const records = await props.client.GET('/portal/certificate-submissions', { params: { query: { page, size: 10 } }, signal: request.signal });
    if (!disposed) history.value = take(records);
  } catch (e) { if (!disposed) await fail(e instanceof Error ? e.message : '申请记录加载失败，请重试。'); }
  finally { requests.delete(request); if (!disposed) historyBusy.value = false; }
}
function clearForm() {
  if (busy.value || historyBusy.value || pending.value) return;
  renewal.value = undefined; file.value = undefined; upload.value = undefined; progress.value = 0; phase.value = ''; uploadKey = ''; error.value = ''; notice.value = '';
  Object.assign(form, { typeId: '', certificateNo: '', issuingAuthority: '', issueDate: '', effectiveDate: '', expiryDate: '', permanent: false, confirmed: false });
}
async function beginRenewal(certificate: components['schemas']['PortalQualification']) {
  if (disposed) return;
  if (busy.value || historyBusy.value || pending.value || file.value || Object.values(form).some(value => Boolean(value))) return fail('请先完成或清空当前填写，再选择原证照；不会覆盖尚未提交的内容。');
  await load(); if (disposed || error.value) return;
  const type = types.value.find(t => t.code === certificate.typeCode);
  if (!type) return fail('原证类型已停用或不可用，请联系邀请方。');
  renewal.value = certificate; Object.assign(form, { typeId: type.id, certificateNo: certificate.certificateNo, issuingAuthority: certificate.issuingAuthority ?? '', issueDate: '', effectiveDate: '', expiryDate: '', permanent: false, confirmed: false });
  await nextTick(); formPanel.value?.focus();
}
defineExpose({ beginRenewal });
function choose(event: Event) {
  if (busy.value || pending.value) return;
  if (typeof crypto.randomUUID !== 'function' || !crypto.subtle) { void fail('当前地址无法安全上传，请使用HTTPS或本机地址访问。'); return; }
  const input = event.target as HTMLInputElement; file.value = input.files?.[0]; input.value = ''; upload.value = undefined; progress.value = 0; phase.value = ''; uploadKey = crypto.randomUUID(); error.value = ''; notice.value = '';
}
async function sha256(bytes: ArrayBuffer) { return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), b => b.toString(16).padStart(2, '0')).join(''); }
async function sendFile() {
  if (busy.value || historyBusy.value || !file.value || pending.value || disposed) return;
  const selected = file.value;
  if (selected.size < 8 || selected.size > 10485760) return fail('请选择8字节至10MB的PDF、PNG或JPEG原件。');
  if (!crypto.subtle) return fail('当前环境无法安全计算摘要，请使用HTTPS或本机地址访问。');
  const name = selected.name.toLowerCase();
  const mime = name.endsWith('.pdf') ? 'application/pdf' : name.endsWith('.png') ? 'image/png' : /\.jpe?g$/.test(name) ? 'image/jpeg' : undefined;
  if (!mime) return fail('仅支持PDF、PNG和JPEG。');
  busy.value = true; paused.value = false; error.value = ''; notice.value = '';
  const request = new AbortController(); requests.add(request);
  try {
    phase.value = '计算完整文件摘要'; const hash = await sha256(await selected.arrayBuffer());
    if (disposed || paused.value) return;
    const initialized = await props.client.POST('/portal/certificate-uploads', { body: { requestKey: uploadKey, fileName: selected.name, contentType: mime, totalSize: selected.size, sha256: hash }, signal: request.signal });
    if (disposed) return; let current = take(initialized); upload.value = current;
    progress.value = Math.round(current.uploadedChunks.length / current.totalChunks * 100);
    if (current.status !== 'COMPLETED') {
      for (let index = 0; index < current.totalChunks; index++) {
        if (current.uploadedChunks.includes(index)) continue;
        if (disposed || paused.value) return;
        phase.value = `上传分片 ${index + 1}/${current.totalChunks}`;
        const bytes = await selected.slice(index * current.chunkSize, (index + 1) * current.chunkSize).arrayBuffer();
        const part = await props.client.PUT('/portal/certificate-uploads/{id}/chunks/{index}', { params: { path: { id: current.id, index }, header: { 'X-Chunk-SHA256': await sha256(bytes) } }, headers: { 'Content-Type': 'application/octet-stream' }, body: bytes as unknown as string, bodySerializer: () => bytes, signal: request.signal });
        if (disposed) return; current = take(part); upload.value = current; progress.value = Math.round(current.uploadedChunks.length / current.totalChunks * 100);
      }
      if (disposed || paused.value) return;
      phase.value = '校验完整原件并合并';
      const completed = await props.client.POST('/portal/certificate-uploads/{id}/complete', { params: { path: { id: current.id } }, body: { version: current.version }, signal: request.signal });
      if (disposed) return; upload.value = take(completed);
    }
    phase.value = '原件上传完成，尚未提交核验'; progress.value = 100;
  } catch (e) { if (!disposed) await fail((e instanceof Error ? e.message : '上传中断。') + ' 同页点击继续上传会核查已有分片；页面刷新后需重新选择上传。'); }
  finally { requests.delete(request); if (!disposed) { busy.value = false; if (paused.value) phase.value = '已暂停，可在本页继续上传'; } }
}
async function submit() {
  if (busy.value || historyBusy.value || disposed) return;
  if (!pending.value) {
    if (!form.confirmed || !form.typeId || !form.certificateNo.trim() || upload.value?.status !== 'COMPLETED') return fail('请选择资质类型、填写证书编号，完成原件上传并确认提交。');
    const type = types.value.find(t => t.id === form.typeId);
    if (!form.permanent && type?.validityRequired && !form.expiryDate) return fail('该类型要求填写到期日期。');
    pending.value = { requestKey: crypto.randomUUID(), typeId: form.typeId, certificateNo: form.certificateNo.trim(), issuingAuthority: form.issuingAuthority.trim(), issueDate: form.issueDate || null, effectiveDate: form.effectiveDate || null, expiryDate: form.permanent ? null : form.expiryDate || null, permanent: form.permanent, uploadId: upload.value.id };
    if (renewal.value) { pending.value.renewalParentId = renewal.value.id; pending.value.renewalParentVersion = renewal.value.version; }
  }
  busy.value = true; error.value = ''; notice.value = '';
  const request = new AbortController(); requests.add(request); let succeeded = false;
  try {
    const result = await props.client.POST('/portal/certificate-submissions', { body: pending.value, signal: request.signal });
    if (disposed) return;
    if (!result.data && [400, 403, 404, 409, 410, 422].includes(result.response.status)) pending.value = undefined;
    const record = take(result); succeeded = true;
    notice.value = `${renewal.value ? '换证' : '证照'}申请已提交：${record.certificateNo}。${labels[record.status]}，不代表准入通过。`;
    pending.value = undefined; file.value = undefined; upload.value = undefined; phase.value = ''; progress.value = 0;
    renewal.value = undefined; Object.assign(form, { typeId: '', certificateNo: '', issuingAuthority: '', issueDate: '', effectiveDate: '', expiryDate: '', permanent: false, confirmed: false }); emit('changed');
  } catch (e) { if (!disposed) await fail((e instanceof Error ? e.message : '提交中断。') + (pending.value ? ' 结果未知，表单已锁定；可刷新记录核查，或重试同一申请。' : ' 请核查记录后更正。')); }
  finally { requests.delete(request); if (!disposed) busy.value = false; }
  if (succeeded && !disposed) await load();
}
onUnmounted(() => { disposed = true; requests.forEach(r => r.abort()); file.value = undefined; pending.value = undefined; upload.value = undefined; });
</script>
<template>
  <section class="submissions" aria-labelledby="certificate-submit-title" :aria-busy="busy || historyBusy">
    <div class="heading"><h2 id="certificate-submit-title">{{ renewal ? '证照换证申请' : '新增证照申请' }}</h2><button class="secondary" :disabled="busy || historyBusy" @click="load()">{{ opened ? '刷新证照申请' : '填写证照申请' }}</button></div>
    <p class="help">上传不等于提交，提交后由员工核验。换证提交或拒绝不改变旧证，批准才替代；新证须已生效且未过期。已提交内容不可直接修改或撤回，拒绝后须重新上传并申请；补正及公开审核说明尚未接入。</p>
    <p v-if="renewal" class="help">原证：{{ renewal.certificateNo }}（{{ renewal.typeName }}），版本 {{ renewal.version }}。类型不可改变；请填写新证日期和原件。</p>
    <div v-if="error" ref="errorPanel" class="error" role="alert" tabindex="-1">{{ error }}</div><p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="historyBusy" role="status">正在读取类型及证照申请…</p>
    <template v-if="opened && types.length">
      <form ref="formPanel" tabindex="-1" aria-label="证照申请填写" @submit.prevent="submit">
        <fieldset :disabled="busy || historyBusy || !!pending"><legend>证照信息及原件</legend>
          <label>申请资质类型<select v-model="form.typeId" required :disabled="!!renewal"><option value="">请选择</option><option v-for="t in types" :key="t.id" :value="t.id">{{ t.name }}（{{ t.code }}）</option></select></label>
          <label>申请证书编号<input v-model="form.certificateNo" maxlength="100" required /></label><label>申请发证机关<input v-model="form.issuingAuthority" maxlength="200" /></label>
          <label>申请签发日期<input v-model="form.issueDate" type="date" /></label><label>申请生效日期<input v-model="form.effectiveDate" type="date" /></label>
          <label class="check"><input v-model="form.permanent" type="checkbox" />长期有效</label><label>申请到期日期<input v-model="form.expiryDate" type="date" :disabled="form.permanent" /></label>
          <label>选择证照原件<input type="file" accept=".pdf,.png,.jpg,.jpeg" @change="choose" /></label>
          <p v-if="file">已选择：{{ file.name }}（{{ file.size }} 字节）</p>
          <p class="help">PDF/PNG/JPEG，最多10MB。上传在本页可续传，不保存到浏览器存储；刷新页面后须重新选择。每账号/企业24小时最多10/50次初始化。</p>
          <button type="button" class="secondary" :disabled="!file || upload?.status === 'COMPLETED'" @click="sendFile">{{ upload ? '继续上传原件' : '上传证照原件' }}</button>
          <label class="check"><input v-model="form.confirmed" type="checkbox" />确认原件与填写内容一致，提交后等待员工核验</label>
        </fieldset>
        <p v-if="phase" role="status">{{ phase }}</p><progress v-if="file || progress" :value="progress" max="100" aria-label="原件上传进度">{{ progress }}%</progress>
        <button v-if="busy && file && !pending" type="button" class="secondary" @click="paused = true">暂停上传</button>
        <button :disabled="busy || historyBusy || (!pending && upload?.status !== 'COMPLETED')">{{ pending ? '重试同一证照申请' : '确认提交证照申请' }}</button>
        <button type="button" class="secondary" :disabled="busy || historyBusy || !!pending" @click="clearForm">清空当前填写</button>
      </form>
    </template>
    <p v-else-if="opened && !historyBusy && !error" class="help">暂无可申请的有效资质类型，请联系邀请方。</p>
    <template v-if="history"><h3>本企业证照申请记录</h3><p v-if="!history.items.length" class="help">暂无门户提交记录。</p><article v-for="item in history.items" :key="item.id" class="request"><h4>{{ item.certificateNo }} · {{ labels[item.status] }}</h4><p>{{ item.typeName }} · 原件：{{ item.fileName }}</p><p v-if="item.parentQualificationId">换证原证：{{ item.parentCertificateNo }}（{{ item.parentQualificationId }}）</p></article><div class="heading"><button class="secondary" :disabled="busy || historyBusy || history.page === 0" @click="load(history.page - 1)">上一页申请</button><span>第 {{ history.page + 1 }} 页，共 {{ history.total }} 条申请</span><button class="secondary" :disabled="busy || historyBusy || (history.page + 1) * history.size >= history.total" @click="load(history.page + 1)">下一页申请</button></div></template>
  </section>
</template>
<style scoped>
.submissions{margin-top:28px;padding-top:24px;border-top:1px solid #dce4ed;min-width:0;overflow-wrap:anywhere}.heading{display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between}.heading h2{margin:0}fieldset{border:1px solid #8295ab;border-radius:8px;margin:16px 0;padding:16px;min-width:0}legend{padding:0 8px}label{display:block;margin:12px 0}input:not([type=checkbox]),select{width:100%;box-sizing:border-box;min-height:44px;padding:10px;border:1px solid #8295ab;border-radius:6px;background:white;color:#24384f;font:inherit}input[type=file]{font-size:14px}.check{display:flex;gap:10px;align-items:center;min-height:44px}.check input{flex:none;width:20px;height:20px}.request{border-bottom:1px solid #dce4ed;overflow-wrap:anywhere}progress{display:block;width:100%;height:24px;margin:12px 0}button{margin-top:8px}
.check{flex-direction:row}.check input{min-height:20px;padding:0}
</style>

<script setup lang="ts">
import PortalMaterialUploader from './PortalMaterialUploader.vue';
import PortalCorrectionAttachmentList from './PortalCorrectionAttachmentList.vue';
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
const correction = ref<components['schemas']['PortalCertificateSubmission']>();
const correctionNote = ref('');
const correctionResponses = ref<string[]>([]);
const materialBusy=ref(false);
const materialGroups=ref<Record<number,{items:{requirementIndex:number;uploadId:string}[];pending:boolean;count:number}>>({});
const formPanel = ref<HTMLElement>();
let uploadKey = '', disposed = false;
const requests = new Set<AbortController>();
const labels = { DRAFT: '待员工核验', VALID: '核验通过', REJECTED: '核验拒绝', EXPIRING: '即将到期', EXPIRED: '已过期', REVOKED: '已撤销', SUPERSEDED: '已换证' };
async function fail(text: string) { if (disposed) return; error.value = text; await nextTick(); errorPanel.value?.focus(); }
function take<T>(result: { data?: { data: T }; response: Response }): T {
  if (result.data) return result.data.data;
  const code = result.response.status;
  if (code === 401) emit('unauthorized');
  throw new Error(code === 403 ? '当前租户未开通资质模块，请联系邀请方。' : code === 410 ? '上传已过期，请重新选择原件并上传。' : code === 429 ? '24小时上传额度已用完，请稍后再试。' : code === 409 ? '数据冲突：请刷新记录和原证照；可能已有待核验换证、原证已变化，补正已提交，或原件/证书编号重复。' : code === 400 || code === 422 ? '提交内容不符合要求，请检查原件格式、必填项及日期。' : '服务暂不可用，请重试；不要更换标识重复提交。');
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
  if (materialBusy.value || busy.value || historyBusy.value || pending.value) return;
  renewal.value = undefined; correction.value = undefined; correctionNote.value = ''; correctionResponses.value = []; materialGroups.value={}; file.value = undefined; upload.value = undefined; progress.value = 0; phase.value = ''; uploadKey = ''; error.value = ''; notice.value = '';
  Object.assign(form, { typeId: '', certificateNo: '', issuingAuthority: '', issueDate: '', effectiveDate: '', expiryDate: '', permanent: false, confirmed: false });
}
async function beginRenewal(certificate: components['schemas']['PortalQualification']) {
  if (disposed) return;
  if (materialBusy.value || busy.value || historyBusy.value || pending.value || file.value || correctionNote.value || Object.values(form).some(value => Boolean(value))) return fail('请先完成或清空当前填写，再选择原证照；不会覆盖尚未提交的内容。');
  await load(); if (disposed || error.value) return;
  const type = types.value.find(t => t.code === certificate.typeCode);
  if (!type) return fail('原证类型已停用或不可用，请联系邀请方。');
  renewal.value = certificate; Object.assign(form, { typeId: type.id, certificateNo: certificate.certificateNo, issuingAuthority: certificate.issuingAuthority ?? '', issueDate: '', effectiveDate: '', expiryDate: '', permanent: false, confirmed: false });
  await nextTick(); formPanel.value?.focus();
}
async function beginCorrection(item: components['schemas']['PortalCertificateSubmission']) {
  if (disposed) return;
  if (materialBusy.value || busy.value || historyBusy.value || pending.value || file.value || correctionNote.value || Object.values(form).some(value => Boolean(value))) return fail('请先完成或清空当前填写，再开始补正；不会覆盖尚未提交的内容。');
  if (item.status !== 'REJECTED' || item.correctionChildId) return fail('该申请已变化或已有后续补正，请刷新记录。');
  if (!types.value.some(t => t.id === item.typeId)) return fail('原申请类型已停用，请联系邀请方。');
  materialGroups.value={}; correction.value = item; correctionNote.value = ''; correctionResponses.value = (item.correctionRequirements ?? []).map(() => ''); renewal.value = undefined;
  Object.assign(form, { typeId: item.typeId, certificateNo: item.certificateNo, issuingAuthority: item.issuingAuthority ?? '', issueDate: item.issueDate ?? '', effectiveDate: item.effectiveDate ?? '', expiryDate: item.expiryDate ?? '', permanent: item.permanent, confirmed: false });
  error.value = ''; notice.value = ''; await nextTick(); formPanel.value?.focus();
}
defineExpose({ beginRenewal });
function choose(event: Event) {
  if (materialBusy.value || busy.value || pending.value) return;
  if (typeof crypto.randomUUID !== 'function' || !crypto.subtle) { void fail('当前地址无法安全上传，请使用HTTPS或本机地址访问。'); return; }
  const input = event.target as HTMLInputElement; file.value = input.files?.[0]; input.value = ''; upload.value = undefined; progress.value = 0; phase.value = ''; uploadKey = crypto.randomUUID(); error.value = ''; notice.value = '';
}
async function sha256(bytes: ArrayBuffer) { return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), b => b.toString(16).padStart(2, '0')).join(''); }
async function sendFile() {
  if (materialBusy.value || busy.value || historyBusy.value || !file.value || pending.value || disposed) return;
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
  if (materialBusy.value || busy.value || historyBusy.value || disposed) return;
  if (!pending.value) {
    if (!form.confirmed || !form.typeId || !form.certificateNo.trim() || upload.value?.status !== 'COMPLETED') return fail('请选择资质类型、填写证书编号，完成原件上传并确认提交。');
    if (correction.value && !correctionNote.value.trim()) return fail('请填写补正说明，说明如何回应上一轮审核意见。');
    if (correction.value && correctionResponses.value.some(v => !v.trim())) return fail('请按上一轮要求逐项填写补正回复。');
    const groups=Object.values(materialGroups.value);
    if(groups.some(g=>g.pending)||groups.reduce((n,g)=>n+g.count,0)>5)return fail('请完成或移除未上传附件，每轮最多5份附件。');
    if(correction.value?.attachmentRequiredFor?.some(index=>!materialGroups.value[index]?.items.length))return fail('请上传每个必交要求的附件。');
    const type = types.value.find(t => t.id === form.typeId);
    if (!form.permanent && type?.validityRequired && !form.expiryDate) return fail('该类型要求填写到期日期。');
    pending.value = { requestKey: crypto.randomUUID(), typeId: form.typeId, certificateNo: form.certificateNo.trim(), issuingAuthority: form.issuingAuthority.trim(), issueDate: form.issueDate || null, effectiveDate: form.effectiveDate || null, expiryDate: form.permanent ? null : form.expiryDate || null, permanent: form.permanent, uploadId: upload.value.id };
    if (correction.value) { pending.value.correctionOfId = correction.value.id; pending.value.correctionOfVersion = correction.value.version; pending.value.correctionNote = correctionNote.value.trim(); pending.value.correctionResponses = correctionResponses.value.map(v => v.trim()); pending.value.correctionAttachments=Object.values(materialGroups.value).flatMap(g=>g.items); }
    if (renewal.value) { pending.value.renewalParentId = renewal.value.id; pending.value.renewalParentVersion = renewal.value.version; }
  }
  busy.value = true; error.value = ''; notice.value = '';
  const request = new AbortController(); requests.add(request); let succeeded = false;
  try {
    const result = await props.client.POST('/portal/certificate-submissions', { body: pending.value, signal: request.signal });
    if (disposed) return;
    if (!result.data && [400, 403, 404, 409, 410, 422].includes(result.response.status)) pending.value = undefined;
    const record = take(result); succeeded = true;
    notice.value = `${correction.value ? '补正' : renewal.value ? '换证' : '证照'}申请已提交：${record.certificateNo}。${labels[record.status]}，不代表准入通过。`;
    pending.value = undefined; file.value = undefined; upload.value = undefined; phase.value = ''; progress.value = 0;
    renewal.value = undefined; correction.value = undefined; correctionNote.value = ''; correctionResponses.value = []; materialGroups.value={}; Object.assign(form, { typeId: '', certificateNo: '', issuingAuthority: '', issueDate: '', effectiveDate: '', expiryDate: '', permanent: false, confirmed: false }); emit('changed');
  } catch (e) { if (!disposed) await fail((e instanceof Error ? e.message : '提交中断。') + (pending.value ? ' 结果未知，表单已锁定；可刷新记录核查，或重试同一申请。' : ' 请核查记录后更正。')); }
  finally { requests.delete(request); if (!disposed) busy.value = false; }
  if (succeeded && !disposed) await load();
}
onUnmounted(() => { disposed = true; requests.forEach(r => r.abort()); file.value = undefined; pending.value = undefined; upload.value = undefined; });
</script>
<template>
  <section class="submissions" aria-labelledby="certificate-submit-title" :aria-busy="busy || historyBusy">
    <div class="heading"><h2 id="certificate-submit-title">{{ correction ? '证照补正申请' : renewal ? '证照换证申请' : '新增证照申请' }}</h2><button class="secondary" :disabled="materialBusy || busy || historyBusy" @click="load()">{{ opened ? '刷新证照申请' : '填写证照申请' }}</button></div>
    <p class="help">上传不等于提交，提交后由员工核验。换证提交或拒绝不改变旧证，批准才替代；新证须已生效且未过期。已提交内容不可直接修改或撤回，拒绝后须重新上传并申请；被拒申请可按审核意见关联补正，原申请和原件保留，每轮仍需独立核验。</p>
    <p v-if="renewal" class="help">原证：{{ renewal.certificateNo }}（{{ renewal.typeName }}），版本 {{ renewal.version }}。类型不可改变；请填写新证日期和原件。</p>
    <div v-if="correction" class="review-feedback"><p>补正依据：申请 {{ correction.id }} · 第 {{ (correction.correctionRound || 0) + 1 }} 次补正</p><p v-if="correction.correctionDueDate">补正期限（北京时间当日结束）：{{ correction.correctionDueDate }}；迟交仍可提交并留痕。</p><p v-if="correction.correctionOverdue" class="help">已逾期，本次将标注为迟交补正。</p><p>上一轮审核说明：{{ correction.publicReviewComment || '历史申请未发布说明，请联系邀请方确认。' }}</p><p v-if="correction.parentQualificationId">换证原证 {{ correction.parentQualificationId }} 沿用原申请关联；原证已变更时需重新发起换证。</p></div>
    <div v-if="error" ref="errorPanel" class="error" role="alert" tabindex="-1">{{ error }}</div><p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="materialBusy" role="status">正在上传补正附件，请等待完成后提交。</p>
    <p v-if="historyBusy" role="status">正在读取类型及证照申请…</p>
    <template v-if="opened && types.length">
      <form ref="formPanel" tabindex="-1" aria-label="证照申请填写" @submit.prevent="submit">
        <fieldset :disabled="materialBusy || busy || historyBusy || !!pending"><legend>证照信息及原件</legend>
          <label>申请资质类型<select v-model="form.typeId" required :disabled="!!renewal || !!correction"><option value="">请选择</option><option v-for="t in types" :key="t.id" :value="t.id">{{ t.name }}（{{ t.code }}）</option></select></label>
          <label v-if="correction">补正说明<textarea v-model="correctionNote" maxlength="1000" rows="4" required aria-describedby="correction-note-help" /></label><p v-if="correction" id="correction-note-help" class="help">请说明本轮修改及补充的材料，最多1000字。此说明会随申请提供给核验员工。</p>
          <div v-for="(requirement,index) in correction?.correctionRequirements ?? []" :key="index"><label>要求 {{ index+1 }}：{{ requirement }}<textarea v-model="correctionResponses[index]" :aria-label="`补正回复 ${index+1}`" rows="3" maxlength="500" required /></label><p v-if="correction?.attachmentRequiredFor?.includes(index)" class="help">本项至少一份附件。</p><PortalMaterialUploader :remaining="5-Object.entries(materialGroups).reduce((sum,[key,group])=>sum+(Number(key)===index?0:group.count),0)" :client="client" :index="index" :disabled="busy||historyBusy||!!pending||materialBusy" @busy="materialBusy=$event" @change="materialGroups[index]=$event" @unauthorized="emit('unauthorized')"/></div>
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
        <button :disabled="materialBusy || busy || historyBusy || (!pending && upload?.status !== 'COMPLETED')">{{ pending ? '重试同一证照申请' : '确认提交证照申请' }}</button>
        <button type="button" class="secondary" :disabled="materialBusy || busy || historyBusy || !!pending" @click="clearForm">清空当前填写</button>
      </form>
    </template>
    <p v-else-if="opened && !historyBusy && !error" class="help">暂无可申请的有效资质类型，请联系邀请方。</p>
    <template v-if="history"><h3>本企业证照申请记录</h3><p v-if="!history.items.length" class="help">暂无门户提交记录。</p><article v-for="item in history.items" :key="item.id" class="request"><h4>{{ item.certificateNo }} · {{ labels[item.status] }}</h4><p>{{ item.typeName }} · 原件：{{ item.fileName }}</p><template v-if="item.reviewedAt"><p>审核时间（北京时间）：{{ new Date(item.reviewedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) }}</p><p class="review-feedback">对外审核说明：{{ item.publicReviewComment || '本次审核未附说明。' }}</p></template><p v-else-if="item.status !== 'DRAFT'" class="help">历史记录未发布对外审核说明，请联系邀请方核查。</p><p v-if="item.correctionDueDate">补正期限（北京时间当日结束）：{{ item.correctionDueDate }}</p><p v-if="item.correctionOverdue" class="help">已逾期，仍可补正，将标注迟交。</p><ol v-if="item.correctionRequirements?.length"><li v-for="(requirement,index) in item.correctionRequirements" :key="index">{{ requirement }}</li></ol><p v-if="item.lateCorrection" class="help">本轮为迟交补正，仍需员工核验。</p><p v-if="item.sourceCorrectionDueDate">本轮所据期限：{{ item.sourceCorrectionDueDate }}（北京时间当日结束）</p><div v-for="(requirement,index) in item.sourceCorrectionRequirements" :key="index"><p>要求 {{ index+1 }}：{{ requirement }}</p><p class="review-feedback">回复：{{ item.correctionResponses?.[index] }}</p></div><p v-if="item.parentQualificationId">换证原证：{{ item.parentCertificateNo }}（{{ item.parentQualificationId }}）</p><template v-if="item.correctionOfId"><p>第 {{ item.correctionRound }} 次补正 · 上一轮 {{ item.correctionOfId }} · 本补正链首次申请 {{ item.correctionRootId }}</p><p class="review-feedback">补正说明：{{ item.correctionNote }}</p></template><PortalCorrectionAttachmentList v-if="item.correctionOfId" :client="client" :qualification-id="item.id" @unauthorized="emit('unauthorized')"/><p v-if="item.correctionChildId" class="help">已关联后续补正申请：{{ item.correctionChildId }}</p><button v-else-if="item.status === 'REJECTED'" class="secondary" :disabled="materialBusy || busy || historyBusy || !!pending" @click="beginCorrection(item)">按审核意见补正</button></article><div class="heading"><button class="secondary" :disabled="materialBusy || busy || historyBusy || history.page === 0" @click="load(history.page - 1)">上一页申请</button><span>第 {{ history.page + 1 }} 页，共 {{ history.total }} 条申请</span><button class="secondary" :disabled="materialBusy || busy || historyBusy || (history.page + 1) * history.size >= history.total" @click="load(history.page + 1)">下一页申请</button></div></template>
  </section>
</template>
<style scoped>
.submissions{margin-top:28px;padding-top:24px;border-top:1px solid #dce4ed;min-width:0;overflow-wrap:anywhere}.heading{display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between}.heading h2{margin:0}fieldset{border:1px solid #8295ab;border-radius:8px;margin:16px 0;padding:16px;min-width:0}legend{padding:0 8px}label{display:block;margin:12px 0}input:not([type=checkbox]),select,textarea{width:100%;box-sizing:border-box;min-height:44px;padding:10px;border:1px solid #8295ab;border-radius:6px;background:white;color:#24384f;font:inherit}input[type=file]{font-size:14px}.check{display:flex;gap:10px;align-items:center;min-height:44px}.check input{flex:none;width:20px;height:20px}.request{border-bottom:1px solid #dce4ed;overflow-wrap:anywhere}progress{display:block;width:100%;height:24px;margin:12px 0}button{margin-top:8px}
.check{flex-direction:row}.check input{min-height:20px;padding:0}
.review-feedback { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>

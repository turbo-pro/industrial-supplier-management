<script setup lang="ts">
import { nextTick, onUnmounted, ref } from 'vue';
import { type components, type createIsmClient } from '@ism/api-client';

const props = defineProps<{ client: ReturnType<typeof createIsmClient> }>();
const emit = defineEmits<{ unauthorized: []; renew: [certificate: components['schemas']['PortalQualification']] }>();
type Certificate = components['schemas']['PortalQualification'];
const labels = { VALID: '有效', EXPIRING: '即将到期', EXPIRED: '已过期', REVOKED: '已撤销', SUPERSEDED: '已换证' };
const opened = ref(false), busy = ref(false), detailBusy = ref(false), downloading = ref(false);
const keyword = ref(''), status = ref<Certificate['status'] | ''>('');
const history = ref<components['schemas']['PortalQualificationPageResponse']['data']>();
const selected = ref<Certificate>();
const error = ref(''), notice = ref(''), errorPanel = ref<HTMLElement>(), detailPanel = ref<HTMLElement>();
let filters: { keyword: string; status: Certificate['status'] | '' } = { keyword: '', status: '' };
let disposed = false;
const requests = new Set<AbortController>();
const urls = new Set<string>();
function message(code: number) {
  if (code === 403) return '当前租户未开通证照查阅，请联系邀请方核查套餐。';
  if (code === 404) return '证照或原件当前不可用，请刷新列表或联系邀请方。';
  return '证照服务暂不可用，请稍后重试。';
}
async function fail(text: string) { if (disposed) return; error.value = text; await nextTick(); errorPanel.value?.focus(); }
async function load(page = 0, apply = false) {
  if (busy.value || detailBusy.value || downloading.value || disposed) return;
  opened.value = true;
  if (apply) filters = { keyword: keyword.value.trim(), status: status.value };
  busy.value = true; error.value = ''; notice.value = ''; history.value = undefined; selected.value = undefined;
  const request = new AbortController(); requests.add(request);
  try {
    const result = await props.client.GET('/portal/qualifications', { params: { query: { ...filters, page, size: 10 } }, signal: request.signal });
    if (disposed) return;
    if (result.data) history.value = result.data.data;
    else if (result.response.status === 401) emit('unauthorized');
    else await fail(message(result.response.status));
  } catch { if (!disposed) await fail('网络异常，证照未能加载。请重试。'); }
  finally { requests.delete(request); if (!disposed) busy.value = false; }
}
async function detail(id: string) {
  if (busy.value || detailBusy.value || downloading.value || disposed) return;
  detailBusy.value = true; selected.value = undefined; error.value = ''; notice.value = '';
  const request = new AbortController(); requests.add(request);
  try {
    const result = await props.client.GET('/portal/qualifications/{id}', { params: { path: { id } }, signal: request.signal });
    if (disposed) return;
    if (result.data) { selected.value = result.data.data; await nextTick(); detailPanel.value?.focus(); }
    else if (result.response.status === 401) emit('unauthorized');
    else await fail(message(result.response.status));
  } catch { if (!disposed) await fail('网络异常，请重新查看证照详情。'); }
  finally { requests.delete(request); if (!disposed) detailBusy.value = false; }
}
async function download() {
  if (!selected.value || downloading.value || disposed) return;
  const id = selected.value.id;
  downloading.value = true; error.value = ''; notice.value = '';
  const request = new AbortController(); requests.add(request);
  try {
    const result = await props.client.GET('/portal/qualifications/{id}/content', { params: { path: { id } }, parseAs: 'blob', signal: request.signal });
    if (disposed) return;
    if (result.data) {
      const url = URL.createObjectURL(result.data); urls.add(url);
      const link = document.createElement('a'); link.href = url;
      const encoded = result.response.headers.get('Content-Disposition')?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
      try { link.download = encoded ? decodeURIComponent(encoded) : '证照原件'; } catch { link.download = '证照原件'; }
      document.body.append(link); link.click(); link.remove();
      setTimeout(() => { URL.revokeObjectURL(url); urls.delete(url); }, 1000);
      notice.value = '原件已交给浏览器下载，请检查下载列表；下载不代表证照或准入有效。';
    } else if (result.response.status === 401) emit('unauthorized');
    else await fail(message(result.response.status));
  } catch { if (!disposed) await fail('下载失败，请检查连接后重试。'); }
  finally { requests.delete(request); if (!disposed) downloading.value = false; }
}
onUnmounted(() => { disposed = true; requests.forEach(r => r.abort()); urls.forEach(url => URL.revokeObjectURL(url)); history.value = undefined; selected.value = undefined; });
</script>

<template>
  <section class="qualifications" aria-labelledby="portal-qualifications-title" :aria-busy="busy || detailBusy || downloading">
    <div class="heading"><h2 id="portal-qualifications-title">本企业资质证照</h2><button class="secondary" :disabled="busy || detailBusy || downloading" @click="load(history?.page ?? 0)">{{ opened ? '刷新证照列表' : '查看本企业证照' }}</button></div>
    <p class="help">查阅已核验及历史证照，待核验和被驳回的内部草稿不展示。过期、撤销、已换证原件仅供留档；实际准入及业务放行以邀请方审核为准。换证请先查看原证详情；新证核验通过后才替代旧证。</p>
    <template v-if="opened">
      <form class="filters" @submit.prevent="load(0, true)">
        <label>证照关键词<input v-model="keyword" maxlength="100" placeholder="类型名称、编码或证书编号" :disabled="busy || detailBusy || downloading" /></label>
        <label>证照状态<select v-model="status" :disabled="busy || detailBusy || downloading"><option value="">全部状态</option><option v-for="(label, code) in labels" :key="code" :value="code">{{ label }}</option></select></label>
        <button :disabled="busy || detailBusy || downloading">筛选证照</button>
      </form>
      <p v-if="busy || detailBusy || downloading" role="status">{{ busy ? '正在读取证照列表…' : detailBusy ? '正在读取证照详情…' : '正在下载原件，请稍候…' }}</p>
      <div v-if="error" ref="errorPanel" class="error" role="alert" tabindex="-1">{{ error }}</div>
      <p v-if="notice" role="status">{{ notice }}</p>
      <template v-if="history">
        <p v-if="!history.items.length" class="help">暂无符合条件的已发布证照。可调整筛选；需新增或换证请联系邀请方。</p>
        <ul class="certificate-list"><li v-for="item in history.items" :key="item.id"><h3>{{ item.typeName }}</h3><p>编号：{{ item.certificateNo }}</p><p>{{ labels[item.status] }} · {{ item.permanent ? '长期有效' : item.expiryDate ? '到期日：' + item.expiryDate : '未填写到期日' }}</p><button class="secondary" :aria-label="'查看证照 ' + item.certificateNo" :disabled="busy || detailBusy || downloading" @click="detail(item.id)">查看详情</button></li></ul>
        <div class="pagination"><button class="secondary" :disabled="history.page === 0 || detailBusy || downloading" @click="load(history.page - 1)">上一页证照</button><span>第 {{ history.page + 1 }} 页，共 {{ history.total }} 条</span><button class="secondary" :disabled="(history.page + 1) * history.size >= history.total || detailBusy || downloading" @click="load(history.page + 1)">下一页证照</button></div>
      </template>
      <section v-if="selected" ref="detailPanel" class="certificate-detail" aria-labelledby="certificate-detail-title" tabindex="-1">
        <h3 id="certificate-detail-title">证照详情</h3>
        <dl><dt>资质类型</dt><dd>{{ selected.typeName }}（{{ selected.typeCode }}）</dd><dt>证书编号</dt><dd>{{ selected.certificateNo }}</dd><dt>发证机关</dt><dd>{{ selected.issuingAuthority || '未填写' }}</dd><dt>签发日期</dt><dd>{{ selected.issueDate || '未填写' }}</dd><dt>生效日期</dt><dd>{{ selected.effectiveDate || '未填写' }}</dd><dt>到期日期</dt><dd>{{ selected.permanent ? '长期有效' : selected.expiryDate || '未填写' }}</dd><dt>证照状态</dt><dd>{{ labels[selected.status] }}</dd><dt>距到期天数</dt><dd>{{ selected.daysUntilExpiry == null ? '不适用' : selected.daysUntilExpiry < 0 ? '已过期 ' + (-selected.daysUntilExpiry) + ' 天' : selected.daysUntilExpiry + ' 天' }}</dd></dl>
        <button :disabled="downloading" @click="download">{{ downloading ? '正在下载原件…' : '下载证照原件' }}</button>
        <button v-if="['VALID','EXPIRING','EXPIRED'].includes(selected.status)" class="secondary" :disabled="downloading || busy || detailBusy" @click="emit('renew', selected)">申请该证照换证</button>
        <p class="help">仅下载当前证照关联的原件，不提供公开存储链接；原件不可用时请联系邀请方。</p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.qualifications{margin-top:28px;padding-top:24px;border-top:1px solid #dce4ed;min-width:0}.heading,.pagination{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:12px}.heading h2{margin:0}.filters{display:flex;flex-wrap:wrap;align-items:end;gap:12px;margin:20px 0}.filters label{flex:1;min-width:min(180px,100%)}.filters input,.filters select{box-sizing:border-box;width:100%;min-height:44px;padding:10px;border:1px solid #8295ab;border-radius:6px;background:white;color:#24384f;font:inherit}.certificate-list{list-style:none;padding:0}.certificate-list li{padding:16px 0;border-bottom:1px solid #dce4ed;overflow-wrap:anywhere}.certificate-list h3{margin:0}.certificate-list p{margin:8px 0}.certificate-detail{margin-top:24px;padding:16px;border:1px solid #8295ab;border-radius:8px;overflow-wrap:anywhere}.certificate-detail dl{grid-template-columns:minmax(80px,110px) minmax(0,1fr)}.pagination{margin-top:16px}
</style>

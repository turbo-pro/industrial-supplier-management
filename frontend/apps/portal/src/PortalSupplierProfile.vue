<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue';
import { type components, type createIsmClient } from '@ism/api-client';
import PortalProfileChanges from './PortalProfileChanges.vue';

const props = defineProps<{ client: ReturnType<typeof createIsmClient> }>();
const emit = defineEmits<{ unauthorized: [] }>();
const profile = ref<components['schemas']['PortalSupplierProfile']>();
const busy = ref(false);
const error = ref('');
const errorPanel = ref<HTMLElement>();
let disposed = false;
let request: AbortController | undefined;
const types = { MANUFACTURER: '生产制造商', TRADER: '贸易商', SERVICE_PROVIDER: '服务商', CONTRACTOR: '承包商', OTHER: '其他' };

async function load() {
  if (busy.value || disposed) return;
  profile.value = undefined; error.value = ''; busy.value = true;
  request = new AbortController();
  try {
    const result = await props.client.GET('/portal/supplier-profile', { signal: request.signal });
    if (disposed) return;
    if (result.data) profile.value = result.data.data;
    else if (result.response.status === 401) emit('unauthorized');
    else error.value = result.response.status === 404
      ? '当前企业档案不可用，请联系邀请方核查。'
      : '企业档案加载失败，请稍后重试。';
  } catch {
    if (!disposed) error.value = '网络异常，未能读取企业档案。请检查连接后重试。';
  } finally {
    if (!disposed) {
      busy.value = false;
      if (error.value) { await nextTick(); errorPanel.value?.focus(); }
    }
  }
}
onMounted(load);
onUnmounted(() => { disposed = true; request?.abort(); profile.value = undefined; });
</script>

<template>
  <section class="profile-section" aria-labelledby="supplier-profile-title" :aria-busy="busy">
    <div class="profile-heading"><h2 id="supplier-profile-title">本企业档案</h2><button class="secondary" :disabled="busy" @click="load">{{ busy ? '正在读取…' : '刷新企业档案' }}</button></div>
    <p class="help">企业资料更正可在下方提交申请，批准后生效；档案状态不代表准入审批结论。</p>
    <p v-if="busy" role="status">正在读取本企业档案…</p>
    <div v-if="error" ref="errorPanel" class="error" role="alert" tabindex="-1">{{ error }}</div>
    <template v-if="profile">
      <dl>
        <dt>企业名称</dt><dd>{{ profile.supplier.name }}</dd>
        <dt>供应商编码</dt><dd>{{ profile.supplier.code }}</dd>
        <dt>企业简称</dt><dd>{{ profile.supplier.shortName || '未填写' }}</dd>
        <dt>统一社会信用代码</dt><dd>{{ profile.supplier.unifiedSocialCreditCode || '未填写' }}</dd>
        <dt>企业类型</dt><dd>{{ types[profile.supplier.type] }}</dd>
        <dt>档案状态</dt><dd>{{ profile.supplier.status === 'ACTIVE' ? '已启用' : '草稿' }}</dd>
        <dt>所属行业</dt><dd>{{ profile.supplier.industry || '未填写' }}</dd>
        <dt>国家/地区代码</dt><dd>{{ profile.supplier.countryCode || '未填写' }}</dd>
        <dt>所在省市</dt><dd>{{ [profile.supplier.province, profile.supplier.city].filter(Boolean).join(' / ') || '未填写' }}</dd>
        <dt>企业地址</dt><dd>{{ profile.supplier.address || '未填写' }}</dd>
        <dt>成立日期</dt><dd>{{ profile.supplier.establishedDate || '未填写' }}</dd>
        <dt>企业网站</dt><dd>{{ profile.supplier.website || '未填写' }}</dd>
      </dl>
      <h3>已关联采购品类</h3>
      <ul v-if="profile.purchaseCategories.length" class="profile-categories"><li v-for="category in profile.purchaseCategories" :key="category.code">{{ category.name }} <span class="help">（{{ category.code }}）</span></li></ul>
      <p v-else class="help">暂无有效的已关联采购品类，请联系邀请方维护。</p>
      <PortalProfileChanges :client="client" :supplier="profile.supplier" @unauthorized="emit('unauthorized')" />
    </template>
  </section>
</template>

<style scoped>
.profile-section{margin-top:28px;padding-top:24px;border-top:1px solid #dce4ed}.profile-heading{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:12px}.profile-heading h2{margin:0}.profile-categories{padding-left:20px;overflow-wrap:anywhere}.profile-categories li{margin:8px 0}h3{font-size:16px;margin:20px 0 8px}dl{grid-template-columns:minmax(90px,120px) minmax(0,1fr)}
</style>

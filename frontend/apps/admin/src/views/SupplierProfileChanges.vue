<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref } from 'vue';
import { ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
type Change = components['schemas']['ProfileChange'];
const props = defineProps<{ supplierId: string }>();
const emit = defineEmits<{ changed: [] }>();
const session = useSessionStore();
const expanded = ref(false), busy = ref(false), loading = ref(false), error = ref(''), notice = ref(''), page = ref(0), comment = ref('');
const history = ref<components['schemas']['ProfileChangePageResponse']['data']>(), selected = ref<Change>();
const errorBox = ref<HTMLElement>();
let disposed = false;
onUnmounted(() => { disposed = true; });
const states = { SUBMITTED: '待审核', APPROVED: '已批准', REJECTED: '已驳回', CANCELLED: '已撤回' };
const labels = { shortName: '企业简称', industry: '所属行业', countryCode: '国家/地区代码', province: '省份', city: '城市', address: '企业地址', website: '企业网站' };
const differences = computed(() => selected.value ? (Object.keys(labels) as (keyof typeof labels)[]).filter(key => (selected.value!.before[key] || '') !== (selected.value!.requested[key] || '')).map(key => ({ label: labels[key], before: selected.value!.before[key] || '未填写', after: selected.value!.requested[key] || '未填写' })) : []);
async function fail(text: string) { error.value = text; await nextTick(); errorBox.value?.focus(); }
async function load() {
  loading.value = true; error.value = ''; history.value = undefined;
  try {
    const result = await session.client.GET('/suppliers/{supplierId}/profile-changes', { params: { path: { supplierId: props.supplierId }, query: { page: page.value, size: 10 } } });
    if (disposed) return;
    if (result.data) history.value = result.data.data; else await fail(result.error?.error.message || '资料变更申请读取失败');
  } catch { await fail('网络异常，申请记录读取失败，请重试'); }
  finally { loading.value = false; }
}
async function open() { expanded.value = !expanded.value; selected.value = undefined; if (expanded.value) await load(); }
async function review(decision: 'APPROVE' | 'REJECT') {
  const item = selected.value; if (!item || busy.value) return;
  if (!comment.value.trim()) return fail('请填写审核说明');
  try { await ElMessageBox.confirm(decision === 'APPROVE' ? '确认批准并更新企业档案？系统将复核原档案版本。' : '确认驳回本次变更申请？企业档案保持不变。', '资料变更审核', { type: 'warning' }); } catch { return; }
  if (disposed) return;
  busy.value = true; error.value = ''; notice.value = '';
  try {
    const result = await session.client.POST('/suppliers/{supplierId}/profile-changes/{id}/review', { params: { path: { supplierId: props.supplierId, id: item.id } }, body: { decision, comment: comment.value.trim(), version: item.version } });
    if (disposed) return;
    if (result.data) { selected.value = result.data.data; await load(); notice.value = decision === 'APPROVE' ? '已批准，企业档案已更新。' : '已驳回，企业档案未修改。'; if (decision === 'APPROVE') emit('changed'); }
    else { await fail(result.error?.error.message || '审核失败，请刷新记录核查'); }
  } catch { await fail('审核结果未确认，请刷新记录核查，不要连续重复提交'); }
  finally { busy.value = false; }
}
async function move(offset: number) { page.value += offset; selected.value = undefined; await load(); }
</script>
<template>
  <section class="profile-changes" aria-label="门户企业资料变更审核">
    <h3>资料变更 <el-button :disabled="busy || loading" :aria-expanded="expanded" @click="open">{{ expanded ? '收起资料变更' : '资料变更审核' }}</el-button></h3>
    <template v-if="expanded">
      <el-alert title="供应商仅申请白名单企业字段；批准前档案不变。原档案已修改时须驳回并重新申请。时间为 UTC。" type="info" :closable="false" />
      <p v-if="error" ref="errorBox" role="alert" tabindex="-1">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p><p v-if="loading" role="status">正在读取资料变更…</p>
      <el-button :disabled="busy || loading" @click="selected = undefined; load()">刷新资料变更</el-button>
      <template v-if="history">
        <p v-if="!history.canReview">当前账号只读，无资料变更审核权限。</p>
        <div class="change-table"><el-table :data="history.items" empty-text="暂无资料变更申请"><el-table-column prop="createdAt" label="提交时间（UTC）" min-width="180" /><el-table-column label="状态" min-width="100"><template #default="{row}">{{ states[row.status as keyof typeof states] }}</template></el-table-column><el-table-column prop="reason" label="申请原因" min-width="200" /><el-table-column label="操作" width="100"><template #default="{row}"><el-button :disabled="busy" @click="selected = row; comment = ''; error = ''; notice = ''">查看变更</el-button></template></el-table-column></el-table></div>
        <div class="paging"><el-button :disabled="busy || loading || page === 0" @click="move(-1)">上一页</el-button><span>第 {{ page + 1 }} 页，共 {{ history.total }} 条</span><el-button :disabled="busy || loading || (page + 1) * 10 >= history.total" @click="move(1)">下一页</el-button></div>
        <div v-if="selected"><h4>变更前后对比 · {{ states[selected.status] }}</h4><p>申请原因：{{ selected.reason }}</p><div class="change-table"><el-table :data="differences"><el-table-column prop="label" label="字段" min-width="120" /><el-table-column prop="before" label="原值" min-width="180" /><el-table-column prop="after" label="申请值" min-width="180" /></el-table></div><p v-if="selected.reviewComment">处理说明：{{ selected.reviewComment }}</p>
          <el-form v-if="history.canReview && selected.status === 'SUBMITTED'" label-position="top" :disabled="busy || loading" @submit.prevent><el-form-item label="审核说明（必填）" :error="error && !comment.trim() ? '请填写审核说明' : undefined"><el-input v-model="comment" type="textarea" maxlength="1000" aria-label="资料变更审核说明" /></el-form-item><el-button type="primary" :loading="busy" @click="review('APPROVE')">批准并更新档案</el-button><el-button type="danger" :disabled="busy" @click="review('REJECT')">驳回变更申请</el-button></el-form>
        </div>
      </template>
    </template>
  </section>
</template>
<style scoped>
.profile-changes{margin-top:24px;min-width:0;width:100%;max-width:100%}.profile-changes h3,.paging{display:flex;gap:12px;flex-wrap:wrap;align-items:center}.change-table{overflow:auto;width:100%;max-width:100%;margin:16px 0}.profile-changes p{overflow-wrap:anywhere}.profile-changes :deep(.el-button){min-height:44px;margin-top:8px}.profile-changes form{margin-top:16px}
</style>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';
import { createIsmClient, type components } from '@ism/api-client';

const props = defineProps<{ tenantId: string; tenantName: string; token: string; canManage: boolean }>();
defineEmits<{ close: [] }>();
type Plan = components['schemas']['SubscriptionView'];
const client = createIsmClient({ getAccessToken: () => props.token });
const result = ref<components['schemas']['SubscriptionPage'] | null>(null);
const loading = ref(false), saving = ref(false), page = ref(0);
const error = ref(''), notice = ref(''), reason = ref('');
const target = ref<Plan | null>(null);
const reasonInput = ref<HTMLTextAreaElement | null>(null);
const heading = ref<HTMLHeadingElement | null>(null);
let request = 0, retryBody = '', retryKey = '';
const labels: Record<Plan['state'], string> = {
  SCHEDULED: '待生效', EFFECTIVE: '生效中', EXPIRED: '已到期', SUPERSEDED: '已替换', CANCELLED: '已取消', UNAVAILABLE: '版本不可用',
};
const time = (value?: string | null) => value ? `${value.replace('T', ' ')} UTC` : '无固定结束时间';

async function load() {
  const id = ++request;
  loading.value = true; error.value = ''; target.value = null;
  try {
    const response = await client.GET('/console/tenants/{tenantId}/subscriptions', {
      params: { path: { tenantId: props.tenantId }, query: { page: page.value, size: 10 } },
    });
    if (id !== request) return;
    if (response.error) { result.value = null; error.value = response.error.error.message; return; }
    result.value = response.data.data;
  } catch { if (id === request) { result.value = null; error.value = '读取订阅计划失败，请重试'; } }
  finally { if (id === request) loading.value = false; }
}
async function select(plan: Plan) {
  target.value = plan; reason.value = ''; retryBody = ''; retryKey = ''; error.value = ''; notice.value = '';
  await nextTick(); reasonInput.value?.focus();
}
async function cancel() {
  if (!target.value || !result.value || !props.canManage || saving.value || !reason.value.trim()) return;
  const body = { tenantVersion: result.value.tenantVersion, reason: reason.value.trim() };
  const serialized = JSON.stringify({ id: target.value.id, ...body });
  if (retryBody !== serialized) { retryBody = serialized; retryKey = crypto.randomUUID(); }
  saving.value = true; error.value = ''; notice.value = '';
  try {
    const response = await client.POST('/console/tenants/{tenantId}/subscriptions/{id}/cancel', {
      params: { path: { tenantId: props.tenantId, id: target.value.id }, header: { 'Idempotency-Key': retryKey } }, body,
    });
    if (response.error) {
      const message = response.error.error.message;
      if (response.response.status === 409) await load();
      error.value = message; return;
    }
    await load(); notice.value = '未来计划已取消，当前生效套餐保持不变';
    await nextTick(); heading.value?.focus();
  } catch { error.value = '取消请求未确认，可重试；相同请求将复用幂等键'; }
  finally { saving.value = false; }
}
function move(delta: number) { page.value += delta; notice.value = ''; void load(); }
onMounted(load);
</script>

<template>
  <section class="panel" aria-label="订阅计划" :aria-busy="loading || saving">
    <div class="panel-title">
      <div><h2 ref="heading" tabindex="-1">{{ tenantName }} · 订阅计划</h2><p>时间按 UTC 显示。只能取消待生效计划，不会恢复已到期的旧套餐。</p></div>
      <button type="button" :disabled="saving" @click="$emit('close')">关闭计划</button>
    </div>
    <p v-if="!canManage">当前账号只读；取消计划需要套餐分配权限。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="loading" role="status">读取订阅计划中…</p>
    <template v-if="result && !loading">
      <p>当前有效版本：{{ result.currentPackageVersionId ?? '暂无有效业务套餐' }} · 共 {{ result.total }} 条记录</p>
      <div class="plan-table"><table>
        <thead><tr><th>套餐 / 版本</th><th>状态</th><th>起始时间</th><th>结束时间</th><th>取消记录</th><th>操作</th></tr></thead>
        <tbody><tr v-for="plan in result.items" :key="plan.id">
          <td>{{ plan.packageName }} / {{ plan.versionName }}</td><td>{{ labels[plan.state] }}</td>
          <td>{{ time(plan.effectiveFrom) }}</td><td>{{ time(plan.effectiveTo) }}</td>
          <td>{{ plan.cancelReason ?? '—' }}<small v-if="plan.cancelledAt">{{ time(plan.cancelledAt) }} · 操作人 {{ plan.cancelledBy }}</small></td>
          <td><button v-if="canManage && plan.canCancel" type="button" :disabled="saving" @click="select(plan)">取消计划</button><span v-else>—</span></td>
        </tr><tr v-if="!result.items.length"><td colspan="6">暂无订阅记录</td></tr></tbody>
      </table></div>
      <nav class="plan-actions" aria-label="订阅分页">
        <button type="button" :disabled="page === 0 || saving" @click="move(-1)">上一页</button><span>第 {{ page + 1 }} 页</span>
        <button type="button" :disabled="(page + 1) * result.size >= result.total || saving" @click="move(1)">下一页</button>
      </nav>
      <form v-if="target && canManage" class="password-reset-form" aria-label="取消未来计划确认" @submit.prevent="cancel">
        <h3>取消 {{ target.packageName }} / {{ target.versionName }}？</h3>
        <p>该计划原定于 {{ time(target.effectiveFrom) }} 生效。取消将保留记录及原因，不影响当前套餐。</p>
        <label>取消原因<textarea ref="reasonInput" v-model="reason" required maxlength="500" :disabled="saving" rows="3" /></label>
        <button type="submit" :disabled="saving || !reason.trim()">{{ saving ? '取消中…' : '确认取消计划' }}</button>
        <button type="button" :disabled="saving" @click="target = null">保留计划</button>
      </form>
    </template>
    <button type="button" :disabled="loading || saving" @click="load">刷新计划</button>
  </section>
</template>

<style scoped>
.panel-title { flex-wrap: wrap; gap: 12px; }
.panel-title > div { flex: 1 1 220px; }
.panel-title button { flex-shrink: 0; }
.plan-table { overflow-x: auto; }
.plan-table td { min-width: 110px; }
.plan-table small { display: block; margin-top: 6px; }
.plan-actions { display: flex; align-items: center; gap: 12px; margin: 16px 0; }
button { width: auto; min-height: 44px; padding: 0 14px; }
textarea { width: 100%; box-sizing: border-box; font: inherit; padding: 8px; }
</style>

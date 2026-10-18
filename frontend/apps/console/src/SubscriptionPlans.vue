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
const reminderTarget = ref<Plan | null>(null);
const reminderRows = ref<components['schemas']['SubscriptionReminder'][]>([]);
const reminderLoading = ref(false), reminderSending = ref(false);
const reminderError = ref(''), reminderNotice = ref('');
let reminderKey = '', reminderRequest = 0;
const stageLabels = { WITHIN_7_DAYS: '7 天内到期', WITHIN_1_DAY: '1 天内到期', EXPIRED: '已到期' };
const deliveryLabels = { PENDING: '处理中', SENT: '已发送', FAILED: '发送失败' };
let request = 0, retryBody = '', retryKey = '';
const labels: Record<Plan['state'], string> = {
  SCHEDULED: '待生效', EFFECTIVE: '生效中', EXPIRED: '已到期', SUPERSEDED: '已替换', CANCELLED: '已取消', UNAVAILABLE: '版本不可用',
};
const time = (value?: string | null) => value ? `${value.replace('T', ' ')} UTC` : '无固定结束时间';

async function load() {
  const id = ++request;
  loading.value = true; error.value = ''; target.value = null; reminderTarget.value = null; reminderRequest++;
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
  reminderTarget.value = null; reminderRequest++;
  target.value = plan; reason.value = ''; retryBody = ''; retryKey = ''; error.value = ''; notice.value = '';
  await nextTick(); reasonInput.value?.focus();
}
async function cancel() {
  if (!target.value || !result.value || !props.canManage || saving.value || reminderSending.value || !reason.value.trim()) return;
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
async function showReminders(plan: Plan) {
  target.value = null;
  reminderTarget.value = plan; reminderRows.value = []; reminderKey = ''; reminderNotice.value = '';
  await loadReminders();
}
async function loadReminders() {
  if (!reminderTarget.value) return;
  const id = ++reminderRequest;
  reminderLoading.value = true; reminderError.value = '';
  try {
    const response = await client.GET('/console/tenants/{tenantId}/subscriptions/{id}/reminders', {
      params: { path: { tenantId: props.tenantId, id: reminderTarget.value.id } },
    });
    if (id !== reminderRequest) return;
    if (response.error) { reminderError.value = response.error.error.message; return; }
    reminderRows.value = response.data.data;
  } catch { if (id === reminderRequest) reminderError.value = '读取提醒记录失败，请刷新记录重试'; }
  finally { if (id === reminderRequest) reminderLoading.value = false; }
}
async function sendReminder() {
  if (!reminderTarget.value || !props.canManage || reminderSending.value) return;
  reminderSending.value = true; reminderError.value = ''; reminderNotice.value = '';
  if (!reminderKey) reminderKey = crypto.randomUUID();
  try {
    const response = await client.POST('/console/tenants/{tenantId}/subscriptions/{id}/reminders/dispatch', {
      params: { path: { tenantId: props.tenantId, id: reminderTarget.value.id }, header: { 'Idempotency-Key': reminderKey } },
    });
    reminderKey = '';
    if (response.error) { reminderError.value = response.error.error.message; return; }
    await loadReminders();
    if (response.data.data.status === 'FAILED') reminderError.value = `发送失败（${response.data.data.errorCode}），请修复收件人或消息模板后重新检查`;
    else reminderNotice.value = response.data.data.status === 'SENT'
      ? `已向 ${response.data.data.recipientCount} 位租户管理员发送站内提醒`
      : '未发送：不在提醒窗口、订阅已失效或该阶段已发送；不会重复投递';
  } catch { reminderError.value = '检查结果未确认，可重试；同一阶段不会重复投递'; }
  finally { reminderSending.value = false; }
}
onMounted(load);
</script>

<template>
  <section class="panel" aria-label="订阅计划" :aria-busy="loading || saving || reminderSending">
    <div class="panel-title">
      <div><h2 ref="heading" tabindex="-1">{{ tenantName }} · 订阅计划</h2><p>时间按 UTC 显示。只能取消待生效计划，不会恢复已到期的旧套餐。</p></div>
      <button type="button" :disabled="saving || reminderSending" @click="$emit('close')">关闭计划</button>
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
          <td><button v-if="canManage && plan.canCancel" type="button" :disabled="saving || reminderSending" @click="select(plan)">取消计划</button>
            <button type="button" :disabled="saving || reminderSending" @click="showReminders(plan)">到期提醒记录</button></td>
        </tr><tr v-if="!result.items.length"><td colspan="6">暂无订阅记录</td></tr></tbody>
      </table></div>
      <nav class="plan-actions" aria-label="订阅分页">
        <button type="button" :disabled="page === 0 || saving || reminderSending" @click="move(-1)">上一页</button><span>第 {{ page + 1 }} 页</span>
        <button type="button" :disabled="(page + 1) * result.size >= result.total || saving || reminderSending" @click="move(1)">下一页</button>
      </nav>
      <form v-if="target && canManage" class="password-reset-form" aria-label="取消未来计划确认" @submit.prevent="cancel">
        <h3>取消 {{ target.packageName }} / {{ target.versionName }}？</h3>
        <p>该计划原定于 {{ time(target.effectiveFrom) }} 生效。取消将保留记录及原因，不影响当前套餐。</p>
        <label>取消原因<textarea ref="reasonInput" v-model="reason" required maxlength="500" :disabled="saving" rows="3" /></label>
        <button type="submit" :disabled="saving || reminderSending || !reason.trim()">{{ saving ? '取消中…' : '确认取消计划' }}</button>
        <button type="button" :disabled="saving" @click="target = null">保留计划</button>
      </form>
      <section v-if="reminderTarget" class="role-preview" aria-label="订阅到期提醒">
        <h3>{{ reminderTarget.packageName }} / {{ reminderTarget.versionName }} · 到期提醒</h3>
        <p>按 7 天内、1 天内及到期阶段各发一次，仅站内通知。人工检查会向当前有效租户管理员发送，不会自动续订或更改套餐。</p>
        <p v-if="reminderError" role="alert">{{ reminderError }}</p><p v-if="reminderNotice" role="status">{{ reminderNotice }}</p>
        <p v-if="reminderLoading" role="status">读取提醒记录中…</p>
        <div v-else class="plan-table"><table>
          <thead><tr><th>阶段</th><th>投递状态</th><th>尝试次数</th><th>收件人数</th><th>最近尝试</th><th>错误码</th></tr></thead>
          <tbody><tr v-for="row in reminderRows" :key="row.stage"><td>{{ stageLabels[row.stage] }}</td><td>{{ deliveryLabels[row.status] }}</td>
            <td>{{ row.attemptCount }}</td><td>{{ row.recipientCount }}</td><td>{{ time(row.attemptedAt) }}</td><td>{{ row.errorCode ?? '—' }}</td></tr>
            <tr v-if="!reminderRows.length"><td colspan="6">{{ reminderError ? '记录未确认，请刷新提醒记录重试' : '尚无提醒记录' }}</td></tr></tbody>
        </table></div>
        <button v-if="canManage" type="button" :disabled="reminderLoading || reminderSending || saving" @click="sendReminder">{{ reminderSending ? '检查发送中…' : '检查并发送到期提醒' }}</button>
        <p v-else>当前账号只读，无权触发提醒。</p>
        <button type="button" :disabled="reminderLoading || reminderSending" @click="loadReminders">刷新提醒记录</button>
      </section>
    </template>
    <button type="button" :disabled="loading || saving || reminderSending" @click="load">刷新计划</button>
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

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { createIsmClient } from '@ism/api-client';

const props = defineProps<{
  token: string; requestId: string; tenantId: string; tenantName: string;
  operatorName: string; expiresAt: string;
}>();
const emit = defineEmits<{ engaged: [value: boolean] }>();
// Capture the operator identity for this component lifetime. Never store session credentials in reactive state.
const token = props.token;
const client = createIsmClient({ getAccessToken: () => token });
const fields = { id: 'ID', code: '编码', name: '名称', type: '类型', status: '状态' };
const supplier = ref('');
const result = ref<Partial<Record<keyof typeof fields, string>>>();
const active = ref(false);
const busy = ref(false);
const exitPending = ref(false);
const expired = ref(false);
const message = ref('');
const error = ref('');
let accessSecret: string | undefined;
let sessionId: string | undefined;
let disposed = false;
const deadline = Date.parse(props.expiresAt.endsWith('Z') ? props.expiresAt : props.expiresAt + 'Z');

function clear() {
  accessSecret = undefined;
  result.value = undefined;
  active.value = false;
}

function expire() {
  expired.value = true;
  clear();
  supplier.value = '';
  error.value = '';
  message.value = '授权已到期，已清除读取结果。';
  emit('engaged', false);
}
const timer = window.setTimeout(expire, Math.max(0, deadline - Date.now() || 0));

async function revoke(id: string) {
  const r = await client.POST('/console/support-sessions/{sessionId}/revoke', { params: { path: { sessionId: id } } });
  if (!r.data?.data) throw Error('Revoke not confirmed');
}

async function start() {
  if (busy.value || active.value || exitPending.value || expired.value || disposed) return;
  if (!(Date.now() < deadline)) { expire(); return; }
  busy.value = true;
  emit('engaged', true);
  error.value = '';
  message.value = '';
  try {
    const browserSecret = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32))))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const reserve = await client.POST('/console/support-requests/{requestId}/sessions', { params: { path: { requestId: props.requestId } } });
    sessionId = reserve.data?.data.sessionId;
    if (!sessionId || disposed || expired.value) throw Error('Cannot reserve');
    const params = { path: { sessionId } };
    const issue = await client.POST('/console/support-sessions/{sessionId}/entry-token', { params, body: { browserSecret } });
    if (!issue.data?.data.entryToken || disposed || expired.value) throw Error('Cannot issue');
    const exchange = await client.POST('/console/support-sessions/{sessionId}/exchange', { params, body: { browserSecret, entryToken: issue.data.data.entryToken } });
    if (!exchange.data?.data.accessSecret || disposed || expired.value || !(Date.now() < deadline)) throw Error('Cannot exchange');
    accessSecret = exchange.data.data.accessSecret;
    active.value = true;
  } catch {
    clear();
    if (sessionId) {
      try { await revoke(sessionId); sessionId = undefined; }
      catch { exitPending.value = true; }
    }
    if (!disposed && !expired.value) {
      error.value = exitPending.value ? '会话状态未确认，请先重试撤销。' : '未能开启会话，请重新检查租户授权。';
      emit('engaged', exitPending.value);
    }
  } finally { busy.value = false; }
}

async function read() {
  if (busy.value || !accessSecret || !sessionId || disposed) return;
  if (!(Date.now() < deadline)) { expire(); return; }
  result.value = undefined;
  message.value = '';
  error.value = '';
  const supplierId = supplier.value.trim();
  if (!/^[1-9][0-9]{0,18}$/.test(supplierId) || BigInt(supplierId) > 9223372036854775807n) {
    error.value = '请输入有效的供应商 ID。';
    return;
  }
  busy.value = true;
  try {
    const r = await client.POST('/console/support-sessions/{sessionId}/tenants/{tenantId}/suppliers/{supplierId}/read', {
      params: { path: { sessionId, tenantId: props.tenantId, supplierId } }, body: { accessSecret },
    });
    if (disposed || expired.value) return;
    if (!(Date.now() < deadline)) { expire(); return; }
    if (!r.data?.data) {
      clear();
      exitPending.value = true;
      error.value = '读取未获授权，请撤销会话后重新检查租户授权。';
    } else if (Object.keys(r.data.data).length) result.value = r.data.data;
    else message.value = '未找到可读取的供应商。';
  } catch {
    if (!disposed && !expired.value) error.value = '读取失败，请重试。';
  } finally { busy.value = false; }
}

async function end() {
  if (busy.value || !sessionId || disposed) return;
  busy.value = true;
  clear();
  supplier.value = '';
  error.value = '';
  message.value = '';
  try {
    await revoke(sessionId);
    sessionId = undefined;
    exitPending.value = false;
    if (!disposed) {
      message.value = '会话已撤销。';
      emit('engaged', false);
    }
  } catch {
    exitPending.value = true;
    if (!disposed && !expired.value) error.value = '撤销结果未确认。已停止读取，请重试撤销。';
  } finally { busy.value = false; }
}

function dispose() {
  if (disposed) return;
  disposed = true;
  window.clearTimeout(timer);
  clear();
  emit('engaged', false);
  if (sessionId) void revoke(sessionId).catch(() => {});
}
watch(() => props.token, dispose, { flush: 'sync' });
onBeforeUnmount(dispose);
</script>

<template>
  <section class="support-session" aria-label="供应商只读支持会话">
    <h4>供应商只读支持会话</h4>
    <p>目标租户：{{ tenantName }} · 操作人：{{ operatorName }} · 截止：{{ expiresAt }} UTC</p>
    <p>仅按供应商 ID 读取已批准的基础字段，操作会记录到审计日志。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="message" role="status">{{ message }}</p>
    <button v-if="!active && !exitPending && !expired" type="button" :disabled="busy" @click="start">开启只读会话</button>
    <form v-if="active" @submit.prevent="read">
      <label>供应商 ID <input v-model="supplier" :disabled="busy" required maxlength="19" inputmode="numeric" @input="result=undefined" /></label>
      <button type="submit" :disabled="busy">读取供应商</button>
    </form>
    <dl v-if="result">
      <template v-for="(label, key) in fields" :key="key">
        <dt v-if="result[key] !== undefined">{{ label }}</dt>
        <dd v-if="result[key] !== undefined">{{ result[key] }}</dd>
      </template>
    </dl>
    <button v-if="(active || exitPending) && !expired" type="button" :disabled="busy" @click="end">
      {{ exitPending ? '重试撤销会话' : '撤销会话并退出' }}
    </button>
  </section>
</template>

<style scoped>
.support-session { margin-block: 1rem; padding: 1rem; border: 1px solid var(--el-border-color, #d5dde8); border-radius: 8px; }
.support-session h4 { margin-top: 0; }
.support-session form { display: flex; gap: .75rem; align-items: end; flex-wrap: wrap; margin-block: .75rem; }
.support-session dl { display: grid; grid-template-columns: max-content 1fr; gap: .35rem 1rem; }
.support-session dd { margin: 0; overflow-wrap: anywhere; }
</style>

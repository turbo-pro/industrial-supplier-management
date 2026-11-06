<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { createIsmClient, type operations } from '@ism/api-client';

type Page = operations['listOwnSupportSessions']['responses'][200]['content']['application/json']['data'];
type Row = Page['items'][number];
type LogPage = operations['listOwnSupportAccessLogs']['responses'][200]['content']['application/json']['data'];
const resultLabels = { READ:'读取成功', NOT_VISIBLE:'不可见或不存在', DENIED:'已拒绝' };
const surfaceLabels = { CONSOLE:'Console', SUPPORT_WORKSPACE:'管理端工作台', UNSPECIFIED:'未记录' };
const props = defineProps<{ token: string; requestId: string; disabled: boolean }>();
const client = createIsmClient({ getAccessToken: () => props.token });
const labels: Record<Row['state'], string> = {
  PENDING: '待兑换', ACTIVE: '已兑换 · 读取时复核', EXPIRED: '已到期', REVOKED: '已撤销',
};
const page = ref<Page>();
const logs = ref<LogPage>();
const logSessionId = ref<string>();
const logPage = ref(0);
const selected = ref<Row>();
const busy = ref(false);
const error = ref('');
const notice = ref('');
const now = ref(Date.now());
let version = 0;
let timer: number;

function remaining(row: Row) {
  if (row.state === 'REVOKED') return '—';
  const expires = Date.parse(row.expiresAt.endsWith('Z') ? row.expiresAt : row.expiresAt + 'Z');
  if (!Number.isFinite(expires)) return '—';
  const seconds = Math.max(0, Math.ceil((expires - now.value) / 1000));
  return seconds === 0 ? '已到期' : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
}

async function list(before?: string) {
  if (busy.value || props.disabled) return;
  busy.value = true;
  error.value = '';
  selected.value = undefined;
  page.value = undefined;
  logs.value = undefined;
  logSessionId.value = undefined;
  const started = version;
  try {
    const r = await client.GET('/console/support-requests/{requestId}/sessions', {
      params: { path: { requestId: props.requestId }, query: { before } },
    });
    if (started !== version) return;
    if (r.data) page.value = r.data.data;
    else error.value = r.response.status === 404 ? '当前申请的会话记录不可用。' : '未能读取会话，请重试。';
  } catch {
    if (started === version) error.value = '未能读取会话，请重试。';
  } finally { if (started === version) busy.value = false; }
}

async function loadLogs(sessionId: string, next = 0) {
  if (busy.value || props.disabled) return;
  const started = version;
  busy.value = true;
  error.value = '';
  logSessionId.value = sessionId;
  logPage.value = next;
  logs.value = undefined;
  try {
    const r = await client.GET('/console/support-sessions/{sessionId}/access-logs', {
      params: { path: { sessionId }, query: { page: next } },
    });
    if (started !== version) return;
    if (r.data) logs.value = r.data.data;
    else error.value = '未能读取访问日志，请重试。';
  } catch {
    if (started === version) error.value = '未能读取访问日志，请重试。';
  } finally { if (started === version) busy.value = false; }
}

async function revoke() {
  if (!selected.value || busy.value || props.disabled) return;
  const id = selected.value.id, started = version;
  busy.value = true;
  error.value = '';
  notice.value = '';
  try {
    const r = await client.POST('/console/support-sessions/{sessionId}/revoke', { params: { path: { sessionId: id } } });
    if (started !== version) return;
    if (!r.data?.data) throw Error('Revocation unconfirmed');
    notice.value = '会话已撤销，正在刷新记录。';
  } catch {
    if (started === version) error.value = '撤销结果未确认，可重试同一会话。';
    return;
  } finally { if (started === version) busy.value = false; }
  await list();
  if (started === version) notice.value = '会话已撤销。';
}

watch(() => props.token, () => {
  ++version;
  page.value = undefined;
  logs.value = undefined;
  logSessionId.value = undefined;
  selected.value = undefined;
  busy.value = false;
  error.value = '';
  notice.value = '';
});
onMounted(() => { timer = window.setInterval(() => { now.value = Date.now(); }, 1000); });
onBeforeUnmount(() => { ++version; window.clearInterval(timer); });
</script>

<template>
  <section class="session-history" aria-label="支持会话记录">
    <h4>支持会话记录</h4>
    <p>可重新打开本申请查询会话并撤销。会话是否能读取数据，以服务端实时授权检查为准。</p>
    <div class="controls">
      <button type="button" :disabled="busy || disabled" @click="list()">读取最新会话记录</button>
      <button v-if="page?.nextBefore" type="button" :disabled="busy || disabled" @click="list(page.nextBefore)">较早会话</button>
    </div>
    <p v-if="busy" role="status">正在处理会话记录…</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="page && !page.items.length">暂无会话。</p>
    <div v-if="page?.items.length" class="table-scroll">
      <table>
        <thead><tr><th scope="col">会话 ID</th><th scope="col">状态</th><th scope="col">剩余时间</th><th scope="col">截止（UTC）</th><th scope="col">操作</th></tr></thead>
        <tbody>
          <tr v-for="row in page.items" :key="row.id">
            <td>{{ row.id }}</td><td>{{ labels[row.state] }}</td><td>{{ remaining(row) }}</td><td>{{ row.expiresAt }}</td>
            <td><button type="button" :disabled="busy || disabled" @click="loadLogs(row.id)">查看访问日志</button> <button v-if="row.state==='ACTIVE' || row.state==='PENDING'" type="button" :disabled="busy || disabled" @click="selected=row;error='';notice=''">撤销此会话</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <section v-if="logSessionId" aria-label="会话访问日志" class="log-section">
      <h5>会话 {{ logSessionId }} 的访问日志</h5>
      <p>仅展示可核对归属的供应商读取事件。</p>
      <p v-if="logs && !logs.items.length">本页没有访问记录。</p>
      <div v-if="logs?.items.length" class="table-scroll">
        <table>
          <thead><tr><th scope="col">发生时间（UTC）</th><th scope="col">供应商 ID</th><th scope="col">入口</th><th scope="col">结果</th><th scope="col">追踪号</th></tr></thead>
          <tbody><tr v-for="event in logs.items" :key="event.id">
            <td>{{ event.occurredAt }}</td><td>{{ event.objectId }}</td><td>{{ surfaceLabels[event.surface] }}</td>
            <td>{{ resultLabels[event.result] }}</td><td>{{ event.traceId || '—' }}</td>
          </tr></tbody>
        </table>
      </div>
      <div class="controls">
        <button type="button" :disabled="busy || disabled" @click="loadLogs(logSessionId, logPage)">重新读取日志</button>
        <button v-if="logPage>0" type="button" :disabled="busy || disabled" @click="loadLogs(logSessionId, logPage-1)">上一页日志</button>
        <button v-if="logs?.hasMore" type="button" :disabled="busy || disabled" @click="loadLogs(logSessionId, logPage+1)">下一页日志</button>
      </div>
    </section>
    <div v-if="selected" class="controls" aria-label="确认撤销">
      <p>撤销会话 {{ selected.id }} 后，已开启的工作台将失去访问权限。</p>
      <button type="button" :disabled="busy || disabled" @click="revoke">确认撤销会话</button>
      <button type="button" :disabled="busy" @click="selected=undefined;error=''">取消</button>
    </div>
  </section>
</template>

<style scoped>
.session-history { margin-block: 1rem; padding: 1rem; border: 1px solid var(--el-border-color, #d5dde8); border-radius: 8px; }
.log-section { margin-top: 1rem; }
.log-section h5 { font-size: 1rem; margin-block: .5rem; }
.session-history h4 { margin-top: 0; }
.controls { display: flex; align-items: center; gap: .5rem; flex-wrap: wrap; }
.table-scroll { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; margin-top: .5rem; }
th, td { text-align: left; padding: .5rem; border-bottom: 1px solid var(--el-border-color, #d5dde8); white-space: nowrap; }
</style>

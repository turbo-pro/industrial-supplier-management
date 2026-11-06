<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { createIsmClient, type operations } from '@ism/api-client';

type Identity = operations['getSupportWorkspaceIdentity']['responses'][200]['content']['application/json']['data'];
const client = createIsmClient();
const fields = { id: 'ID', code: '编码', name: '名称', type: '类型', status: '状态' };
const identity = ref<Identity>();
const result = ref<Partial<Record<keyof typeof fields, string>>>();
const supplier = ref('');
const loading = ref(false);
const error = ref('');
const notice = ref('正在等待 Console 交接…');
const heading = ref<HTMLElement>();
const scope = computed(() => identity.value?.scope);
let sessionId: string | undefined;
let accessSecret: string | undefined;
let browserSecret: string | undefined;
let opener: Window | null = window.opener;
let port: MessagePort | undefined;
let waiting: number | undefined;
let poll: number | undefined;
let expiry: number | undefined;
let stopped = false;
let checking = false;
const consoleOrigin = trustedOrigin(import.meta.env.VITE_ISM_SUPPORT_CONSOLE_ORIGIN);

function trustedOrigin(value: string | undefined) {
  try {
    const url = new URL(value ?? '');
    if (url.origin === value && (url.protocol === 'https:' ||
        (url.protocol === 'http:' && ['127.0.0.1', 'localhost'].includes(url.hostname)))) return url.origin;
  } catch { /* Missing deployment configuration. */ }
  return undefined;
}

function deadline(value: string) {
  return Date.parse(value.endsWith('Z') ? value : value + 'Z');
}

function stop(text: string, ended = false) {
  if (stopped) return;
  stopped = true;
  window.clearTimeout(waiting);
  window.clearInterval(poll);
  window.clearTimeout(expiry);
  window.removeEventListener('message', receive);
  opener = null;
  window.opener = null;
  port?.postMessage({ type: ended ? 'ISM_SUPPORT_EXITED_V1' : 'ISM_SUPPORT_DISCONNECTED_V1' });
  port?.close();
  port = undefined;
  browserSecret = undefined;
  accessSecret = undefined;
  identity.value = undefined;
  result.value = undefined;
  supplier.value = '';
  error.value = '';
  notice.value = text;
  heading.value?.focus();
}

async function receive(event: MessageEvent) {
  if (stopped || !opener || event.origin !== consoleOrigin || event.source !== opener ||
      event.data?.type !== 'ISM_SUPPORT_ENTRY_V1' || !event.ports[0] ||
      typeof event.data.sessionId !== 'string' || !/^[1-9][0-9]{0,18}$/.test(event.data.sessionId) ||
      typeof event.data.entryToken !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(event.data.entryToken) ||
      !browserSecret) return;
  window.removeEventListener('message', receive);
  window.clearTimeout(waiting);
  port = event.ports[0];
  opener = null;
  window.opener = null;
  const id: string = event.data.sessionId;
  sessionId = id;
  const browser = browserSecret;
  browserSecret = undefined;
  try {
    const r = await client.POST('/support-workspace/{sessionId}/exchange', {
      params: { path: { sessionId: id } }, body: { entryToken: event.data.entryToken, browserSecret: browser },
    });
    if (stopped) return;
    if (!r.data?.data.accessSecret) throw Error('Invalid exchange');
    accessSecret = r.data.data.accessSecret;
    await refresh();
    if (!stopped) {
      notice.value = '';
      port?.postMessage({ type: 'ISM_SUPPORT_ATTACHED_V1' });
      poll = window.setInterval(() => void refresh(), 5000);
    }
  } catch { stop('交接失败。请回到 Console 重新开启会话。'); }
}

async function refresh() {
  if (stopped || checking || !sessionId || !accessSecret) return;
  checking = true;
  try {
    const r = await client.POST('/support-workspace/{sessionId}/identity', {
      params: { path: { sessionId } }, body: { accessSecret },
    });
    if (stopped || !accessSecret) return;
    const next = r.data?.data;
    if (!next || next.realm !== 'SUPPORT_WORKSPACE' || !(Date.now() < deadline(next.expiresAt))) {
      stop('会话已失效，已清除读取结果。');
      return;
    }
    if (identity.value && JSON.stringify(identity.value.scope) !== JSON.stringify(next.scope)) result.value = undefined;
    identity.value = next;
    window.clearTimeout(expiry);
    expiry = window.setTimeout(() => stop('会话已到期，已清除读取结果。'), deadline(next.expiresAt) - Date.now());
  } catch { stop('未能核验会话，已停止读取。请返回 Console。'); }
  finally { checking = false; }
}

async function read() {
  if (stopped || loading.value || !identity.value || !sessionId || !accessSecret) return;
  if (!(Date.now() < deadline(identity.value.expiresAt))) { stop('会话已到期，已清除读取结果。'); return; }
  result.value = undefined;
  notice.value = '';
  error.value = '';
  const supplierId = supplier.value.trim();
  if (!/^[1-9][0-9]{0,18}$/.test(supplierId) || BigInt(supplierId) > 9223372036854775807n) {
    error.value = '请输入有效的供应商 ID。';
    return;
  }
  const startingScope = JSON.stringify(identity.value.scope);
  loading.value = true;
  try {
    const r = await client.POST('/support-workspace/{sessionId}/suppliers/{supplierId}/read', {
      params: { path: { sessionId, supplierId } }, body: { accessSecret },
    });
    if (stopped || !accessSecret || !identity.value) return;
    if (!(Date.now() < deadline(identity.value.expiresAt))) { stop('会话已到期，已清除读取结果。'); return; }
    if (!r.data?.data) stop('读取未获授权，已清除会话。请返回 Console。');
    else if (startingScope !== JSON.stringify(identity.value.scope)) notice.value = '授权范围已变化，请重新读取。';
    else if (Object.keys(r.data.data).length) result.value = r.data.data;
    else notice.value = '未找到可读取的供应商。';
  } catch { stop('读取失败，已停止会话。请返回 Console。'); }
  finally { loading.value = false; }
}

async function exit() {
  if (stopped || !sessionId || !accessSecret) return;
  const id = sessionId, secret = accessSecret;
  accessSecret = undefined;
  identity.value = undefined;
  result.value = undefined;
  notice.value = '正在撤销会话…';
  try {
    const r = await client.POST('/support-workspace/{sessionId}/exit', {
      params: { path: { sessionId: id } }, body: { accessSecret: secret },
    });
    if (r.data?.data.status === 'REVOKED') stop('会话已撤销，可关闭此窗口。', true);
    else stop('已停止读取。请返回 Console 确认撤销。');
  } catch { stop('已停止读取。请返回 Console 确认撤销。'); }
}

function leave() { stop('会话已关闭。'); }
onMounted(() => {
  if (!opener || !consoleOrigin) { stop('请从 Console 的支持申请打开此工作台。'); return; }
  browserSecret = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32))))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  window.addEventListener('pagehide', leave);
  window.addEventListener('message', receive);
  waiting = window.setTimeout(() => stop('交接超时。请返回 Console 重新开启会话。'), 25_000);
  opener.postMessage({ type: 'ISM_SUPPORT_READY_V1', browserSecret }, consoleOrigin);
});
onBeforeUnmount(() => { window.removeEventListener('pagehide', leave); leave(); });
</script>

<template>
  <header class="identity" aria-label="支持会话身份">
    <div class="identity-content">
      <div>
        <p class="eyebrow">管理端 · 受限只读</p>
        <h1 ref="heading" tabindex="-1">供应商支持工作台</h1>
        <p v-if="identity">租户：{{ identity.tenantName }} · 操作人：{{ identity.operatorName }} · 截止：{{ identity.expiresAt }} UTC</p>
        <p v-if="scope" class="eyebrow">范围：{{ scope.tenantAll ? '租户全部' : `${scope.organizations.length} 个组织` }} · {{ scope.fields.length }} 个基础字段</p>
      </div>
      <el-button v-if="identity" plain type="danger" @click="exit">撤销会话并退出</el-button>
    </div>
  </header>
  <main class="workspace">
    <section v-if="identity && scope" class="card" aria-labelledby="lookup-title">
      <h2 id="lookup-title">按 ID 读取供应商</h2>
      <p>可读取字段：{{ scope.fields.map(field => fields[field as keyof typeof fields] ?? field).join('、') }}。</p>
      <p>组织范围：{{ scope.tenantAll ? '租户全部' : scope.organizations.join('、') }}。读取记录将写入审计。</p>
      <form @submit.prevent="read">
        <label for="supplier-id">供应商 ID</label>
        <div class="query">
          <el-input id="supplier-id" v-model="supplier" inputmode="numeric" maxlength="19" :disabled="loading" @input="result=undefined" />
          <el-button native-type="submit" type="primary" :loading="loading">读取供应商</el-button>
        </div>
      </form>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <dl v-if="result" aria-label="已批准的供应商字段">
        <template v-for="(label, key) in fields" :key="key">
          <dt v-if="result[key] !== undefined">{{ label }}</dt>
          <dd v-if="result[key] !== undefined">{{ result[key] }}</dd>
        </template>
      </dl>
    </section>
    <p v-if="notice" role="status" class="card">{{ notice }}</p>
  </main>
</template>

<style>
:root { color-scheme: light; --support-ink:#172b42; --support-muted:#526477; --support-line:#dce4ed; --support-brand:#0e7490; }
body { margin:0; color:var(--support-ink); background:#f3f6fa; font: 16px/1.55 system-ui, "PingFang SC", "Microsoft YaHei", sans-serif; }
.identity { position:sticky; top:0; z-index:1; background:#14263d; color:#fff; }
.identity-content, .workspace { max-width: 58rem; margin:auto; padding: 1rem clamp(1rem, 4vw, 2rem); }
.identity-content { display:flex; gap:.75rem 1.5rem; align-items:center; justify-content:space-between; flex-wrap:wrap; }
h1 { margin:.2rem 0; font-size:1.5rem; } h2 { margin:0; font-size:1.2rem; }
p { margin:.4rem 0; overflow-wrap:anywhere; }
.eyebrow { color:#b7e4ed; font-size:.875rem; }
.card { background:#fff; border:1px solid var(--support-line); border-radius:12px; padding:clamp(1rem, 4vw, 1.5rem); }
.card + .card { margin-top:1rem; }
.card p { color:var(--support-muted); }
form { margin-top:1rem; } label { display:block; margin-bottom:.35rem; font-weight:600; }
.query { display:flex; gap:.5rem; flex-wrap:wrap; }
.query .el-input { flex:1 1 14rem; --el-input-height:44px; --el-input-focus-border-color:var(--support-brand); }
.identity .el-button--danger.is-plain { --el-button-text-color:#b42332; --el-button-hover-bg-color:#b42332; --el-button-hover-border-color:#b42332; }
.el-button { min-height:44px; } .el-button--primary { --el-button-bg-color:var(--support-brand); --el-button-border-color:var(--support-brand); }
dl { display:grid; grid-template-columns:max-content 1fr; gap:.5rem 1rem; border-top:1px solid var(--support-line); padding-top:1rem; }
dd { margin:0; overflow-wrap:anywhere; } .card .error { color:#b42332; }
:focus-visible { outline:2px solid #1586aa; outline-offset:3px; }
</style>

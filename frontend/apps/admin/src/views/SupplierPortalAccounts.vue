<script setup lang="ts">
import { nextTick, ref } from 'vue';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
type Account = components['schemas']['PortalManagedAccount'];
const props = defineProps<{ supplierId: string; supplierStatus: string }>();
const session = useSessionStore();
const expanded = ref(false), busy = ref(false), loading = ref(false), error = ref(''), notice = ref(''), reason = ref('');
const errorBox = ref<HTMLElement>();
const view = ref<components['schemas']['PortalAccountPageResponse']['data']>();
const page = ref(1);
const target = ref<{ account: Account; action: 'ENABLE' | 'DISABLE' | 'REVOKE' }>();
const labels = { ENABLE: '恢复账号', DISABLE: '停用账号', REVOKE: '撤销全部会话' };
async function fail(message: string) { error.value = message; await nextTick(); errorBox.value?.focus(); }
async function load(requested = page.value) {
  loading.value = true; error.value = ''; target.value = undefined;
  try {
    const result = await session.client.GET('/suppliers/{supplierId}/portal-accounts', { params: { path: { supplierId: props.supplierId }, query: { page: requested, size: 20 } } });
    if (result.error) { view.value = undefined; return await fail(result.error.error.message); }
    view.value = result.data.data; page.value = requested;
  } catch { view.value = undefined; await fail('门户账号读取失败，请重试；读取失败不代表没有账号。'); }
  finally { loading.value = false; }
}
async function open() { expanded.value = !expanded.value; notice.value = ''; target.value = undefined; if (expanded.value) await load(); }
function select(account: Account, action: 'ENABLE' | 'DISABLE' | 'REVOKE') { target.value = { account, action }; reason.value = ''; error.value = ''; notice.value = ''; }
async function save() {
  if (!target.value || busy.value || loading.value || !view.value?.canManage) return;
  if (!reason.value.trim()) return fail('请填写操作原因。');
  const selected = target.value;
  busy.value = true; error.value = ''; notice.value = '';
  const params = { path: { supplierId: props.supplierId, id: selected.account.id } };
  const body = { version: selected.account.version, reason: reason.value.trim() };
  try {
    const result = selected.action === 'REVOKE'
      ? await session.client.POST('/suppliers/{supplierId}/portal-accounts/{id}/revoke-sessions', { params, body })
      : await session.client.POST('/suppliers/{supplierId}/portal-accounts/{id}/status', { params, body: { ...body, status: selected.action === 'ENABLE' ? 'ACTIVE' : 'DISABLED' } });
    if (result.error) { await load(); return await fail(`${result.error.error.message}；请核对最新状态后重新选择操作。`); }
    notice.value = `${labels[selected.action]}成功，原有会话均已撤销。`;
    await load();
  } catch {
    view.value = undefined; target.value = undefined;
    await fail('操作结果未确认，请刷新账号列表核查后再决定是否重试，勿连续提交。');
  } finally { busy.value = false; }
}
</script>
<template>
  <section class="portal-accounts" aria-label="供应商门户账号">
    <h3>门户账号 <el-button :aria-expanded="expanded" :disabled="busy || loading" @click="open">{{ expanded ? '收起账号管理' : '门户账号管理' }}</el-button></h3>
    <template v-if="expanded">
      <p>仅管理当前供应商的本地门户账号，不代表门禁或其他外部系统已回收。停用/恢复均撤销原会话；仅撤销会话不会禁止再次登录。</p>
      <p v-if="error" ref="errorBox" role="alert" tabindex="-1">{{ error }}</p>
      <p v-if="notice" role="status">{{ notice }}</p>
      <p v-if="loading" role="status">正在读取门户账号…</p>
      <template v-if="view">
        <p v-if="!view.canManage">当前账号只读，无权调整门户账号或撤销会话。</p>
        <div class="account-table"><el-table :data="view.accounts.items" empty-text="当前供应商暂无门户账号">
          <el-table-column prop="username" label="用户名" min-width="150" />
          <el-table-column prop="displayName" label="显示名称" min-width="130" />
          <el-table-column label="状态" min-width="90"><template #default="{row}">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</template></el-table-column>
          <el-table-column prop="liveSessions" label="未到期会话数" min-width="130" />
          <el-table-column label="上次登录（UTC）" min-width="210"><template #default="{row}">{{ row.lastLoginAt || '尚未登录' }}</template></el-table-column>
          <el-table-column label="操作" min-width="260"><template #default="{row}"><template v-if="view.canManage">
            <el-button v-if="row.status === 'ACTIVE'" :disabled="busy || loading" @click="select(row,'DISABLE')">停用账号</el-button>
            <el-button v-else :disabled="busy || loading || !['DRAFT','ACTIVE'].includes(supplierStatus)" @click="select(row,'ENABLE')">恢复账号</el-button>
            <el-button :disabled="busy || loading" @click="select(row,'REVOKE')">撤销全部会话</el-button>
          </template><span v-else>只读</span></template></el-table-column>
        </el-table></div>
        <p>共 {{ view.accounts.total }} 个账号，第 {{ view.accounts.page }} 页。会话数不代表业务资格；恢复账号不解除密码错误临时锁，不恢复旧会话。</p>
        <el-button :disabled="busy || loading || page <= 1" @click="load(page-1)">上一页账号</el-button>
        <el-button :disabled="busy || loading || page * view.accounts.size >= view.accounts.total" @click="load(page+1)">下一页账号</el-button>
        <el-form v-if="target" label-position="top" :disabled="busy || loading" @submit.prevent="save">
          <h4>确认{{ labels[target.action] }}：{{ target.account.username }}</h4>
          <p>{{ target.action === 'DISABLE' ? '此账号将不能登录，全部原有会话失效。' : target.action === 'ENABLE' ? '允许重新登录，但仍须通过租户、供应商和密码锁检查。旧会话不会恢复。' : '全部原有会话立即失效；账号仍可用密码重新登录。如需阻止登录，请使用停用。' }}</p>
          <el-form-item label="操作原因（必填）" :error="error && !reason.trim() ? '请填写操作原因' : undefined"><el-input v-model="reason" type="textarea" maxlength="500" aria-label="门户账号操作原因" /></el-form-item>
          <el-button native-type="submit" type="danger" :loading="busy">确认{{ labels[target.action] }}</el-button><el-button :disabled="busy || loading" @click="target=undefined">取消账号操作</el-button>
        </el-form>
      </template>
      <el-button :disabled="busy || loading" @click="load()">刷新门户账号</el-button>
    </template>
  </section>
</template>
<style scoped>
.portal-accounts{margin-top:24px;min-width:0}.portal-accounts h3{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.portal-accounts p{overflow-wrap:anywhere}.account-table{overflow:auto;margin:16px 0}.portal-accounts :deep(.el-button){min-height:44px;margin-top:8px}.portal-accounts form{margin:16px 0}.portal-accounts [role=alert]:focus-visible{outline:2px solid var(--ism-brand);outline-offset:4px}
</style>

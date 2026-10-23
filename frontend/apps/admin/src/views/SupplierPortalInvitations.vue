<script setup lang="ts">
import { nextTick, ref } from 'vue';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
import SupplierPortalAccounts from './SupplierPortalAccounts.vue';
import SupplierProfileChanges from './SupplierProfileChanges.vue';
const emit = defineEmits<{ changed: [] }>();
const props = defineProps<{ supplierId: string; supplierStatus: string }>();
const session = useSessionStore();
const expanded = ref(false), busy = ref(false), loading = ref(false), error = ref(''), token = ref('');
const errorBox = ref<HTMLElement>();
const history = ref<components['schemas']['PortalInvitationHistoryResponse']['data']>();
const name = ref(''), email = ref(''), hours = ref(24), reason = ref('');
const target = ref<components['schemas']['PortalInvitation']>();
const labels = { PENDING: '待使用', EXPIRED: '已过期', REPLACED: '已被重发替代', REVOKED: '已撤销', ACCEPTED: '已注册兑换' };
async function fail(message: string) { error.value = message; await nextTick(); errorBox.value?.focus(); }
async function load() {
  loading.value = true; error.value = '';
  try {
    const response = await session.client.GET('/suppliers/{supplierId}/portal-invitations', { params: { path: { supplierId: props.supplierId } } });
    if (response.error) { history.value = undefined; await fail(response.error.error.message); return; }
    history.value = response.data.data;
  } catch { history.value = undefined; await fail('邀请记录读取失败，请重试'); }
  finally { loading.value = false; }
}
async function open() { expanded.value = !expanded.value; token.value = ''; target.value = undefined; if (expanded.value) await load(); }
async function issue() {
  if (!history.value?.canManage || busy.value) return;
  if (!name.value.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) { await fail('请填写收件人姓名和有效邮箱'); return; }
  busy.value = true; error.value = ''; token.value = '';
  try {
    const response = await session.client.POST('/suppliers/{supplierId}/portal-invitations', { params: { path: { supplierId: props.supplierId } }, body: { expectedGeneration: history.value.latestGeneration, recipientName: name.value.trim(), recipientEmail: email.value.trim(), validHours: hours.value } });
    if (response.error) { await load(); await fail(response.error.error.message); return; }
    token.value = response.data.data.token;
    await load();
  } catch { await fail('签发结果未确认，请先刷新记录；令牌无法再次取回，必要时重新签发使旧邀请失效'); history.value = undefined; }
  finally { busy.value = false; }
}
async function revoke() {
  if (!target.value || !history.value?.canManage || busy.value) return;
  if (!reason.value.trim()) { await fail('撤销必须填写原因'); return; }
  busy.value = true; error.value = '';
  try {
    const response = await session.client.POST('/suppliers/{supplierId}/portal-invitations/{id}/revoke', { params: { path: { supplierId: props.supplierId, id: target.value.id } }, body: { version: target.value.version, reason: reason.value.trim() } });
    token.value = ''; target.value = undefined; await load();
    if (response.error) await fail(response.error.error.message);
  } catch { await fail('撤销结果未确认，请刷新记录后重试'); }
  finally { busy.value = false; }
}
</script>
<template>
  <section aria-label="供应商门户邀请" class="portal-invitations">
    <h3>门户邀请 <el-button :disabled="busy" :aria-expanded="expanded" @click="open">{{ expanded ? '收起邀请管理' : '门户邀请管理' }}</el-button></h3>
    <template v-if="expanded">
      <el-alert title="门户注册与认证默认关闭，需部署方启用后使用独立门户页面；不会发送邮件或自动批准准入。" type="info" :closable="false" />
      <p v-if="error" ref="errorBox" role="alert" tabindex="-1">{{ error }}</p>
      <p v-if="loading" role="status">正在读取邀请记录…</p>
      <div v-if="token" class="invitation-secret"><strong>本次令牌仅展示一次，请安全保管，不要写入日志或公开转发。</strong><p><code>{{ token }}</code></p><el-button @click="token=''">隐藏令牌</el-button></div>
      <template v-if="history">
        <p v-if="!history.canManage">当前账号只读，无权签发或撤销邀请。</p>
        <el-form v-else label-position="top" :disabled="busy || loading" @submit.prevent="issue">
          <el-form-item label="收件人姓名（必填）" :error="error && !name.trim() ? '请填写姓名' : undefined"><el-input v-model="name" maxlength="100" aria-label="邀请收件人姓名" /></el-form-item>
          <el-form-item label="收件人邮箱（必填）" :error="error && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) ? '请填写有效邮箱' : undefined"><el-input v-model="email" maxlength="254" type="email" aria-label="邀请收件人邮箱" /></el-form-item>
          <el-form-item label="有效时长（1–168 小时）"><el-input-number v-model="hours" :min="1" :max="168" :precision="0" aria-label="邀请有效小时" /></el-form-item>
          <el-button native-type="submit" type="primary" :loading="busy" :disabled="!['DRAFT','ACTIVE'].includes(supplierStatus)">签发新邀请（旧邀请失效）</el-button>
        </el-form>
        <p>显示最近 100 条；时间为 UTC。重新签发不会发送邮件，历史记录不能取回令牌。</p>
        <div class="invitation-table"><el-table :data="history.items" empty-text="暂无邀请记录">
          <el-table-column prop="generation" label="批次" width="75" />
          <el-table-column prop="recipientName" label="收件人" min-width="100" />
          <el-table-column prop="recipientEmail" label="邮箱" min-width="180" />
          <el-table-column label="状态" min-width="140"><template #default="{row}">{{ labels[row.status as keyof typeof labels] }}</template></el-table-column>
          <el-table-column prop="expiresAt" label="到期时间（UTC）" min-width="200" />
          <el-table-column label="操作" width="100"><template #default="{row}"><el-button v-if="history.canManage && row.status==='PENDING'" :disabled="busy || loading" @click="target=row;reason='';token=''">撤销邀请</el-button></template></el-table-column>
        </el-table></div>
        <el-form v-if="target" label-position="top" :disabled="busy || loading" @submit.prevent="revoke"><p>确认撤销第 {{ target.generation }} 批邀请？撤销后不能使用。</p><el-form-item label="撤销原因（必填）"><el-input v-model="reason" maxlength="500" type="textarea" aria-label="邀请撤销原因" /></el-form-item><el-button native-type="submit" type="danger" :loading="busy">确认撤销邀请</el-button><el-button :disabled="busy" @click="target=undefined">取消撤销</el-button></el-form>
      </template>
      <el-button :disabled="busy || loading" @click="load">刷新邀请记录</el-button>
    </template>
    <SupplierPortalAccounts :key="supplierId" :supplier-id="supplierId" :supplier-status="supplierStatus" />
    <SupplierProfileChanges :key="'changes-' + supplierId" :supplier-id="supplierId" @changed="emit('changed')" />
  </section>
</template>
<style scoped>
.portal-invitations{margin-top:24px}.portal-invitations h3{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.portal-invitations p{overflow-wrap:anywhere}.invitation-table{overflow:auto;margin:16px 0}.invitation-secret{padding:16px;margin:16px 0;border:1px solid var(--ism-border);background:var(--ism-brand-soft);border-radius:8px}.invitation-secret code{overflow-wrap:anywhere}.portal-invitations :deep(.el-button){min-height:44px;margin-top:8px}.portal-invitations form{margin:16px 0}
</style>

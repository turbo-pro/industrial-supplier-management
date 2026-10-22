<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
const props = defineProps<{ supplierId: string; account: components['schemas']['PortalManagedAccount'] }>();
const emit = defineEmits<{ close: [] }>();
const session = useSessionStore();
const busy = ref(false), error = ref(''), reason = ref(''), verified = ref(false), uncertain = ref(false), revealed = ref(false);
const loaded = ref(false), errorPanel = ref<HTMLElement>();
watch(error, async value => { if (value) { await nextTick(); errorPanel.value?.focus(); } });
const version = ref(props.account.version);
const issued = ref<components['schemas']['PortalRecoveryIssuedResponse']['data']>();
const state = ref<components['schemas']['PortalRecoveryStateResponse']['data']>();
const params = { path: { supplierId: props.supplierId, id: props.account.id } };
const statuses: Record<string, string> = { PENDING: '待使用', EXPIRED: '已过期', INVALIDATED: '账号变更已使凭证失效', CONSUMED: '已使用', REVOKED: '已撤销', REPLACED: '已替换' };
async function load() {
  busy.value = true; error.value = '';
  try {
    const result = await session.client.GET('/suppliers/{supplierId}/portal-accounts/{id}/password-recovery', { params });
    if (result.error) { state.value = undefined; loaded.value = false; error.value = result.error.error.message; }
    else { state.value = result.data.data; loaded.value = true; }
  } catch { state.value = undefined; loaded.value = false; error.value = '读取失败，不代表没有恢复凭证。请重试读取。'; }
  finally { busy.value = false; }
}
async function save(revoke = false) {
  if (busy.value || uncertain.value) return;
  if (!reason.value.trim() || (!revoke && !verified.value)) { error.value = '请填写原因；签发前必须通过可信渠道核实账号持有人身份。'; return; }
  busy.value = true; error.value = ''; issued.value = undefined; revealed.value = false;
  const body = { version: version.value, reason: reason.value.trim() };
  try {
    if (revoke) {
      const result = await session.client.POST('/suppliers/{supplierId}/portal-accounts/{id}/password-recovery/revoke', { params, body });
      if (result.error) { error.value = result.error.error.message + '；请关闭并刷新账号后核查。'; uncertain.value = true; return; }
      emit('close');
    } else {
      const result = await session.client.POST('/suppliers/{supplierId}/portal-accounts/{id}/password-recovery', { params, body: { ...body, identityVerified: true } });
      if (result.error) { error.value = result.error.error.message + '；请关闭并刷新账号后核查。'; uncertain.value = true; return; }
      issued.value = result.data.data; version.value = result.data.data.accountVersion;
      await load();
    }
  } catch { uncertain.value = true; error.value = '操作结果未确认。不要连续签发；请关闭并刷新账号及凭证状态。若凭证遗失，可核查后重新签发以替换。'; }
  finally { busy.value = false; }
}
onMounted(load);
</script>
<template>
  <section class="recovery" aria-label="门户密码恢复" :aria-busy="busy">
    <h4>恢复门户密码：{{ account.username }}</h4>
    <p>先通过合同约定联系人等可信渠道核实身份，不得仅凭未验证邮箱。凭证有效 15 分钟，重新签发会替换旧凭证。签发不修改密码或撤销会话；疑似被盗请先停用账号。其他账号操作也可能使凭证失效。</p>
    <p v-if="error" ref="errorPanel" role="alert" tabindex="-1">{{ error }}</p><p v-if="busy" role="status">正在处理恢复凭证…</p>
    <p v-if="state">最近凭证：{{ statuses[state.status] || state.status }}；到期时间（UTC）：{{ state.expiresAt }}。</p>
    <p v-else-if="!busy && !error">尚未签发恢复凭证。</p>
    <template v-if="issued">
      <p role="status">恢复凭证已签发，仅本次响应可见。请通过可信渠道安全交给本人；关闭后无法再读取。</p>
      <label>一次性恢复凭证<el-input :model-value="issued.token" :type="revealed ? 'text' : 'password'" readonly autocomplete="off" aria-label="一次性恢复凭证" /></label>
      <el-button :aria-pressed="revealed" @click="revealed = !revealed">{{ revealed ? '隐藏恢复凭证' : '显示恢复凭证' }}</el-button>
      <el-button @click="issued=undefined;revealed=false">清除本页凭证（不撤销）</el-button>
    </template>
    <el-form label-position="top" :disabled="busy || uncertain || !loaded" @submit.prevent="save()">
      <el-form-item label="身份核实及操作原因（必填）" :error="error && !reason.trim() ? '请填写核实及操作原因' : undefined"><el-input v-model="reason" type="textarea" maxlength="500" aria-label="密码恢复操作原因" /></el-form-item>
      <el-checkbox v-model="verified" aria-label="已通过可信渠道核实身份">已通过可信渠道核实账号持有人身份</el-checkbox>
      <div><el-button native-type="submit" type="danger" :loading="busy" :disabled="busy || uncertain || !loaded">确认签发或替换恢复凭证</el-button><el-button :disabled="busy || uncertain || !loaded || !state || !['PENDING','EXPIRED','INVALIDATED'].includes(state.status)" @click="save(true)">确认撤销恢复凭证</el-button></div>
    </el-form>
    <el-button :disabled="busy" @click="load">重读恢复状态</el-button><el-button :disabled="busy" @click="emit('close')">关闭密码恢复</el-button>
  </section>
</template>
<style scoped>
.recovery{padding:16px;border:1px solid var(--ism-border);border-radius:8px;margin:16px 0;min-width:0}.recovery p{overflow-wrap:anywhere}.recovery label{display:block}.recovery :deep(.el-button){min-height:44px;margin-top:8px;white-space:normal}.recovery :deep(.el-checkbox){height:auto;min-height:44px;white-space:normal}.recovery :deep(.el-checkbox__label){white-space:normal}
</style>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue';
import { createIsmClient, type components } from '@ism/api-client';

type Identity = components['schemas']['PortalIdentity'];
const mode = ref<'login' | 'register'>('login');
const identity = ref<Identity>();
// Session secrets stay in memory: reload requires login; no shared admin storage.
let accessToken: string | undefined;
const client = createIsmClient({ getAccessToken: () => accessToken });
const form = reactive({ tenantCode: '', username: '', password: '', invitationToken: '', confirmation: '' });
const busy = ref(false);
const error = ref('');
const notice = ref('');
const errorPanel = ref<HTMLElement>();
async function fail(message: string) { error.value = message; await nextTick(); errorPanel.value?.focus(); }
function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login';
  form.password = ''; form.confirmation = ''; form.invitationToken = ''; error.value = ''; notice.value = '';
}
function responseError(status: number, registration = false): string {
  if (status === 429) return '请求过于频繁，请稍后再试。请勿连续重复提交。';
  if (status === 401) return '登录凭证无效或账号暂不可用，请核对租户编码、用户名和密码。';
  if (status === 409) return '账号或邮箱已绑定，请尝试登录，或联系邀请方核查。';
  if (status === 422 || status === 403) return registration
    ? '注册未获允许：邀请可能失效或已使用，或部署方尚未开放注册。请联系邀请方。'
    : '当前账号或部署暂不允许访问，请联系邀请方。';
  if (status === 400) return '提交内容不符合要求，请核对字段格式。';
  return '服务暂不可用，请稍后重试；不要连续提交。';
}
async function submit() {
  if (busy.value) return;
  error.value = ''; notice.value = '';
  if (!/^[A-Za-z][A-Za-z0-9_.-]{2,63}$/.test(form.username.trim())) return fail('用户名需以英文字母开头，共 3–64 位，只能包含字母、数字、下划线、点和连字符。');
  if (new TextEncoder().encode(form.password).length > 72) return fail('密码不能超过 72 个 UTF-8 字节。');
  const registering = mode.value === 'register';
  if (registering && (!/^[A-Za-z0-9_-]{43}$/.test(form.invitationToken.trim()) || form.password !== form.confirmation)) return fail('请填写完整的 43 位邀请凭证，并确保两次密码一致。');
  if (registering && (form.password.length < 12 || !/[A-Z]/.test(form.password) || !/[a-z]/.test(form.password) || !/[0-9]/.test(form.password) || !/[^A-Za-z0-9]/.test(form.password))) return fail('密码至少 12 位，需包含大写字母、小写字母、数字和符号。');
  busy.value = true;
  try {
    if (registering) {
      const result = await client.POST('/portal/auth/register', { body: { invitationToken: form.invitationToken.trim(), username: form.username.trim(), password: form.password } });
      if (!result.data) return await fail(responseError(result.response.status, true));
      mode.value = 'login'; form.invitationToken = '';
      notice.value = '注册成功。请使用邀请方提供的租户编码和刚设置的账号密码登录；注册不代表供应商准入通过。';
    } else {
      const result = await client.POST('/portal/auth/login', { body: { tenantCode: form.tenantCode.trim(), username: form.username.trim(), password: form.password } });
      if (!result.data) return await fail(responseError(result.response.status));
      accessToken = result.data.data.accessToken;
      identity.value = result.data.data.user;
      notice.value = '已登录供应商独立身份域。刷新页面后需重新登录。';
    }
  } catch {
    await fail(registering ? '网络异常，注册结果未确认。请先尝试登录或联系邀请方核查，勿反复兑换邀请。' : '网络异常，登录结果未确认，请检查连接后重试。');
  } finally { form.password = ''; form.confirmation = ''; busy.value = false; }
}
async function refreshIdentity() {
  if (busy.value) return;
  busy.value = true; error.value = ''; notice.value = '';
  try {
    const result = await client.GET('/portal/auth/me');
    if (result.data) { identity.value = result.data.data; notice.value = '当前身份已由服务器重新确认。'; }
    else if (result.response.status === 401) { accessToken = undefined; identity.value = undefined; await fail('会话已失效，请重新登录。'); }
    else { identity.value = undefined; accessToken = undefined; await fail(responseError(result.response.status)); }
  } catch { await fail('无法确认当前身份，请检查连接后重试。当前显示的是上次成功确认的信息。'); }
  finally { busy.value = false; }
}
async function logout() {
  if (busy.value) return;
  busy.value = true; error.value = ''; notice.value = '';
  try {
    const result = await client.POST('/portal/auth/logout');
    if (!result.response.ok && result.response.status !== 401) return await fail('退出未确认，请重试。当前会话尚未确认撤销。');
    accessToken = undefined; identity.value = undefined;
    notice.value = '已退出当前会话。其他设备的登录不受影响。';
  } catch { await fail('网络异常，退出未确认。请恢复连接后重试退出。'); }
  finally { busy.value = false; }
}
onMounted(() => { document.title = '供应商协作门户 · ISM'; });
</script>

<template>
  <main class="shell">
    <header><span class="brand">ISM / 供应商协作</span><span class="tag">独立门户</span></header>
    <section class="intro"><p class="eyebrow">INDUSTRIAL SUPPLIER MANAGEMENT</p><h1>供应商协作门户</h1><p>使用邀请凭证建立账号，以独立身份安全访问。</p></section>
    <section class="card" :aria-busy="busy">
      <div v-if="error" ref="errorPanel" role="alert" tabindex="-1" class="error">{{ error }}</div>
      <p v-if="notice" role="status" class="notice">{{ notice }}</p>
      <template v-if="identity">
        <h2>当前登录身份</h2><p>欢迎，{{ identity.displayName || identity.username }}</p>
        <dl><dt>用户名</dt><dd>{{ identity.username }}</dd><dt>账号 ID</dt><dd>{{ identity.accountId }}</dd><dt>租户 ID</dt><dd>{{ identity.tenantId }}</dd><dt>供应商 ID</dt><dd>{{ identity.supplierId }}</dd></dl>
        <p class="help">此页仅确认账号绑定，不代表准入审批通过。资料提交及业务协同尚未开放。</p>
        <div class="actions"><button :disabled="busy" @click="refreshIdentity">重新确认身份</button><button class="secondary" :disabled="busy" @click="logout">退出当前会话</button></div>
      </template>
      <template v-else>
        <h2>{{ mode === 'login' ? '账号登录' : '邀请注册' }}</h2>
        <p class="help">{{ mode === 'login' ? '请输入邀请方提供的租户编码和门户账号。' : '邀请决定租户与供应商归属，不能自行选择或改绑。' }}</p>
        <form @submit.prevent="submit">
          <fieldset :disabled="busy">
            <label v-if="mode === 'login'">租户编码<input v-model="form.tenantCode" required maxlength="64" autocomplete="organization"></label>
            <label v-else>邀请凭证<input v-model="form.invitationToken" aria-label="邀请凭证" aria-describedby="invitation-help" required minlength="43" maxlength="43" autocomplete="off" spellcheck="false"><span id="invitation-help" class="help">由邀请方安全提供，不会自动发送邮件。请勿分享给他人。</span></label>
            <label>用户名<input v-model="form.username" required minlength="3" maxlength="64" autocomplete="username" autocapitalize="none" spellcheck="false"></label>
            <label>密码<input v-model="form.password" aria-label="密码" :aria-describedby="mode === 'register' ? 'password-help' : undefined" required type="password" maxlength="72" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"><span v-if="mode === 'register'" id="password-help" class="help">至少 12 位，包含大小写字母、数字和符号；最多 72 个 UTF-8 字节。</span></label>
            <label v-if="mode === 'register'">确认密码<input v-model="form.confirmation" required type="password" maxlength="72" autocomplete="new-password"></label>
            <button class="primary" type="submit">{{ busy ? '正在处理…' : mode === 'login' ? '登录门户' : '创建门户账号' }}</button>
          </fieldset>
        </form>
        <button class="switch" :disabled="busy" @click="switchMode">{{ mode === 'login' ? '有邀请凭证？注册账号' : '已有账号？返回登录' }}</button>
        <p class="help">忘记密码或邀请异常，请联系邀请方管理员。当前未提供在线找回密码。</p>
      </template>
    </section>
    <footer>门户与企业管理后台账号相互独立 · 不保存密码或登录令牌到浏览器存储</footer>
  </main>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { createIsmClient } from '@ism/api-client';
const props = defineProps<{ token: string }>();
const client = createIsmClient({ getAccessToken: () => props.token });
const sessionId = ref(''), reason = ref(''), message = ref('');
const confirming = ref(false), busy = ref(false);
let generation = 0;
watch(() => props.token, () => { generation++; sessionId.value=''; reason.value=''; message.value=''; confirming.value=false; busy.value=false; });
async function terminate() {
  if (busy.value) return;
  const started = generation;
  busy.value=true; message.value='';
  try {
    const result = await client.POST('/console/support-sessions/{sessionId}/terminate', {
      params: { path: { sessionId: sessionId.value.trim() } }, body: { reason: reason.value.trim() },
    });
    if (started !== generation) return;
    message.value = result.data?.data.status === 'REVOKED' ? '会话已终止，进入和访问凭证均已失效。' : '未确认终止成功，请核对会话 ID、权限后重试。';
    if (result.data) confirming.value=false;
  } catch { if (started===generation) message.value='网络异常，结果未确认。可重试同一会话。'; }
  finally { if (started===generation) busy.value=false; }
}
</script>
<template>
  <section class="panel" aria-label="强制终止支持会话">
    <h2>强制终止支持会话</h2>
    <p>用于安全处置。终止后立即回收该会话的进入及访问凭证，保留操作原因和管理员审计。</p>
    <form @submit.prevent="confirming=true;message=''">
      <label>支持会话 ID <input v-model="sessionId" required pattern="[1-9][0-9]*" inputmode="numeric" :disabled="busy || confirming" /></label>
      <label>终止原因 <textarea v-model="reason" required maxlength="1000" :disabled="busy || confirming" /></label>
      <button v-if="!confirming" type="submit" :disabled="busy || !reason.trim()">核对终止操作</button>
    </form>
    <div v-if="confirming" role="group" aria-label="确认强制终止">
      <p>确认终止会话 {{ sessionId }}？已打开的支持工作台将失去访问权限。</p>
      <p>原因：{{ reason }}</p>
      <button type="button" :disabled="busy" @click="terminate">{{ busy ? '正在终止…' : '确认强制终止' }}</button>
      <button type="button" :disabled="busy" @click="confirming=false">取消</button>
    </div>
    <p role="status">{{ message }}</p>
  </section>
</template>
<style scoped>
form { display: grid; gap: 1rem; max-width: 42rem; }
label { display: grid; gap: .5rem; }
input, textarea { box-sizing: border-box; width: 100%; min-height: 44px; }
button { min-height: 44px; margin-right: .5rem; }
</style>

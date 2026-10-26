<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

const emit = defineEmits<{ saved: [] }>();
const session = useSessionStore();
const opened = ref(false), busy = ref(false), error = ref('');
const errorPanel = ref<HTMLElement>();
const current = ref<components['schemas']['Qualification']>();
const decision = ref<'APPROVE' | 'REJECT'>('APPROVE');
const form = reactive({ comment: '', publicComment: '' });
function open(value: components['schemas']['Qualification'], action: 'APPROVE' | 'REJECT') {
  if (opened.value || busy.value) return;
  current.value = value; decision.value = action;
  form.comment = ''; form.publicComment = ''; error.value = ''; opened.value = true;
}
async function fail(message: string) { error.value = message; await nextTick(); errorPanel.value?.focus(); }
async function save() {
  if (busy.value || !current.value) return;
  if (!form.comment.trim()) return fail('请填写内部核验意见。');
  if (current.value.portalSubmission && decision.value === 'REJECT' && !form.publicComment.trim()) return fail('请填写供应商可见的拒绝原因及需要补充的材料。');
  busy.value = true; error.value = '';
  try {
    const { error: responseError } = await session.client.POST('/qualifications/{id}/verify', {
      params: { path: { id: current.value.id } },
      body: { decision: decision.value, comment: form.comment.trim(), version: current.value.version,
        ...(current.value.portalSubmission ? { publicComment: form.publicComment.trim() || null } : {}) }
    });
    if (responseError) { await fail(responseError.error.message); return; }
    opened.value = false; emit('saved'); ElMessage.success('核验结果已保存');
  } catch { await fail('提交结果未知，请先关闭并刷新台账核查；重复提交会由版本校验阻止。'); }
  finally { busy.value = false; }
}
defineExpose({ open });
</script>
<template>
  <el-dialog v-model="opened" title="资质核验" width="min(600px, calc(100vw - 32px))" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" destroy-on-close>
    <p>{{ current?.certificateNo }} · {{ decision === 'APPROVE' ? '核验通过' : '核验拒绝' }}</p>
    <div v-if="current?.correctionOfId"><p>第 {{ current.correctionRound }} 次补正 · 上一轮申请 {{ current.correctionOfId }}</p><p class="correction-note">供应商补正说明：{{ current.correctionNote }}</p></div>
    <p v-if="current?.parentQualificationId">关联原证照 {{ current.parentQualificationId }}。核验通过将生效新证；门户换证会同时替代未变更的原证，拒绝不新增替代。</p>
    <div v-if="error" ref="errorPanel" role="alert" tabindex="-1" class="review-error">{{ error }}</div>
    <el-form label-position="top" :disabled="busy" @submit.prevent="save">
      <el-form-item label="内部核验意见" required><el-input v-model="form.comment" aria-label="内部核验意见" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
      <p class="review-help">仅供内部留痕，不会自动复制给供应商。</p>
      <template v-if="current?.portalSubmission">
        <el-form-item label="对外审核说明" :required="decision === 'REJECT'"><el-input v-model="form.publicComment" aria-label="对外审核说明" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
        <p class="review-help">此内容会原样展示给供应商，提交后不可改写。拒绝时请说明具体原因和补充材料；请勿填写内部风险信息、个人隐私或凭据。</p>
      </template>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="opened=false">取消</el-button><el-button type="primary" :loading="busy" @click="save">确定</el-button></template>
  </el-dialog>
</template>
<style scoped>
.review-help { color: var(--ism-text-secondary, #526477); line-height: 1.6; }
.review-error { color: var(--ism-danger, #b42332); margin-bottom: 16px; }
.correction-note { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>

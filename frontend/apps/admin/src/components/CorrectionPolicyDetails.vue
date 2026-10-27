<script setup lang="ts">
import CorrectionAttachmentList from './CorrectionAttachmentList.vue';
import type { components } from '@ism/api-client';
defineProps<{ value: components['schemas']['Qualification'] }>();
</script>
<template>
  <div class="correction-policy">
    <template v-if="value.status === 'WITHDRAWN'"><p>供应商已撤回，本申请不再核验。</p><p v-if="value.withdrawnAt">撤回时间（北京时间）：{{ new Date(value.withdrawnAt).toLocaleString('zh-CN', {timeZone:'Asia/Shanghai',hour12:false}) }}</p><p class="response">撤回原因：{{ value.withdrawalReason }}</p></template>
    <p v-if="value.correctionDueDate">补正期限（北京时间当日结束）：{{ value.correctionDueDate }}</p>
    <ol v-if="value.correctionRequirements?.length"><li v-for="(item,index) in value.correctionRequirements" :key="index">{{ item }}{{value.attachmentRequiredFor?.includes(index)?'（附件必交）':''}}</li></ol>
    <CorrectionAttachmentList v-if="value.correctionOfId" :key="value.id" :qualification-id="value.id"/>
    <p v-if="value.sourceCorrectionDueDate">本轮所据期限（北京时间当日结束）：{{ value.sourceCorrectionDueDate }}</p>
    <p v-if="value.lateCorrection" class="late">迟交补正：超过上一轮期限，仍需独立核验。</p>
    <div v-for="(item,index) in value.sourceCorrectionRequirements" :key="index"><p><strong>要求 {{ index+1 }}：{{ item }}</strong></p><p class="response">供应商回复：{{ value.correctionResponses?.[index] }}</p></div>
  </div>
</template>
<style scoped>
.correction-policy { overflow-wrap: anywhere; }
.response { white-space: pre-wrap; }
.late { color: var(--ism-warning, #946016); }
</style>

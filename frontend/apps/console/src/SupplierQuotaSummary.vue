<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount} from 'vue';
import {createIsmClient,type components} from '@ism/api-client';
const props=defineProps<{tenantId:string;token?:string}>();
const client=createIsmClient({getAccessToken:()=>props.token});
const result=ref<components['schemas']['QuotaUsage']>(),loading=ref(false),error=ref('');let mounted=true,request=0;
async function load(){const id=++request,token=props.token;loading.value=true;error.value='';result.value=undefined;try{const r=await client.GET('/console/tenants/{tenantId}/quota-usage',{params:{path:{tenantId:props.tenantId},query:{quotaCode:'SUPPLIER_COUNT'}}});if(!mounted||id!==request||token!==props.token)return;if(r.error){error.value=r.error.error.message;return;}result.value=r.data.data;}catch{if(mounted&&id===request)error.value='供应商用量读取失败，请重试';}finally{if(mounted&&id===request)loading.value=false;}}
onMounted(load);onBeforeUnmount(()=>{mounted=false;request++;});
</script>
<template><section class="quota-summary" aria-label="供应商档案额度"><h3>供应商档案额度</h3><p>统计租户内保留的全部供应商主体，包含草稿、停用和已退出档案。组织筛选不影响计数；退出保留历史档案，不自动释放容量。</p><p v-if="loading" role="status">正在读取实时用量…</p><p v-if="error" role="alert">{{error}}</p><template v-if="result"><dl><div><dt>已用档案</dt><dd>{{result.used}}</dd></div><div><dt>当前上限</dt><dd>{{result.limit}}</dd></div><div><dt>剩余容量</dt><dd>{{result.remaining}}</dd></div></dl><p v-if="result.hardLimited" role="status">当前不能继续新增供应商，请调整套餐；查询、编辑、停用和退出操作不受该容量限制。</p><p v-else-if="result.warning" role="status">供应商档案用量已达到预警线，请提前调整套餐。</p><p v-else>当前供应商档案容量可用。</p></template><button type="button" :disabled="loading" @click="load">刷新供应商用量</button></section></template>
<style scoped>.quota-summary{margin:16px 0;padding:16px;border:1px solid #c8d2da;border-radius:8px;}.quota-summary p{line-height:1.6;overflow-wrap:anywhere;}dl{display:flex;flex-wrap:wrap;gap:24px;}dl div{min-width:90px;}dt{color:#526477;}dd{font-size:24px;margin:8px 0;}button{width:auto;min-height:44px;padding:0 14px;}[role=alert]{color:#a52828;}</style>

<script setup lang="ts">
import { onUnmounted, ref } from 'vue';
import { useSessionStore } from '../stores/session';
const props=defineProps<{qualificationId:string}>();
const client=useSessionStore().client;
const items=ref<{id:string;requirementIndex:number;fileName:string}[]>(),error=ref(''),busy=ref(false);
let disposed=false;const abort=new AbortController();
async function load(){if(busy.value)return;busy.value=true;error.value='';try{const r=await client.GET('/qualifications/{id}/correction-attachments',{params:{path:{id:props.qualificationId}},signal:abort.signal});if(!r.data)throw new Error('附件读取失败，请重试。');if(!disposed)items.value=r.data.data;}catch(e){if(!disposed)error.value=e instanceof Error?e.message:'读取失败';}finally{if(!disposed)busy.value=false;}}
async function download(item:{id:string;fileName:string}){if(busy.value)return;busy.value=true;error.value='';try{const r=await client.GET('/qualifications/{id}/correction-attachments/{attachmentId}/content',{params:{path:{id:props.qualificationId,attachmentId:item.id}},parseAs:'blob',signal:abort.signal});if(!r.response.ok||!r.data)throw new Error('没有下载权限、附件已失效或服务暂不可用。');if(disposed)return;const url=URL.createObjectURL(r.data as Blob),a=document.createElement('a');a.href=url;a.download=item.fileName;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}catch(e){if(!disposed)error.value=e instanceof Error?e.message:'下载失败';}finally{if(!disposed)busy.value=false;}}
onUnmounted(()=>{disposed=true;abort.abort();});
</script>
<template><div class="attachment-list"><button type="button" :disabled="busy" @click="load">查看补正附件</button><p v-if="error" role="alert">{{error}}</p><p v-if="items&&!items.length">本轮没有关联补正附件。</p><div v-for="item in items" :key="item.id"><span>要求 {{item.requirementIndex+1}} · {{item.fileName}}</span><button type="button" :disabled="busy" :aria-label="`下载附件 ${item.fileName}`" @click="download(item)">下载附件</button></div></div></template>
<style scoped>.attachment-list{overflow-wrap:anywhere}button{min-height:40px;margin:6px 8px 6px 0}</style>

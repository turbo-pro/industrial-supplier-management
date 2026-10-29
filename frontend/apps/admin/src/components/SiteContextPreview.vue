<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
const props=defineProps<{options:components['schemas']['PersonalSiteOption'][];initialSiteId?:string}>();
const session=useSessionStore();
const selected=ref(props.initialSiteId),busy=ref(false),error=ref('');
const issued=ref<components['schemas']['SiteContextIssued']>(),current=ref<components['schemas']['SiteContextCurrent']>();
let timer:ReturnType<typeof setTimeout>|undefined;let mounted=true;
function clear(){if(timer)clearTimeout(timer);timer=undefined;issued.value=undefined;current.value=undefined;error.value='';}
async function issue(){
  if(busy.value)return;busy.value=true;clear();
  try{const result=await session.client.POST('/site-context',{body:{siteId:selected.value??null}});if(!mounted)return;if(result.error){error.value=result.error.error.message;return;}issued.value=result.data.data;
    timer=setTimeout(()=>{issued.value=undefined;current.value=undefined;error.value='场站预览已过期，请重新获取';},Math.max(0,Date.parse(result.data.data.expiresAt)-Date.now()));
  }catch{if(mounted)error.value='未能获取场站预览，请重试';}finally{if(mounted)busy.value=false;}
}
async function validate(){
  if(busy.value||!issued.value)return;busy.value=true;current.value=undefined;error.value='';
  try{const result=await session.client.GET('/site-context',{params:{header:{'X-Site-Context':issued.value.contextToken}}});if(!mounted)return;
    if(result.error){clear();error.value=result.error.error.message;return;}current.value=result.data.data;
  }catch{if(mounted){clear();error.value='校验失败，请重新获取场站预览';}}finally{if(mounted)busy.value=false;}
}
onBeforeUnmount(()=>{mounted=false;clear();});
</script>
<template>
  <el-card shadow="never" class="site-context-preview">
    <template #header>场站选择预览</template>
    <p>预览仅用于核对场站选择，不会切换当前组织或业务页面。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top" @submit.prevent="issue">
      <el-form-item label="预览场站"><el-select v-model="selected" clearable :disabled="busy" aria-label="预览场站" placeholder="按有效首选或唯一授权场站选择"><el-option v-for="site in options" :key="site.id" :value="site.id" :label="`${site.name}（${site.code}）`" /></el-select></el-form-item>
      <div class="actions"><el-button native-type="submit" type="primary" :loading="busy">获取场站预览</el-button><el-button :disabled="busy||!issued" @click="validate">校验场站预览</el-button><el-button :disabled="busy||!issued" @click="clear">清除预览</el-button></div>
    </el-form>
    <p v-if="issued">待校验场站：{{ issued.site.name }}（{{ issued.site.code }}）</p>
    <p v-if="current" role="status">已校验场站：{{ current.site.name }}（{{ current.site.code }}），仅用于本次预览。有效期至 {{ new Date(current.expiresAt).toLocaleString('zh-CN',{hour12:false}) }}</p>
  </el-card>
</template>
<style scoped>
.site-context-preview {margin-top:20px;}.site-context-preview p {line-height:1.6;overflow-wrap:anywhere;}.el-select {width:100%;}.actions {display:flex;flex-wrap:wrap;gap:8px;}.actions .el-button {margin-left:0;}
</style>

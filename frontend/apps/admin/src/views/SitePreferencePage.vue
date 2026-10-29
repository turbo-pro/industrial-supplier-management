<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
const session=useSessionStore();
type State=components['schemas']['SitePreference'];
type Option=components['schemas']['PersonalSiteOption'];
const state=ref<State>(), options=ref<Option[]>([]), selected=ref<string>(), selectedOption=ref<Option>();
const loading=ref(false), searching=ref(false), saving=ref(false), unknown=ref(false);
const error=ref(''), searchError=ref(''), notice=ref(''), q=ref(''), total=ref(0), page=ref(0);
const locked=computed(()=>loading.value||saving.value||unknown.value||!state.value?.canManage);
const choices=computed(()=>selectedOption.value&&!options.value.some(row=>row.id===selectedOption.value?.id)?[selectedOption.value,...options.value]:options.value);
let searchGeneration=0;
let attempt:{key:string;body:components['schemas']['SaveSitePreference']}|null=null;
async function load(){
  if(loading.value||saving.value||unknown.value)return;loading.value=true;error.value='';
  try{const result=await session.client.GET('/site-preferences');if(result.error){error.value=result.error.error.message;return;}state.value=result.data.data;selected.value=state.value.preferredSite?.id;selectedOption.value=state.value.preferredSite??undefined;}
  catch{error.value='首选场站读取失败，请重试';}finally{loading.value=false;}
}
async function search(next=0){
  const generation=++searchGeneration;searching.value=true;searchError.value='';
  try{const result=await session.client.GET('/site-preferences/options',{params:{query:{q:q.value,page:next,size:20}}});if(generation!==searchGeneration)return;if(result.error){searchError.value=result.error.error.message;return;}options.value=result.data.data.items;total.value=result.data.data.total;page.value=next;}
  catch{if(generation===searchGeneration)searchError.value='候选场站读取失败，请重试';}finally{if(generation===searchGeneration)searching.value=false;}
}
function changePage(value:number){void search(value-1);}
function change(value:string|undefined){selectedOption.value=choices.value.find(row=>row.id===value);}
async function save(){
  if(loading.value||saving.value||!state.value?.canManage)return;
  attempt??={key:crypto.randomUUID(),body:{siteId:selected.value??null,version:state.value.version}};
  saving.value=true;error.value='';notice.value='';
  try{const result=await session.client.PUT('/site-preferences',{params:{header:{'Idempotency-Key':attempt.key}},body:attempt.body});
    if(result.error){unknown.value=result.response.status>=500;error.value=unknown.value?'结果尚未确认，请使用原请求重试':result.error.error.message;if(!unknown.value)attempt=null;return;}
    state.value=result.data.data;selected.value=state.value.preferredSite?.id;selectedOption.value=state.value.preferredSite??undefined;attempt=null;unknown.value=false;notice.value='个人首选场站已保存';
  }catch{unknown.value=true;error.value='结果尚未确认，请使用原请求重试';}finally{saving.value=false;}
}
onMounted(()=>{void load();void search();});
</script>
<template>
  <section class="site-preference-page">
    <h1>首选场站</h1><p>保存个人首选，不改变账号组织、当前组织或业务访问权限。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <p v-if="notice" role="status">{{ notice }}</p>
    <el-card shadow="never">
      <template #header>我的首选经营场站</template>
      <el-alert v-if="state?.unavailable" title="原首选场站已不可用，请重新选择或清除。" type="warning" :closable="false" />
      <el-form label-position="top" @submit.prevent="search(0)">
        <el-form-item label="搜索场站名称或编码"><el-input v-model="q" maxlength="100" :disabled="loading||saving||unknown" aria-label="搜索场站名称或编码" /></el-form-item>
        <el-button native-type="submit" :loading="searching" :disabled="loading||saving||unknown">查询候选</el-button>
      </el-form>
      <el-alert v-if="searchError" :title="searchError" type="error" :closable="false" />
      <el-form label-position="top" class="choice-form" @submit.prevent="save">
        <el-form-item label="首选场站"><el-select v-model="selected" clearable :disabled="locked||searching" aria-label="首选场站" placeholder="未设置首选场站" @change="change"><el-option v-for="item in choices" :key="item.id" :value="item.id" :label="`${item.name}（${item.code}）`" /></el-select></el-form-item>
        <p v-if="!searching&&!searchError&&total===0">当前没有符合授权与组织归属的场站。</p>
        <el-pagination v-if="total>20" :current-page="page+1" :page-size="20" :total="total" layout="prev, pager, next" :disabled="loading||saving||unknown" @current-change="changePage" />
        <div class="actions"><el-button type="primary" native-type="submit" :loading="saving" :disabled="!state?.canManage||loading">{{ unknown?'使用原请求重试':'保存首选场站' }}</el-button><el-button :disabled="locked" @click="selected=undefined;selectedOption=undefined">清除选择</el-button><el-button :disabled="loading||saving||unknown" @click="load();search()">重新读取</el-button></div>
      </el-form>
      <p v-if="state&&!state.canManage">当前账号仅可查看个人首选场站。</p>
    </el-card>
  </section>
</template>
<style scoped>
.site-preference-page {max-width:880px;min-width:0;}
.site-preference-page p {line-height:1.6;overflow-wrap:anywhere;}
.choice-form {margin-top:20px;}.el-select {width:100%;}.actions {display:flex;flex-wrap:wrap;gap:8px;margin-top:16px;}.actions .el-button {margin-left:0;}
</style>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type EntityType=components['schemas']['SearchEntityType'];
type SearchItem=components['schemas']['SearchItem'];
const businessTypes:EntityType[]=['SUPPLIER','CONTRACT','PROJECT'];
const labels:Record<string,string>={SUPPLIER:'供应商',CONTRACT:'合同',PROJECT:'项目'};
const session=useSessionStore(),router=useRouter();
const query=reactive({keyword:'',types:[...businessTypes] as EntityType[],status:'',from:'',to:'',page:0,size:20});
const rows=ref<SearchItem[]>([]),hasMore=ref(false),searchedTypes=ref<EntityType[]>([]),loading=ref(false);
let sequence=0;
async function search(){
  if(!query.keyword.trim()&&!query.status.trim()&&!query.from&&!query.to){ElMessage.warning('请输入关键词或高级筛选条件');return;}
  if(query.types.length===0){ElMessage.warning('请选择至少一类业务对象');return;}
  if(query.from&&query.to&&query.from>query.to){ElMessage.warning('开始时间不能晚于结束时间');return;}
  const current=++sequence;loading.value=true;
  try{
    const {data,error}=await session.client.POST('/search',{body:{keyword:query.keyword.trim(),types:query.types,statuses:query.status.trim()?[query.status.trim()]:[],updatedFrom:query.from?`${query.from}T00:00:00`:undefined,updatedTo:query.to?`${query.to}T23:59:59`:undefined,page:query.page,size:query.size}});
    if(current!==sequence)return;
    if(error){rows.value=[];hasMore.value=false;ElMessage.error(error.error.message);return;}
    rows.value=data?.data?.items??[];hasMore.value=data?.data?.hasMore??false;searchedTypes.value=data?.data?.searchedTypes??[];
  }catch{if(current===sequence){rows.value=[];hasMore.value=false;ElMessage.error('搜索失败，请重试');}}
  finally{if(current===sequence)loading.value=false;}
}
function submit(){query.page=0;void search();}
function open(row:SearchItem){void router.push({path:row.route,query:{keyword:row.type==='SUPPLIER'?row.subtitle:row.title}});}
watch(()=>session.currentOrganization?.id,()=>{sequence++;rows.value=[];hasMore.value=false;searchedTypes.value=[];query.page=0;});
</script>
<template>
  <div class="page">
    <div class="page-heading"><div><h1>业务全局搜索</h1><p>按当前账号的权限和数据范围检索供应商、合同与项目</p></div></div>
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="submit">
        <el-form-item label="关键词"><el-input v-model="query.keyword" maxlength="100" clearable placeholder="名称、编码或供应商信用代码" style="width:300px" @keyup.enter="submit"/></el-form-item>
        <el-form-item label="对象"><el-select v-model="query.types" multiple collapse-tags style="width:260px"><el-option v-for="type in businessTypes" :key="type" :label="labels[type]" :value="type"/></el-select></el-form-item>
        <el-form-item label="状态"><el-input v-model="query.status" maxlength="32" clearable placeholder="精确状态代码" style="width:140px"/></el-form-item>
        <el-form-item label="更新时间"><el-date-picker v-model="query.from" type="date" value-format="YYYY-MM-DD" placeholder="开始" style="width:145px"/> — <el-date-picker v-model="query.to" type="date" value-format="YYYY-MM-DD" placeholder="结束" style="width:145px"/></el-form-item>
        <el-form-item><el-button type="primary" :loading="loading" @click="submit">搜索</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-alert v-if="searchedTypes.length && searchedTypes.length<query.types.length" title="部分对象因权限或数据范围不足未检索" type="info" :closable="false" style="margin-bottom:12px"/>
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无结果；请检查条件或当前账号权限">
        <el-table-column label="类型" width="110"><template #default="{row}">{{labels[row.type]??row.type}}</template></el-table-column>
        <el-table-column prop="title" label="名称" min-width="220"/>
        <el-table-column prop="subtitle" label="编码 / 供应商" min-width="220"/>
        <el-table-column prop="status" label="状态" width="130"/>
        <el-table-column prop="updatedAt" label="更新时间" width="200"/>
        <el-table-column label="操作" width="110"><template #default="{row}"><el-button link type="primary" @click="open(row)">打开台账</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><span>第 {{query.page+1}} 页{{hasMore?'，还有更多结果':''}}</span><div><el-button :disabled="loading||query.page===0" @click="query.page--;search()">上一页</el-button><el-button :disabled="loading||!hasMore" @click="query.page++;search()">下一页</el-button></div></div>
    </el-card>
  </div>
</template>

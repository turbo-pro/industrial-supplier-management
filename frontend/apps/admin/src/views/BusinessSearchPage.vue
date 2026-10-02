<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type EntityType=components['schemas']['SearchEntityType'];
type SearchItem=components['schemas']['SearchItem'];
type SavedSearch=components['schemas']['SavedSearch'];
const businessTypes:EntityType[]=['SUPPLIER','SUPPLIER_ADMISSION','SUPPLIER_QUALIFICATION','CONTRACT','PROJECT','PERSON','ASSET','SAFETY_ISSUE','QUALITY_NCR','PERFORMANCE_EVALUATION','SITE_ATTENDANCE'];
const labels:Record<string,string>={SUPPLIER:'供应商',SUPPLIER_ADMISSION:'准入申请',SUPPLIER_QUALIFICATION:'资质证照',CONTRACT:'合同',PROJECT:'项目',PERSON:'供应商人员',ASSET:'车辆设备',SAFETY_ISSUE:'安全隐患',QUALITY_NCR:'质量不符合项',PERFORMANCE_EVALUATION:'绩效评价',SITE_ATTENDANCE:'现场出入'};
const session=useSessionStore(),router=useRouter();
const query=reactive({keyword:'',types:[...businessTypes] as EntityType[],status:'',from:'',to:'',page:0,size:20});
const rows=ref<SearchItem[]>([]),hasMore=ref(false),searchedTypes=ref<EntityType[]>([]),loading=ref(false);
const saved=ref<SavedSearch[]>([]),selectedId=ref(''),schemeBusy=ref(false),schemeAvailable=ref(true),makeDefault=ref(false);
let sequence=0;
function validFilters(){if(!query.keyword.trim()&&!query.status.trim()&&!query.from&&!query.to){ElMessage.warning('请输入关键词或高级筛选条件');return false;}if(query.types.length===0){ElMessage.warning('请选择至少一类业务对象');return false;}if(query.from&&query.to&&query.from>query.to){ElMessage.warning('开始时间不能晚于结束时间');return false;}return true;}
function snapshot(){return{keyword:query.keyword.trim(),types:[...query.types],statuses:query.status.trim()?[query.status.trim()]:[],updatedFrom:query.from?`${query.from}T00:00:00`:undefined,updatedTo:query.to?`${query.to}T23:59:59`:undefined,page:0,size:query.size};}
async function loadSaved(){
  try{const {data,error}=await session.client.GET('/search/saved');
    if(error){schemeAvailable.value=false;saved.value=[];return;}
    schemeAvailable.value=true;saved.value=data?.data??[];
    if(selectedId.value&&!saved.value.some(s=>s.id===selectedId.value))selectedId.value='';
  }catch{schemeAvailable.value=false;saved.value=[];}
}
function applyScheme(scheme:SavedSearch){
  if(!scheme.query.types.length||scheme.query.types.some(type=>!businessTypes.includes(type))||scheme.query.statuses.length>1){ElMessage.warning('此方案包含当前页面尚未支持的搜索条件');return;}
  query.keyword=scheme.query.keyword;query.types=[...scheme.query.types];query.status=scheme.query.statuses[0]??'';
  query.from=scheme.query.updatedFrom?.slice(0,10)??'';query.to=scheme.query.updatedTo?.slice(0,10)??'';
  query.page=0;makeDefault.value=scheme.defaultSearch;selectedId.value=scheme.id;
  rows.value=[];hasMore.value=false;searchedTypes.value=[];
}
function chooseScheme(){const scheme=saved.value.find(s=>s.id===selectedId.value);if(scheme)applyScheme(scheme);}
async function createScheme(){
  if(!schemeAvailable.value||!validFilters())return;
  let name:string;
  try{name=(await ElMessageBox.prompt('输入搜索方案名称','保存当前搜索条件',{inputValidator:(value:string)=>!!value.trim()&&value.trim().length<=100||'名称须为 1–100 字'})).value.trim();}catch{return;}
  schemeBusy.value=true;
  try{const {data,error}=await session.client.POST('/search/saved',{body:{name,query:snapshot(),defaultSearch:makeDefault.value,version:0}});
    if(error){ElMessage.error(error.error.message);await loadSaved();return;}
    await loadSaved();selectedId.value=data?.data?.id??'';ElMessage.success('搜索方案已保存');
  }catch{ElMessage.error('保存搜索方案失败，请重试');}finally{schemeBusy.value=false;}
}
async function updateScheme(){
  const current=saved.value.find(s=>s.id===selectedId.value);if(!current||!validFilters())return;
  schemeBusy.value=true;
  try{const {error}=await session.client.PUT('/search/saved/{id}',{params:{path:{id:current.id}},body:{name:current.name,query:snapshot(),defaultSearch:makeDefault.value,version:current.version}});
    if(error){ElMessage.error(error.error.message);await loadSaved();return;}
    await loadSaved();ElMessage.success('搜索方案已更新');
  }catch{ElMessage.error('更新搜索方案失败，请重试');}finally{schemeBusy.value=false;}
}
async function renameScheme(){
  const current=saved.value.find(s=>s.id===selectedId.value);if(!current)return;
  let name:string;
  try{name=(await ElMessageBox.prompt('输入新名称','重命名搜索方案',{inputValue:current.name,inputValidator:(value:string)=>!!value.trim()&&value.trim().length<=100||'名称须为 1–100 字'})).value.trim();}catch{return;}
  if(name===current.name)return;
  schemeBusy.value=true;
  try{const {error}=await session.client.PUT('/search/saved/{id}',{params:{path:{id:current.id}},body:{name,query:current.query,defaultSearch:current.defaultSearch,version:current.version}});
    if(error){ElMessage.error(error.error.message);await loadSaved();return;}
    await loadSaved();ElMessage.success('搜索方案已重命名');
  }catch{ElMessage.error('重命名失败，请重试');}finally{schemeBusy.value=false;}
}
async function deleteScheme(){
  const current=saved.value.find(s=>s.id===selectedId.value);if(!current)return;
  try{await ElMessageBox.confirm(`删除个人搜索方案“${current.name}”？`,'删除确认',{type:'warning'});}catch{return;}
  schemeBusy.value=true;
  try{const {error}=await session.client.DELETE('/search/saved/{id}',{params:{path:{id:current.id},query:{version:current.version}}});
    if(error){ElMessage.error(error.error.message);await loadSaved();return;}
    selectedId.value='';makeDefault.value=false;await loadSaved();ElMessage.success('搜索方案已删除');
  }catch{ElMessage.error('删除搜索方案失败，请重试');}finally{schemeBusy.value=false;}
}
async function search(){
  if(!validFilters())return;
  const current=++sequence;loading.value=true;
  try{
    const {data,error}=await session.client.POST('/search',{body:{...snapshot(),page:query.page}});
    if(current!==sequence)return;
    if(error){rows.value=[];hasMore.value=false;ElMessage.error(error.error.message);return;}
    rows.value=data?.data?.items??[];hasMore.value=data?.data?.hasMore??false;searchedTypes.value=data?.data?.searchedTypes??[];
  }catch{if(current===sequence){rows.value=[];hasMore.value=false;ElMessage.error('搜索失败，请重试');}}
  finally{if(current===sequence)loading.value=false;}
}
function submit(){query.page=0;void search();}
function open(row:SearchItem){
  if(row.type==='SUPPLIER_ADMISSION'){void router.push({path:row.route,query:{admissionId:row.id}});return;}
  if(row.type==='SUPPLIER_QUALIFICATION'){void router.push({path:row.route,query:{keyword:row.title}});return;}
  if(row.type==='PERFORMANCE_EVALUATION'){void router.push({path:row.route,query:{evaluationId:row.id}});return;}
  if(row.type==='SITE_ATTENDANCE'){void router.push({path:row.route,query:{attendanceId:row.id}});return;}
  const keyword=row.type==='SUPPLIER'||row.type==='SAFETY_ISSUE'||row.type==='QUALITY_NCR'?(row.subtitle??'').split(' · ')[0]:row.title;
  void router.push({path:row.route,query:{keyword}});
}
watch(()=>session.currentOrganization?.id,()=>{sequence++;rows.value=[];hasMore.value=false;searchedTypes.value=[];query.page=0;});
watch(()=>session.actorId,()=>{selectedId.value='';saved.value=[];void loadSaved();});
onMounted(async()=>{await loadSaved();const defaultScheme=saved.value.find(s=>s.defaultSearch);if(defaultScheme)applyScheme(defaultScheme);});
onBeforeUnmount(()=>{sequence++;});
</script>
<template>
  <div class="page">
    <div class="page-heading"><div><h1>业务全局搜索</h1><p>按当前账号的权限和数据范围检索供应商、合同、项目、资源、安全、质量与绩效记录</p></div></div>
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="submit">
        <el-form-item label="关键词"><el-input v-model="query.keyword" maxlength="100" clearable placeholder="名称、编码或供应商信用代码" style="width:300px" @keyup.enter="submit"/></el-form-item>
        <el-form-item label="对象"><el-select v-model="query.types" multiple collapse-tags style="width:260px"><el-option v-for="type in businessTypes" :key="type" :label="labels[type]" :value="type"/></el-select></el-form-item>
        <el-form-item label="状态"><el-input v-model="query.status" maxlength="32" clearable placeholder="精确状态代码" style="width:140px"/></el-form-item>
        <el-form-item label="更新时间"><el-date-picker v-model="query.from" type="date" value-format="YYYY-MM-DD" placeholder="开始" style="width:145px"/> — <el-date-picker v-model="query.to" type="date" value-format="YYYY-MM-DD" placeholder="结束" style="width:145px"/></el-form-item>
        <el-form-item><el-button type="primary" :loading="loading" @click="submit">搜索</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never" class="filter-card">
      <el-form inline><el-form-item label="个人搜索方案"><el-select v-model="selectedId" clearable placeholder="选择已有方案" style="width:240px" :disabled="!schemeAvailable||schemeBusy" @change="chooseScheme"><el-option v-for="scheme in saved" :key="scheme.id" :label="`${scheme.name}${scheme.defaultSearch?' · 默认':''}`" :value="scheme.id"/></el-select></el-form-item>
        <el-form-item><el-checkbox v-model="makeDefault" :disabled="!schemeAvailable||schemeBusy">设为默认</el-checkbox></el-form-item>
        <el-form-item><el-button :disabled="!schemeAvailable||schemeBusy" @click="createScheme">另存为</el-button><el-button :disabled="!schemeAvailable||!selectedId||schemeBusy" @click="updateScheme">更新条件</el-button><el-button :disabled="!schemeAvailable||!selectedId||schemeBusy" @click="renameScheme">重命名</el-button><el-button type="danger" plain :disabled="!schemeAvailable||!selectedId||schemeBusy" @click="deleteScheme">删除方案</el-button></el-form-item>
      </el-form>
      <div v-if="!schemeAvailable" class="subtext">当前账号无个人搜索方案管理权限，仍可在授权范围内直接搜索。</div>
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

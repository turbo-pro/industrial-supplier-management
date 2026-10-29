<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
const props=defineProps<{canViewDetails:boolean}>(),session=useSessionStore();
const rows=ref<components['schemas']['ProjectSummary'][]>([]),detail=ref<components['schemas']['Project']>(),total=ref(0),busy=ref(false),error=ref('');
const query=reactive({keyword:'',page:0});let epoch=0,mounted=true;
const states:Record<string,string>={PLANNED:'计划中',ACTIVE:'进行中',SUSPENDED:'已暂停',COMPLETED:'已完成',CANCELLED:'已取消'};
function organization(id:string){return session.organizationOptions.find(o=>o.id===id)?.label??'组织名称未授权或未加载';}
async function load(){const generation=++epoch;rows.value=[];detail.value=undefined;total.value=0;error.value='';busy.value=true;
 try{const r=await session.client.GET('/projects',{params:{query:{keyword:query.keyword,page:query.page,size:20}}});if(!mounted||generation!==epoch)return;if(r.error||!r.data?.data){error.value=r.error?.error.message??'项目总览未返回完整数据';return;}rows.value=r.data.data.items;total.value=r.data.data.total;}
 catch{if(mounted&&generation===epoch)error.value='项目总览读取失败，请重新查询';}finally{if(mounted&&generation===epoch)busy.value=false;}}
async function open(id:string){if(!props.canViewDetails||busy.value)return;const generation=++epoch;detail.value=undefined;error.value='';busy.value=true;
 try{const r=await session.client.GET('/projects/{id}',{params:{path:{id}}});if(!mounted||generation!==epoch)return;if(r.error||!r.data?.data){error.value=r.error?.error.message??'项目详情未返回完整数据';return;}detail.value=r.data.data;}
 catch{if(mounted&&generation===epoch)error.value='项目详情读取失败，请重新打开';}finally{if(mounted&&generation===epoch)busy.value=false;}}
onMounted(load);onBeforeUnmount(()=>{mounted=false;epoch++;});
</script>
<template>
 <section class="organization-project-overview"><h1>组织项目总览</h1><p>仅展示项目数据权限内的记录，含保留的历史归属。此视角只读，项目维护请进入对应经营场站。</p><p v-if="!canViewDetails">当前仅有总览权限，项目详情需要单独授权。</p>
 <el-alert v-if="error" :title="error" type="error" :closable="false"/>
 <el-form label-position="top" @submit.prevent="query.page=0;load()"><el-form-item label="项目关键词"><el-input v-model="query.keyword" maxlength="100" aria-label="组织项目关键词" :disabled="busy"/></el-form-item><el-button native-type="submit" :loading="busy">查询组织项目</el-button></el-form>
 <div class="table-region"><el-table :data="rows" v-loading="busy" row-key="id"><el-table-column prop="projectCode" label="项目编码" min-width="160"/><el-table-column prop="name" label="项目名称" min-width="180"/><el-table-column prop="supplierName" label="供应商" min-width="160"/><el-table-column label="归属组织" min-width="180"><template #default="{row}">{{organization(row.organizationId)}}</template></el-table-column><el-table-column label="状态" width="100"><template #default="{row}">{{states[row.status]}}</template></el-table-column><el-table-column label="详情" width="100"><template #default="{row}"><el-button link type="primary" :disabled="!canViewDetails||busy" @click="open(row.id)">查看详情</el-button></template></el-table-column><template #empty>{{error?'读取失败，请重新查询':'没有符合条件的项目'}}</template></el-table></div>
 <el-pagination :total="total" :page-size="20" :current-page="query.page+1" :disabled="busy" layout="total, prev, next" @current-change="query.page=$event-1;load()"/>
 <el-card v-if="detail" class="overview-detail" shadow="never"><h2>{{detail.name}}</h2><dl><dt>项目编码</dt><dd>{{detail.projectCode}}</dd><dt>归属组织</dt><dd>{{organization(detail.organizationId)}}</dd><dt>供应商</dt><dd>{{detail.supplierName}}</dd><dt>状态</dt><dd>{{states[detail.status]}}</dd><dt>计划周期</dt><dd>{{detail.plannedStartDate}} — {{detail.plannedEndDate}}</dd></dl><el-button @click="detail=undefined">关闭详情</el-button></el-card>
 </section>
</template>
<style scoped>.organization-project-overview{min-width:0;max-width:1200px;}.organization-project-overview p{line-height:1.6;overflow-wrap:anywhere;}.table-region{max-width:100%;overflow-x:auto;margin-top:20px;}.overview-detail{margin-top:20px;}dd{margin:4px 0 16px;overflow-wrap:anywhere;}</style>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';import { useRoute, useRouter } from 'vue-router';import { ElMessage, ElMessageBox } from 'element-plus';import { Plus, Refresh, Search } from '@element-plus/icons-vue';import type { components } from '@ism/api-client';import { useSessionStore } from '../stores/session';
type Contract=components['schemas']['ContractSummary'];type Project=components['schemas']['ProjectSummary'];type Supplier=components['schemas']['SupplierSummary'];
import PersonalTableViews from '../components/PersonalTableViews.vue';
const contractDefinitions=[{key:'contractNo',label:'合同编号',required:true,width:160},{key:'name',label:'合同/供应商',required:true,width:250},{key:'amount',label:'金额',required:false,width:150},{key:'period',label:'期限',required:false,width:240},{key:'status',label:'状态',required:false,width:110}];
const projectDefinitions=[{key:'projectCode',label:'项目编码',required:true,width:150},{key:'name',label:'项目/供应商',required:true,width:250},{key:'contractNo',label:'关联合同',required:false,width:150},{key:'period',label:'计划周期',required:false,width:240},{key:'status',label:'状态',required:false,width:110}];
const contractColumns=ref(contractDefinitions.map(c=>({key:c.key,visible:true,width:c.width})));
const projectColumns=ref(projectDefinitions.map(c=>({key:c.key,visible:true,width:c.width})));
const contractOptions=ref<Contract[]>([]);
let requestVersion=0;
const session=useSessionStore(),route=useRoute(),router=useRouter();const tab=computed(()=>route.meta.tab as string);const loading=ref(false),dialog=ref(false),total=ref(0);const contracts=ref<Contract[]>([]),projects=ref<Project[]>([]),suppliers=ref<Supplier[]>([]);const query=reactive({keyword:'',status:'',page:0,size:20});
const contractForm=reactive({contractNo:'',name:'',supplierId:'',type:'SERVICE' as components['schemas']['ContractType'],amount:0,currency:'CNY',signedDate:'',startDate:'',endDate:'',fileId:''});
const projectForm=reactive({projectCode:'',name:'',supplierId:'',contractId:'',type:'MAINTENANCE' as components['schemas']['ProjectType'],siteAddress:'',plannedStartDate:'',plannedEndDate:'',budgetAmount:undefined as number|undefined,currency:'CNY'});
const contractStatuses={DRAFT:'草稿',ACTIVE:'履行中',COMPLETED:'已完成',TERMINATED:'已终止',EXPIRED:'已到期'},projectStatuses={PLANNED:'计划中',ACTIVE:'进行中',SUSPENDED:'已暂停',COMPLETED:'已完成',CANCELLED:'已取消'};
async function load(){
  const version=++requestVersion;const requestedTab=tab.value;loading.value=true;
  try{
    if(requestedTab==='contracts'){
      const {data,error}=await session.client.GET('/contracts',{params:{query:{keyword:query.keyword||undefined,status:(query.status||undefined) as components['schemas']['ContractStatus']|undefined,page:query.page,size:query.size}}});
      if(version!==requestVersion||requestedTab!==tab.value)return;
      if(error){contracts.value=[];total.value=0;ElMessage.error(error.error.message);return;}
      contracts.value=data?.data?.items??[];total.value=data?.data?.total??0;
    }else{
      const {data,error}=await session.client.GET('/projects',{params:{query:{keyword:query.keyword||undefined,status:(query.status||undefined) as components['schemas']['ProjectStatus']|undefined,page:query.page,size:query.size}}});
      if(version!==requestVersion||requestedTab!==tab.value)return;
      if(error){projects.value=[];total.value=0;ElMessage.error(error.error.message);return;}
      projects.value=data?.data?.items??[];total.value=data?.data?.total??0;
    }
  }catch{if(version===requestVersion){contracts.value=[];projects.value=[];total.value=0;ElMessage.error('台账加载失败，请重试');}}
  finally{if(version===requestVersion)loading.value=false;}
}
async function openCreate(){
  const requestedTab=tab.value;const epoch=requestVersion;
  try{
    const {data,error}=await session.client.GET('/suppliers',{params:{query:{status:'ACTIVE',page:0,size:100}}});
    if(requestedTab!==tab.value||epoch!==requestVersion)return;
    if(error){ElMessage.error(error.error.message);return;}suppliers.value=data?.data?.items??[];
    if(tab.value==='contracts')Object.assign(contractForm,{contractNo:'',name:'',supplierId:'',type:'SERVICE',amount:0,currency:'CNY',signedDate:'',startDate:'',endDate:'',fileId:''});
    else{
      const result=await session.client.GET('/contracts',{params:{query:{status:'ACTIVE',page:0,size:100}}});
      if(requestedTab!==tab.value||epoch!==requestVersion)return;
      if(result.error){ElMessage.error(result.error.error.message);return;}
      contractOptions.value=result.data?.data?.items??[];
      Object.assign(projectForm,{projectCode:'',name:'',supplierId:'',contractId:'',type:'MAINTENANCE',siteAddress:'',plannedStartDate:'',plannedEndDate:'',budgetAmount:undefined,currency:'CNY'});
    }
    dialog.value=true;
  }catch{if(requestedTab===tab.value&&epoch===requestVersion)ElMessage.error('新建所需资料加载失败，请重试');}
}
async function save(){if(tab.value==='contracts'){const f=contractForm;if(!f.contractNo||!f.name||!f.supplierId||!f.startDate||!f.endDate||!f.fileId){ElMessage.warning('请完整填写合同必填项');return;}const {error}=await session.client.POST('/contracts',{body:{...f,signedDate:f.signedDate||undefined,ownerId:session.actorId!,version:0}});if(error){ElMessage.error(error.error.message);return;}}else{const f=projectForm;if(!f.projectCode||!f.name||!f.supplierId||!f.plannedStartDate||!f.plannedEndDate){ElMessage.warning('请完整填写项目必填项');return;}const {error}=await session.client.POST('/projects',{body:{...f,contractId:f.contractId||undefined,managerId:session.actorId!,budgetAmount:f.budgetAmount,version:0}});if(error){ElMessage.error(error.error.message);return;}}dialog.value=false;ElMessage.success('草稿已创建');await load();}
async function changeContract(row:Contract,status:components['schemas']['ContractStatus']){let reason:string|undefined;if(status==='TERMINATED')reason=(await ElMessageBox.prompt('请输入终止原因','终止合同',{inputPattern:/.+/})).value;else await ElMessageBox.confirm(`确认将合同变更为“${contractStatuses[status]}”？`,'状态确认');const {error}=await session.client.POST('/contracts/{id}/status',{params:{path:{id:row.id}},body:{status,reason,version:row.version}});if(error)ElMessage.error(error.error.message);else await load();}
async function changeProject(row:Project,status:components['schemas']['ProjectStatus']){let reason:string|undefined;if(status==='SUSPENDED'||status==='CANCELLED')reason=(await ElMessageBox.prompt('请输入状态变更原因','项目状态',{inputPattern:/.+/})).value;else await ElMessageBox.confirm(`确认将项目变更为“${projectStatuses[status]}”？`,'状态确认');const {error}=await session.client.POST('/projects/{id}/status',{params:{path:{id:row.id}},body:{status,reason,version:row.version}});if(error)ElMessage.error(error.error.message);else await load();}
function switchTab(name:string){void router.push(name==='contracts'?'/projects/contracts':'/projects/ledger');}watch(()=>route.path,()=>{dialog.value=false;contracts.value=[];projects.value=[];total.value=0;query.status='';query.page=0;query.keyword=typeof route.query.keyword==='string'?route.query.keyword.slice(0,100):'';void load();});onMounted(()=>{query.keyword=typeof route.query.keyword==='string'?route.query.keyword.slice(0,100):'';void load();});
</script>
<template><div class="page"><div class="page-heading"><div><h1>{{tab==='contracts'?'合同台账':'项目台账'}}</h1><p>{{tab==='contracts'?'管理供应商合同金额、期限与履行状态':'以项目为主线承接人员、设备、安全和质量履约数据'}}</p></div><el-button type="primary" :icon="Plus" @click="openCreate">{{tab==='contracts'?'新建合同':'新建项目'}}</el-button></div><el-tabs :model-value="tab" @tab-change="switchTab"><el-tab-pane label="合同台账" name="contracts"/><el-tab-pane label="项目台账" name="projects"/></el-tabs>
<el-card shadow="never" class="filter-card"><el-form inline><el-form-item label="关键词"><el-input v-model="query.keyword" clearable :prefix-icon="Search" placeholder="编码、名称、供应商"/></el-form-item><el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部状态" style="width:150px"><el-option v-for="(name,key) in (tab==='contracts'?contractStatuses:projectStatuses)" :key="key" :label="name" :value="key"/></el-select></el-form-item><el-button type="primary" @click="query.page=0;load()">查询</el-button><el-button :icon="Refresh" @click="query.keyword='';query.status='';query.page=0;load()">重置</el-button></el-form></el-card>
<el-card v-if="tab==='contracts'" key="contracts" shadow="never" class="table-card">
  <PersonalTableViews key="contract.ledger" table-key="contract.ledger" :defaults="contractDefinitions" @change="contractColumns=$event"/>
  <el-table :key="contractColumns.filter(c=>c.visible).map(c=>c.key+':'+c.width).join('|')" v-loading="loading" :data="contracts">
    <el-table-column v-for="column in contractColumns.filter(c=>c.visible)" :key="column.key" :label="contractDefinitions.find(c=>c.key===column.key)?.label" :width="column.width">
      <template #default="{row}">
        <template v-if="column.key==='name'"><strong>{{row.name}}</strong><div class="subtext">{{row.supplierName}}</div></template>
        <template v-else-if="column.key==='amount'">{{row.currency}} {{row.amount.toLocaleString()}}</template>
        <template v-else-if="column.key==='period'">{{row.startDate}} 至 {{row.endDate}}</template>
        <el-tag v-else-if="column.key==='status'">{{contractStatuses[row.status as keyof typeof contractStatuses]}}</el-tag>
        <template v-else>{{row[column.key]}}</template>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="190" fixed="right"><template #default="{row}"><el-button v-if="row.status==='DRAFT'" link type="primary" @click="changeContract(row,'ACTIVE')">生效</el-button><template v-if="row.status==='ACTIVE'"><el-button link type="success" @click="changeContract(row,'COMPLETED')">完成</el-button><el-button link type="danger" @click="changeContract(row,'TERMINATED')">终止</el-button></template></template></el-table-column>
  </el-table>
</el-card>
<el-card v-else key="projects" shadow="never" class="table-card">
  <PersonalTableViews key="project.ledger" table-key="project.ledger" :defaults="projectDefinitions" @change="projectColumns=$event"/>
  <el-table :key="projectColumns.filter(c=>c.visible).map(c=>c.key+':'+c.width).join('|')" v-loading="loading" :data="projects">
    <el-table-column v-for="column in projectColumns.filter(c=>c.visible)" :key="column.key" :label="projectDefinitions.find(c=>c.key===column.key)?.label" :width="column.width">
      <template #default="{row}">
        <template v-if="column.key==='name'"><strong>{{row.name}}</strong><div class="subtext">{{row.supplierName}}</div></template>
        <template v-else-if="column.key==='period'">{{row.plannedStartDate}} 至 {{row.plannedEndDate}}</template>
        <el-tag v-else-if="column.key==='status'">{{projectStatuses[row.status as keyof typeof projectStatuses]}}</el-tag>
        <template v-else>{{row[column.key]??'—'}}</template>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="220" fixed="right"><template #default="{row}"><el-button v-if="row.status==='PLANNED'||row.status==='SUSPENDED'" link type="primary" @click="changeProject(row,'ACTIVE')">{{row.status==='SUSPENDED'?'复工':'开工'}}</el-button><template v-if="row.status==='ACTIVE'"><el-button link type="warning" @click="changeProject(row,'SUSPENDED')">暂停</el-button><el-button link type="success" @click="changeProject(row,'COMPLETED')">完成</el-button></template></template></el-table-column>
  </el-table>
</el-card>
<el-pagination style="margin-top:16px" :current-page="query.page+1" :total="total" :page-size="query.size" layout="prev, pager, next" @current-change="(page:number)=>{query.page=page-1;load()}"/>
<el-dialog v-model="dialog" :title="tab==='contracts'?'新建合同':'新建项目'" width="720px"><el-form v-if="tab==='contracts'" label-position="top"><el-row :gutter="18"><el-col :span="12"><el-form-item label="合同编号 *"><el-input v-model="contractForm.contractNo"/></el-form-item></el-col><el-col :span="12"><el-form-item label="合同名称 *"><el-input v-model="contractForm.name"/></el-form-item></el-col><el-col :span="12"><el-form-item label="供应商 *"><el-select v-model="contractForm.supplierId" filterable style="width:100%"><el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="合同类型"><el-select v-model="contractForm.type"><el-option label="服务" value="SERVICE"/><el-option label="采购" value="PURCHASE"/><el-option label="工程" value="ENGINEERING"/><el-option label="框架" value="FRAMEWORK"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="合同金额"><el-input-number v-model="contractForm.amount" :min="0" :precision="2" style="width:100%"/></el-form-item></el-col><el-col :span="12"><el-form-item label="合同文件 ID *"><el-input v-model="contractForm.fileId"/></el-form-item></el-col><el-col :span="8"><el-form-item label="签订日期"><el-date-picker v-model="contractForm.signedDate" value-format="YYYY-MM-DD"/></el-form-item></el-col><el-col :span="8"><el-form-item label="开始日期 *"><el-date-picker v-model="contractForm.startDate" value-format="YYYY-MM-DD"/></el-form-item></el-col><el-col :span="8"><el-form-item label="结束日期 *"><el-date-picker v-model="contractForm.endDate" value-format="YYYY-MM-DD"/></el-form-item></el-col></el-row></el-form><el-form v-else label-position="top"><el-row :gutter="18"><el-col :span="12"><el-form-item label="项目编码 *"><el-input v-model="projectForm.projectCode"/></el-form-item></el-col><el-col :span="12"><el-form-item label="项目名称 *"><el-input v-model="projectForm.name"/></el-form-item></el-col><el-col :span="12"><el-form-item label="供应商 *"><el-select v-model="projectForm.supplierId" filterable style="width:100%"><el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="关联合同"><el-select v-model="projectForm.contractId" clearable style="width:100%"><el-option v-for="c in contractOptions.filter(x=>!projectForm.supplierId||x.supplierId===projectForm.supplierId)" :key="c.id" :label="c.name" :value="c.id"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="现场地址"><el-input v-model="projectForm.siteAddress"/></el-form-item></el-col><el-col :span="12"><el-form-item label="预算金额"><el-input-number v-model="projectForm.budgetAmount" :min="0" :precision="2"/></el-form-item></el-col><el-col :span="12"><el-form-item label="计划开始 *"><el-date-picker v-model="projectForm.plannedStartDate" value-format="YYYY-MM-DD"/></el-form-item></el-col><el-col :span="12"><el-form-item label="计划结束 *"><el-date-picker v-model="projectForm.plannedEndDate" value-format="YYYY-MM-DD"/></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存草稿</el-button></template></el-dialog><div class="pagination"><span>共 {{total}} 条</span></div></div></template>

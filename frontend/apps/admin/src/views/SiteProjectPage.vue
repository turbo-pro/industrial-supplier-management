<script setup lang="ts">
import { onMounted, onBeforeUnmount, nextTick, reactive, ref, watch } from 'vue';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
import { ElMessageBox } from 'element-plus';
type Project=components['schemas']['ProjectSummary'];
type Detail=components['schemas']['Project'];
type Issued=components['schemas']['SiteContextIssued'];
const session=useSessionStore();
const errorFocus=ref<HTMLElement>();
const options=ref<components['schemas']['PersonalSiteOption'][]>([]),selected=ref<string>(),context=ref<Issued>();
const listError=ref(false);
const busy=ref(false),loading=ref(false),saving=ref(false),error=ref(''),notice=ref(''),optionError=ref(''),siteQuery=ref(''),sitePage=ref(0),siteTotal=ref(0);
watch(error,async value=>{if(value){await nextTick();errorFocus.value?.focus();}});
const rows=ref<Project[]>([]),total=ref(0),detail=ref<Detail>(),dialog=ref(false),editing=ref<string>(),unknown=ref(false);
const access=ref({canManage:false,canChangeStatus:false});
const suppliers=ref<components['schemas']['SupplierSummary'][]>([]),supplierBusy=ref(false);
const query=reactive({keyword:'',status:undefined as components['schemas']['ProjectStatus']|undefined,page:0});
const form=reactive<components['schemas']['SaveProject']>({projectCode:'',name:'',supplierId:'',type:'MAINTENANCE',plannedStartDate:'',plannedEndDate:'',managerId:'',version:0});
const states:Record<string,string>={PLANNED:'计划中',ACTIVE:'进行中',SUSPENDED:'已暂停',COMPLETED:'已完成',CANCELLED:'已取消'};
let timer:ReturnType<typeof setTimeout>|undefined,epoch=0,siteSearch=0,supplierSearch=0,mounted=true;
function clear(){epoch++;supplierSearch++;suppliers.value=[];supplierBusy.value=false;listError.value=false;if(timer)clearTimeout(timer);context.value=undefined;rows.value=[];total.value=0;detail.value=undefined;dialog.value=false;unknown.value=false;access.value={canManage:false,canChangeStatus:false};loading.value=false;}
function fail(result:{response:Response;error?:{error:{message:string}}}){const message=result.error?.error.message??'请求失败，请重试';if(result.response.status===401||result.response.status===403)clear();error.value=message;}
async function sites(page=0){const generation=++siteSearch;optionError.value='';try{const r=await session.client.GET('/site-preferences/options',{params:{query:{q:siteQuery.value,page,size:20}}});if(!mounted||generation!==siteSearch)return;if(r.error||!r.data?.data){optionError.value=r.error?.error.message??'接口未返回完整资料，请重试';return;}options.value=r.data.data.items;sitePage.value=page;siteTotal.value=r.data.data.total;}catch{if(mounted&&generation===siteSearch)optionError.value='候选场站读取失败，请重新查询';}}
async function enter(){if(busy.value||saving.value)return;busy.value=true;clear();const generation=epoch;error.value='';notice.value='';try{const r=await session.client.POST('/site-context',{body:{siteId:selected.value??null}});if(!mounted||generation!==epoch)return;if(r.error||!r.data?.data){fail(r);return;}context.value=r.data.data;query.page=0;query.keyword='';query.status=undefined;
 timer=setTimeout(()=>{clear();error.value='场站会话已过期，请重新进入；未确认的保存结果请在重新进入后核对项目编码。';},Math.max(0,Date.parse(r.data.data.expiresAt)-Date.now()));await load();}catch{if(mounted&&generation===epoch){clear();error.value='进入失败，请重新选择场站';}}finally{if(mounted)busy.value=false;}}
async function load(){if(!context.value||saving.value)return;const generation=++epoch,header={'X-Site-Context':context.value.contextToken};loading.value=true;listError.value=false;error.value='';rows.value=[];total.value=0;detail.value=undefined;
 try{const rights=await session.client.GET('/site-projects/access',{params:{header}});if(!mounted||generation!==epoch)return;if(rights.error||!rights.data?.data){listError.value=true;fail(rights);return;}access.value=rights.data.data;
 const r=await session.client.GET('/site-projects',{params:{header,query:{keyword:query.keyword,status:query.status,page:query.page,size:20}}});if(!mounted||generation!==epoch)return;if(r.error||!r.data?.data){listError.value=true;fail(r);return;}rows.value=r.data.data.items;total.value=r.data.data.total;unknown.value=false;
 }catch{if(mounted&&generation===epoch){listError.value=true;error.value='项目列表读取失败，请重新查询';}}finally{if(mounted&&generation===epoch)loading.value=false;}}
async function findSuppliers(keyword=''){const generation=++supplierSearch;supplierBusy.value=true;try{const r=await session.client.GET('/suppliers',{params:{query:{keyword,status:'ACTIVE',page:0,size:20}}});if(!mounted||generation!==supplierSearch)return;if(r.error||!r.data?.data){error.value=r.error?.error.message??'接口未返回完整资料，请重试';return;}suppliers.value=r.data.data.items;}catch{if(mounted&&generation===supplierSearch)error.value='供应商读取失败，请重新搜索';}finally{if(mounted&&generation===supplierSearch)supplierBusy.value=false;}}
async function open(row:Project){if(!context.value||loading.value||saving.value)return;const generation=++epoch;loading.value=true;detail.value=undefined;error.value='';try{const r=await session.client.GET('/site-projects/{id}',{params:{path:{id:row.id},header:{'X-Site-Context':context.value.contextToken}}});if(!mounted||generation!==epoch)return;if(r.error||!r.data?.data){fail(r);return;}detail.value=r.data.data;}catch{if(mounted&&generation===epoch)error.value='详情读取失败，请重新打开';}finally{if(mounted&&generation===epoch)loading.value=false;}}
function create(){if(!context.value||unknown.value||!access.value.canManage)return;editing.value=undefined;Object.assign(form,{projectCode:'',name:'',supplierId:'',contractId:undefined,type:'MAINTENANCE',siteAddress:'',plannedStartDate:'',plannedEndDate:'',managerId:session.actorId??'',budgetAmount:undefined,currency:'CNY',version:0});dialog.value=true;error.value='';void findSuppliers();}
function edit(){if(!detail.value||!access.value.canManage||unknown.value)return;const r=detail.value;editing.value=r.id;Object.assign(form,{projectCode:r.projectCode,name:r.name,supplierId:r.supplierId,contractId:r.contractId??undefined,type:r.type,siteAddress:r.siteAddress??'',plannedStartDate:r.plannedStartDate,plannedEndDate:r.plannedEndDate,managerId:r.managerId,budgetAmount:r.budgetAmount??undefined,currency:r.currency??'CNY',version:r.version});dialog.value=true;error.value='';}
async function save(){if(!context.value||saving.value||unknown.value)return;
 if(!form.projectCode.trim()||!form.name.trim()||!form.supplierId||!form.managerId||!form.plannedStartDate||!form.plannedEndDate||form.plannedStartDate>form.plannedEndDate){error.value='请完整填写项目编码、名称、供应商、负责人和有效计划日期';return;}
 const generation=epoch,header={'X-Site-Context':context.value.contextToken},body={...form,projectCode:form.projectCode.trim(),name:form.name.trim(),contractId:form.contractId||undefined};saving.value=true;error.value='';notice.value='';
 try{const r=editing.value?await session.client.PUT('/site-projects/{id}',{params:{path:{id:editing.value},header},body}):await session.client.POST('/site-projects',{params:{header},body});if(!mounted||generation!==epoch)return;
 if(r.error||!r.data?.data){if(r.response.status>=500){unknown.value=true;error.value='结果尚未确认，请关闭表单并重新查询，按项目编码核对后再操作。';}else fail(r);return;}dialog.value=false;detail.value=r.data.data;notice.value='项目已保存';
 }catch{if(mounted&&generation===epoch)unknown.value=true;}finally{if(mounted)saving.value=false;}
 if(unknown.value)error.value='保存结果尚未确认，请关闭表单并刷新列表，按项目编码核对后再操作。';else if(!dialog.value&&context.value)await load();}
async function status(next:components['schemas']['ProjectStatus']){if(!context.value||!detail.value||saving.value||unknown.value)return;const target=detail.value,generation=epoch,token=context.value.contextToken;let reason:string|undefined;
 try{reason=['SUSPENDED','CANCELLED'].includes(next)?(await ElMessageBox.prompt('请填写原因','项目状态变更',{inputPattern:/\S+/,inputErrorMessage:'必须填写原因'})).value:undefined;if(!reason)await ElMessageBox.confirm(`确认将项目改为“${states[next]}”？`,'项目状态确认');}catch{return;}
 if(!context.value||generation!==epoch||detail.value?.id!==target.id)return;saving.value=true;error.value='';notice.value='';try{const r=await session.client.POST('/site-projects/{id}/status',{params:{path:{id:target.id},header:{'X-Site-Context':token}},body:{status:next,reason,version:target.version}});if(!mounted||generation!==epoch)return;if(r.error||!r.data?.data){if(r.response.status>=500){unknown.value=true;error.value='结果尚未确认，请关闭表单并重新查询，按项目编码核对后再操作。';}else fail(r);return;}const changed=r.data.data;detail.value=changed;rows.value=rows.value.map(row=>row.id===target.id?{...row,status:changed.status,version:changed.version,updatedAt:changed.updatedAt}:row);notice.value='项目状态已更新';}catch{if(mounted&&generation===epoch)unknown.value=true;}finally{if(mounted)saving.value=false;}if(unknown.value)error.value='状态结果尚未确认，请重新查询核对。';}
watch(()=>session.accessToken,()=>{clear();siteSearch++;supplierSearch++;options.value=[];suppliers.value=[];selected.value=undefined;error.value='';notice.value='';if(session.accessToken)void sites();});
onMounted(()=>{void sites();});onBeforeUnmount(()=>{mounted=false;clear();siteSearch++;supplierSearch++;});
</script>
<template>
<section class="site-project-page">
 <h1>场站项目工作区</h1><p>本页项目按当前授权场站管理。供应商是租户共享主体；原组织台账及其他业务入口尚未随本页切换。</p>
 <p v-if="error&&!dialog" ref="errorFocus" tabindex="-1" role="alert" class="form-error">{{error}}</p><p v-if="notice" role="status">{{notice}}</p>
 <el-card shadow="never"><el-form label-position="top" @submit.prevent="sites(0)"><el-form-item label="搜索授权场站"><el-input v-model="siteQuery" aria-label="搜索授权场站" maxlength="100" :disabled="busy||saving"/></el-form-item><el-button native-type="submit" :disabled="busy||saving">查询场站</el-button></el-form>
 <el-alert v-if="optionError" :title="optionError" type="error" :closable="false"/>
 <el-form label-position="top" class="site-choice" @submit.prevent="enter"><el-form-item label="经营场站"><el-select v-model="selected" clearable :disabled="busy||saving" aria-label="经营场站" placeholder="使用有效首选或唯一授权场站"><el-option v-for="s in options" :key="s.id" :value="s.id" :label="`${s.name}（${s.code}）`"/></el-select></el-form-item><el-button native-type="submit" type="primary" :loading="busy" :disabled="saving">进入场站</el-button></el-form>
 <p v-if="!options.length&&!optionError">没有可选场站时，请联系管理员核对组织归属和场站权限。</p>
 <el-pagination v-if="siteTotal>20" :current-page="sitePage+1" :total="siteTotal" :page-size="20" layout="prev, next" :disabled="busy||saving" @current-change="sites($event-1)"/>
 <p v-if="context" role="status">当前场站：{{context.site.name}}（{{context.site.code}}）</p></el-card>
 <template v-if="context">
 <el-form label-position="top" class="filters" @submit.prevent="query.page=0;load()"><el-form-item label="项目关键词"><el-input v-model="query.keyword" aria-label="项目关键词" maxlength="100" :disabled="loading||saving"/></el-form-item><el-form-item label="项目状态"><el-select v-model="query.status" clearable aria-label="项目状态" :disabled="loading||saving"><el-option v-for="(name,key) in states" :key="key" :value="key" :label="name"/></el-select></el-form-item><div class="actions"><el-button native-type="submit" :loading="loading" :disabled="saving">查询项目</el-button><el-button :disabled="loading||saving" @click="query.keyword='';query.status=undefined;query.page=0;load()">重置查询</el-button><el-button type="primary" :disabled="loading||saving||unknown||!access.canManage" @click="create">新建场站项目</el-button></div></el-form>
 <p v-if="!access.canManage&&!loading">当前仅可查看项目，管理操作需额外授权。</p>
 <div class="table-region"><el-table :data="rows" v-loading="loading" row-key="id"><el-table-column prop="projectCode" label="项目编码" min-width="160"/><el-table-column prop="name" label="项目名称" min-width="180"/><el-table-column prop="supplierName" label="供应商" min-width="160"/><el-table-column label="状态" width="100"><template #default="{row}">{{states[row.status]}}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" :disabled="loading||saving" @click="open(row)">详情</el-button></template></el-table-column><template #empty>{{listError?'读取失败，请重新查询':'当前场站没有符合条件的项目'}}</template></el-table></div>
 <el-pagination :total="total" :page-size="20" :current-page="query.page+1" layout="total, prev, next" :disabled="loading||saving" @current-change="query.page=$event-1;load()"/>
 <el-card v-if="detail" class="project-detail" shadow="never"><h2>{{detail.name}}</h2><dl><dt>项目编码</dt><dd>{{detail.projectCode}}</dd><dt>供应商</dt><dd>{{detail.supplierName}}</dd><dt>状态</dt><dd>{{states[detail.status]}}</dd><dt>现场地址</dt><dd>{{detail.siteAddress||'未填写'}}</dd><dt>计划周期</dt><dd>{{detail.plannedStartDate}} — {{detail.plannedEndDate}}</dd></dl><div class="actions"><el-button v-if="detail.status==='PLANNED'" :disabled="!access.canManage||saving||unknown" @click="edit">编辑项目</el-button><template v-if="access.canChangeStatus"><el-button v-if="detail.status==='PLANNED'||detail.status==='SUSPENDED'" :disabled="saving||unknown" @click="status('ACTIVE')">{{detail.status==='PLANNED'?'开工':'复工'}}</el-button><el-button v-if="detail.status==='ACTIVE'" :disabled="saving||unknown" @click="status('SUSPENDED')">暂停</el-button><el-button v-if="detail.status==='ACTIVE'" :disabled="saving||unknown" @click="status('COMPLETED')">完成</el-button><el-button v-if="['PLANNED','ACTIVE','SUSPENDED'].includes(detail.status)" :disabled="saving||unknown" @click="status('CANCELLED')">取消项目</el-button></template><el-button :disabled="saving" @click="detail=undefined">关闭详情</el-button></div></el-card>
 </template>
 <el-dialog v-model="dialog" :title="editing?'编辑场站项目':'新建场站项目'" width="min(680px, calc(100vw - 32px))" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
 <p v-if="error" ref="errorFocus" tabindex="-1" role="alert" class="form-error">{{error}}</p>
 <el-form label-position="top" @submit.prevent="save" :disabled="saving||unknown">
 <el-form-item label="项目编码（必填）"><el-input v-model="form.projectCode" aria-label="项目编码" maxlength="64" :disabled="!!editing" placeholder="大写字母、数字、下划线或短横线"/></el-form-item>
 <el-form-item label="项目名称（必填）"><el-input v-model="form.name" aria-label="项目名称" maxlength="200"/></el-form-item>
 <el-form-item label="供应商（必填）"><el-select v-if="!editing" v-model="form.supplierId" filterable remote :remote-method="findSuppliers" :loading="supplierBusy" aria-label="供应商" placeholder="搜索有效供应商"><el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="`${s.name}（${s.code}）`"/></el-select><el-input v-else :model-value="detail?.supplierName" disabled/></el-form-item>
 <el-form-item label="项目类型"><el-select v-model="form.type" aria-label="项目类型"><el-option label="检维修" value="MAINTENANCE"/><el-option label="工程建设" value="CONSTRUCTION"/><el-option label="技术服务" value="TECHNICAL_SERVICE"/><el-option label="物流" value="LOGISTICS"/><el-option label="其他" value="OTHER"/></el-select></el-form-item>
 <el-form-item label="关联合同 ID（选填，须有效且有权查看）"><el-input v-model="form.contractId" :disabled="!!editing" aria-label="关联合同 ID"/></el-form-item>
 <el-form-item label="负责人 ID（必填，默认当前账号）"><el-input v-model="form.managerId" aria-label="负责人 ID"/></el-form-item>
 <el-form-item label="计划开始（必填）"><el-input v-model="form.plannedStartDate" type="date" aria-label="计划开始"/></el-form-item><el-form-item label="计划结束（必填）"><el-input v-model="form.plannedEndDate" type="date" aria-label="计划结束"/></el-form-item>
 <el-form-item label="现场地址"><el-input v-model="form.siteAddress" aria-label="现场地址" maxlength="300"/></el-form-item><el-form-item label="预算金额"><el-input-number v-model="form.budgetAmount" :min="0" :precision="2" aria-label="预算金额"/></el-form-item><el-form-item label="币种"><el-input v-model="form.currency" maxlength="3" aria-label="币种"/></el-form-item>
 </el-form><template #footer><el-button :disabled="saving" @click="dialog=false">关闭表单</el-button><el-button type="primary" :loading="saving" :disabled="unknown" @click="save">保存项目</el-button></template>
 </el-dialog>
</section>
</template>
<style scoped>
.form-error{color:var(--el-color-danger);border:1px solid var(--el-color-danger-light-7);padding:12px;border-radius:6px;}
.site-project-page {min-width:0;max-width:1200px;}.site-project-page p{line-height:1.6;overflow-wrap:anywhere;}.el-select{width:100%;}.site-choice,.filters,.project-detail{margin-top:20px;}.actions{display:flex;gap:8px;flex-wrap:wrap;}.actions .el-button{margin-left:0;}.table-region{max-width:100%;overflow-x:auto;}dd{margin:4px 0 16px;overflow-wrap:anywhere;}h2{font-size:20px;}.el-alert{margin-bottom:12px;}
</style>

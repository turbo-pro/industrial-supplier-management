<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
type Application=components['schemas']['ExitApplication'];
type Entity=components['schemas']['ExitEntity'];
type ReminderFailure=components['schemas']['ExitReminderFailure'];
type LocalArchive=components['schemas']['ExitLocalArchive'];
type RecoveryInventory=components['schemas']['ExitAccessRecoveryInventory'];
const props=defineProps<{modelValue:boolean;supplier:Pick<components['schemas']['SupplierSummary'],'id'|'name'>|null}>();
const emit=defineEmits<{ 'update:modelValue':[value:boolean];changed:[] }>();
const visible=computed({get:()=>props.modelValue,set:(value:boolean)=>emit('update:modelValue',value)});
const session=useSessionStore(),rows=ref<Application[]>([]),page=ref(0),total=ref(0),loading=ref(false),busy=ref(false);
const form=reactive({type:'NORMAL' as 'NORMAL'|'ELIMINATION',reason:'',evidenceFileId:''});
const states={SUBMITTED:'处置中 / 待独立审批',REJECTED:'已驳回',CANCELLED:'已撤回',BUSINESS_CLOSED:'本地业务已关闭'};
const canApply=ref(false);
const failureRows=ref<ReminderFailure[]>([]),failurePage=ref(0),failureTotal=ref(0),failureLoading=ref(false);
const archive=ref<LocalArchive>(),archiveVisible=ref(false),archiveLoading=ref(false);
const recovery=ref<RecoveryInventory>(),recoveryVisible=ref(false),recoveryLoading=ref(false);
const recoveryChannels:Record<string,string>={PORTAL_ACCOUNT:'供应商门户账号',DOOR_ACCESS:'外部门禁权限',API_CREDENTIAL:'接口凭证'};
type RecoveryTask=components['schemas']['ExitAccessRecoveryTask'];
const discoveryTask=ref<RecoveryTask>(),discoveryDialog=ref(false),discoveryBusy=ref(false);
const recoveryAssignTask=ref<RecoveryTask>(),recoveryAssignDialog=ref(false),recoveryAssignBusy=ref(false);
const recoveryAssignForm=reactive({assigneeId:'',dueDate:'',note:''});
function openRecoveryAssign(task:RecoveryTask){recoveryAssignTask.value=task;Object.assign(recoveryAssignForm,{assigneeId:task.assigneeId??'',dueDate:task.dueDate??'',note:task.assignmentNote??''});recoveryAssignDialog.value=true;}
async function saveRecoveryAssign(){
  const supplierId=props.supplier?.id,inventory=recovery.value,task=recoveryAssignTask.value;
  if(!supplierId||!inventory||!task)return;
  if(!/^[1-9]\d{0,18}$/.test(recoveryAssignForm.assigneeId)||!recoveryAssignForm.note.trim()){ElMessage.warning('请填写有效责任人账号 ID 和分派说明');return;}
  recoveryAssignBusy.value=true;
  try{
    const {data,error}=await session.client.POST('/suppliers/{supplierId}/exit-applications/{id}/access-recovery/{taskId}/assign',{
      params:{path:{supplierId,id:inventory.applicationId,taskId:task.id}},
      body:{assigneeId:recoveryAssignForm.assigneeId,dueDate:recoveryAssignForm.dueDate||null,note:recoveryAssignForm.note.trim(),version:task.version}
    });
    if(error){ElMessage.error(error.error.message+'；如版本变化，请刷新后重试');return;}
    recovery.value=data?.data;recoveryAssignDialog.value=false;ElMessage.success('内部核查责任已留痕；外部回收仍未核验');
  }catch{ElMessage.error('分派失败，请刷新核对');}finally{recoveryAssignBusy.value=false;}
}
const discoveryForm=reactive({finding:'PRESENT' as 'PRESENT'|'ABSENT',evidenceFileId:'',note:''});
function openDiscovery(task:RecoveryTask){
  discoveryTask.value=task;discoveryForm.finding=(task.finding as 'PRESENT'|'ABSENT')??'PRESENT';
  discoveryForm.evidenceFileId=task.evidenceFileId??'';discoveryForm.note=task.discoveryNote??'';discoveryDialog.value=true;
}
async function saveDiscovery(){
  const supplierId=props.supplier?.id,inventory=recovery.value,task=discoveryTask.value;
  if(!supplierId||!inventory||!task)return;
  if(!/^[1-9]\d{0,18}$/.test(discoveryForm.evidenceFileId)||!discoveryForm.note.trim()){
    ElMessage.warning('请选择已上传的有效证据文件并填写核查说明');return;
  }
  discoveryBusy.value=true;
  try{
    const {data,error}=await session.client.POST('/suppliers/{supplierId}/exit-applications/{id}/access-recovery/{taskId}/discovery',{
      params:{path:{supplierId,id:inventory.applicationId,taskId:task.id}},
      body:{finding:discoveryForm.finding,evidenceFileId:discoveryForm.evidenceFileId,note:discoveryForm.note.trim(),version:task.version}
    });
    if(error){ElMessage.error(error.error.message+'；如记录已变化，请刷新后重试');return;}
    recovery.value=data?.data;discoveryDialog.value=false;
    ElMessage.success('核查发现已留痕；外部回收仍未核验');
  }catch{ElMessage.error('保存核查发现失败，请刷新核对');}finally{discoveryBusy.value=false;}
}
async function viewRecovery(row:Application){
  const supplierId=props.supplier?.id;if(!supplierId||row.status!=='BUSINESS_CLOSED')return;
  const version=requestVersion;recoveryLoading.value=true;recovery.value=undefined;
  try{
    const {data,error}=await session.client.GET('/suppliers/{supplierId}/exit-applications/{id}/access-recovery',{params:{path:{supplierId,id:row.id}}});
    if(version!==requestVersion||supplierId!==props.supplier?.id||!props.modelValue)return;
    if(error){ElMessage.error(error.error.message);return;}
    recovery.value=data?.data;recoveryVisible.value=true;
  }catch{if(version===requestVersion)ElMessage.error('访问回收任务加载失败，请重试');}
  finally{recoveryLoading.value=false;}
}
async function viewArchive(row:Application){
  const supplierId=props.supplier?.id;if(!supplierId||row.status!=='BUSINESS_CLOSED')return;
  const version=requestVersion;archiveLoading.value=true;archive.value=undefined;
  try{
    const {data,error}=await session.client.GET('/suppliers/{supplierId}/exit-applications/{id}/archive',{params:{path:{supplierId,id:row.id}}});
    if(version!==requestVersion||supplierId!==props.supplier?.id||!props.modelValue)return;
    if(error){ElMessage.error(error.error.message);return;}
    archive.value=data?.data;archiveVisible.value=true;
  }catch{if(version===requestVersion)ElMessage.error('封存校验加载失败，请重试');}
  finally{archiveLoading.value=false;}
}
async function loadFailures(targetPage=0){
  const supplierId=props.supplier?.id;if(!supplierId)return;
  const version=requestVersion,sequence=++failureRequestVersion;failureLoading.value=true;
  try{
    const {data,error}=await session.client.GET('/suppliers/{supplierId}/exit-applications/reminder-failures',{params:{path:{supplierId},query:{page:targetPage,size:20}}});
    if(version!==requestVersion||sequence!==failureRequestVersion||supplierId!==props.supplier?.id||!props.modelValue)return;
    if(error){failureRows.value=[];failureTotal.value=0;ElMessage.error(error.error.message);return;}
    failureRows.value=data?.data?.items??[];failureTotal.value=data?.data?.total??0;failurePage.value=targetPage;
  }catch{if(version===requestVersion&&sequence===failureRequestVersion){failureRows.value=[];failureTotal.value=0;ElMessage.error('催办失败台账加载失败');}}
  finally{if(version===requestVersion&&sequence===failureRequestVersion)failureLoading.value=false;}
}
const labels:Record<string,string>={OPEN_CONTRACT:'合同',OPEN_PROJECT:'项目',OPEN_PERSON:'人员',OPEN_ASSET:'车辆设备',OPEN_SAFETY:'安全隐患',OPEN_ATTENDANCE:'现场签到',OPEN_QUALITY:'质量不符合项',OPEN_IMPROVEMENT:'绩效改进'};
const assignment=ref(false),selectedApplication=ref<Application>(),selectedEntity=ref<Entity>();
const disposition=reactive({assigneeId:'',note:''});
const deadlineDialog=ref(false),deadlineForm=reactive({dueDate:'',reason:''});
function openDeadline(row:Application,entity:Entity){selectedApplication.value=row;selectedEntity.value=entity;Object.assign(deadlineForm,{dueDate:entity.dueDate??'',reason:''});deadlineDialog.value=true;}
async function saveDeadline(){
  const row=selectedApplication.value,entity=selectedEntity.value,supplierId=props.supplier?.id;
  if(!row||!entity||!supplierId)return;
  if(!deadlineForm.reason.trim()){ElMessage.warning('请填写期限变更原因');return;}
  busy.value=true;
  try{const {error}=await session.client.POST('/suppliers/{supplierId}/exit-applications/{id}/entities/{entityId}/deadline',{params:{path:{supplierId,id:row.id,entityId:entity.id}},body:{dueDate:deadlineForm.dueDate||null,reason:deadlineForm.reason.trim(),version:entity.version,applicationVersion:row.version}});
    if(error){ElMessage.error(error.error.message+'；如版本变化，请刷新后重新编辑');return;}
    deadlineDialog.value=false;ElMessage.success('处置期限已记录，不改变业务结清状态');await load();
  }catch{ElMessage.error('期限更新失败，请刷新核对');}finally{busy.value=false;}
}
async function remind(row:Application,entity:Entity){
  const supplierId=props.supplier?.id;if(!supplierId)return;
  try{await ElMessageBox.confirm('向当前责任人发送站内催办？同一事项 24 小时内只能催办一次，不会完成或放行业务。','退出事项催办',{confirmButtonText:'发送催办',cancelButtonText:'取消'});
    if(supplierId!==props.supplier?.id||!props.modelValue)return;
    busy.value=true;
    const {error}=await session.client.POST('/suppliers/{supplierId}/exit-applications/{id}/entities/{entityId}/remind',{params:{path:{supplierId,id:row.id,entityId:entity.id}},body:{version:entity.version,applicationVersion:row.version}});
    if(error){ElMessage.error(error.error.message);return;}
    ElMessage.success('站内催办已发送');await load();
  }catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error('催办失败，请刷新核对');}finally{busy.value=false;}
}
const entityPages=ref<Record<string,{page:number;total:number;items:Entity[];loading:boolean}>>({});
const entityRequests=new Map<string,number>();
async function loadEntities(row:Application,targetPage:number){
  const supplierId=props.supplier?.id;if(!supplierId)return;
  const version=requestVersion,sequence=(entityRequests.get(row.id)??0)+1;entityRequests.set(row.id,sequence);
  entityPages.value[row.id]={page:targetPage,total:row.entityTotal,items:[],loading:true};
  try{
    const {data,error}=await session.client.GET('/suppliers/{supplierId}/exit-applications/{id}/entities',{params:{path:{supplierId,id:row.id},query:{page:targetPage,size:20}}});
    if(version!==requestVersion||sequence!==entityRequests.get(row.id)||supplierId!==props.supplier?.id||!props.modelValue)return;
    if(error||!data?.data){ElMessage.error(error?.error.message??'事项明细加载失败');return;}
    if(data.data.applicationVersion!==row.version){ElMessage.warning('申请已经变化，请刷新退出申请后再查看明细');return;}
    entityPages.value[row.id]={page:targetPage,total:data.data.total,items:data.data.items,loading:false};
  }catch{if(version===requestVersion&&sequence===entityRequests.get(row.id))ElMessage.error('事项明细加载失败，请刷新重试');}
  finally{if(version===requestVersion&&sequence===entityRequests.get(row.id)&&entityPages.value[row.id])entityPages.value[row.id]!.loading=false;}
}
function openAssign(row:Application,entity:Entity){selectedApplication.value=row;selectedEntity.value=entity;Object.assign(disposition,{assigneeId:entity.assigneeId??session.actorId??'',note:entity.note??''});assignment.value=true;}
async function saveAssignment(){
  const row=selectedApplication.value,entity=selectedEntity.value,supplierId=props.supplier?.id;
  if(!row||!entity||!supplierId)return;
  if(!/^[1-9][0-9]{0,18}$/.test(disposition.assigneeId)||!disposition.note.trim()){ElMessage.warning('请填写有效责任人账号 ID 和处理说明');return;}
  busy.value=true;
  try{
    const {error}=await session.client.POST('/suppliers/{supplierId}/exit-applications/{id}/entities/{entityId}/assign',{
      params:{path:{supplierId,id:row.id,entityId:entity.id}},body:{assigneeId:disposition.assigneeId,note:disposition.note.trim(),version:entity.version,applicationVersion:row.version}
    });
    if(error){ElMessage.error(error.error.message+'；如版本冲突，请刷新后重新编辑');return;}
    assignment.value=false;ElMessage.success('责任与说明已记录，站内通知已发送；处置后须重新核验');await load();
  }catch{ElMessage.error('保存失败，请刷新核对结果');}finally{busy.value=false;}
}
let requestVersion=0,failureRequestVersion=0;
async function load(){
  if(!props.supplier)return;
  const version=++requestVersion,supplierId=props.supplier.id;
  loading.value=true;
  try{
    const {data,error}=await session.client.GET('/suppliers/{supplierId}/exit-applications',{
      params:{path:{supplierId},query:{page:page.value,size:20}}
    });
    if(version!==requestVersion||supplierId!==props.supplier?.id||!props.modelValue)return;
    if(error){rows.value=[];total.value=0;canApply.value=false;ElMessage.error(error.error.message);return;}
    entityPages.value={};entityRequests.clear();rows.value=data?.data?.items??[];total.value=data?.data?.total??0;canApply.value=data?.data?.canApply??false;
    void loadFailures(failurePage.value);
  }catch{if(version===requestVersion){rows.value=[];total.value=0;canApply.value=false;ElMessage.error('退出申请加载失败');}}finally{if(version===requestVersion)loading.value=false;}
}
async function create(){
  if(!props.supplier||!form.reason.trim()||!form.evidenceFileId.trim()){ElMessage.warning('请填写退出原因及依据文件 ID');return;}
  try{
    await ElMessageBox.confirm('提交后将阻止该供应商新增业务，已有业务仍可继续处置。是否提交？','提交退出申请',{type:'warning'});
    busy.value=true;
    const {error}=await session.client.POST('/suppliers/{supplierId}/exit-applications',{
      params:{path:{supplierId:props.supplier.id}},body:{...form}
    });
    if(error){ElMessage.error(error.error.message);return;}
    ElMessage.success('退出申请已提交，新增业务已阻止');form.reason='';form.evidenceFileId='';page.value=0;
    await load();emit('changed');
  }catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error('提交失败，请刷新核对结果');}finally{busy.value=false;}
}
async function act(row:Application,action:'recheck'|'cancel'){
  if(!props.supplier)return;
  try{
    if(action==='cancel')await ElMessageBox.confirm('撤回本人的退出申请？其他限制仍继续有效。','撤回退出申请');
    busy.value=true;const params={path:{supplierId:props.supplier.id,id:row.id}};
    const result=action==='recheck'
      ?await session.client.POST('/suppliers/{supplierId}/exit-applications/{id}/recheck',{params,body:{version:row.version}})
      :await session.client.POST('/suppliers/{supplierId}/exit-applications/{id}/cancel',{params,body:{version:row.version}});
    if(result.error){ElMessage.error(result.error.error.message);return;}
    ElMessage.success(action==='recheck'?'处置项已重新核验':'退出申请已撤回');await load();emit('changed');
  }catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error('操作失败，请刷新核对结果');}finally{busy.value=false;}
}
async function review(row:Application,decision:'APPROVE'|'REJECT'){
  if(!props.supplier)return;
  try{
    const comment=(await ElMessageBox.prompt(decision==='APPROVE'?'批准将不可逆关闭本地业务；外部账号及门禁仍标记为未核验。请填写审批意见':'请填写驳回原因','独立退出审批',{inputValidator:(value:string)=>!!value.trim()||'审批意见不能为空'})).value;
    busy.value=true;
    const {error}=await session.client.POST('/suppliers/{supplierId}/exit-applications/{id}/review',{
      params:{path:{supplierId:props.supplier.id,id:row.id}},body:{decision,comment,version:row.version}
    });
    if(error){ElMessage.error(error.error.message);return;}
    ElMessage.success(decision==='APPROVE'?'本地业务已关闭，外部访问回收仍待核验':'申请已驳回');
    await load();emit('changed');
  }catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error('审批失败，请刷新核对结果');}finally{busy.value=false;}
}
watch(()=>[props.modelValue,props.supplier?.id],()=>{requestVersion++;failureRequestVersion++;entityPages.value={};entityRequests.clear();assignment.value=false;deadlineDialog.value=false;archiveVisible.value=false;archive.value=undefined;rows.value=[];total.value=0;canApply.value=false;loading.value=false;failureRows.value=[];failureTotal.value=0;failurePage.value=0;failureLoading.value=false;if(props.modelValue){page.value=0;Object.assign(form,{type:'NORMAL',reason:'',evidenceFileId:''});void load();}});
</script>
<template>
  <el-dialog v-model="visible" :title="`${supplier?.name??''} · 退出流程`" width="1040px" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <el-alert title="本页只审批本地业务关闭，不代表外部账号、门禁或接口凭证已回收。紧急淘汰与强制移交尚未接入。" type="warning" :closable="false"/>
    <el-form v-if="canApply" label-position="top" style="margin-top:16px">
      <el-form-item label="退出类型"><el-select v-model="form.type"><el-option label="正常退出" value="NORMAL"/><el-option label="淘汰" value="ELIMINATION"/></el-select></el-form-item>
      <el-form-item label="退出原因"><el-input v-model="form.reason" type="textarea" maxlength="2000" show-word-limit/></el-form-item>
      <el-form-item label="已上传的依据文件 ID"><el-input v-model="form.evidenceFileId" maxlength="19"/></el-form-item>
      <el-button type="primary" :loading="busy" :disabled="loading" @click="create">提交退出申请</el-button>
    </el-form>
    <p>展开申请查看处置项及历史。初始数量保留；当前数量为最近核验快照，批准时始终重新检查。</p>
    <el-card shadow="never" style="margin-bottom:16px">
      <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span>自动催办投递记录（{{failureTotal}}）</span><el-button :disabled="failureLoading" @click="loadFailures(failurePage)">刷新</el-button></div></template>
      <el-alert title="仅记录自动催办失败及其后续成功恢复；历史失败不代表当前事项仍未结清。错误代码不包含异常堆栈。" type="info" :closable="false"/>
      <el-table v-loading="failureLoading" :data="failureRows" style="margin-top:12px">
        <el-table-column prop="entityId" label="事项 ID" min-width="160"/>
        <el-table-column label="投递状态" width="125"><template #default="{row:failure}"><el-tag :type="failure.status==='DELIVERED'?'success':'danger'">{{failure.status==='DELIVERED'?'已恢复':'失败待重试'}}</el-tag></template></el-table-column>
        <el-table-column label="当前事项" width="170"><template #default="{row:failure}">{{failure.applicationStatus==='SUBMITTED'&&failure.entityState==='OPEN'?'处置中':'已结束/已结清'}}</template></el-table-column>
        <el-table-column prop="failureCount" label="失败次数" width="95"/>
        <el-table-column prop="reasonCode" label="错误代码" min-width="190"/>
        <el-table-column prop="lastFailedAt" label="最近失败（UTC）" width="190"/>
        <el-table-column prop="resolvedAt" label="恢复时间（UTC）" width="190"/>
      </el-table>
      <el-pagination :current-page="failurePage+1" :total="failureTotal" :page-size="20" layout="total, prev, pager, next" :disabled="failureLoading" @current-change="(value:number)=>loadFailures(value-1)"/>
    </el-card>
    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column type="expand">
        <template #default="{row}">
          <el-alert v-if="row.result" title="完成范围：本地业务。外部访问回收：未核验。" type="warning" :closable="false"/>
          <el-button v-if="row.status==='BUSINESS_CLOSED'" :loading="archiveLoading" style="margin:10px 0" @click="viewArchive(row)">查看本地证据封存校验</el-button>
          <el-button v-if="row.status==='BUSINESS_CLOSED'" :loading="recoveryLoading" style="margin:10px 0" @click="viewRecovery(row)">查看外部访问核查任务</el-button>
          <el-table :data="row.items">
            <el-table-column prop="label" label="处置检查项"/><el-table-column prop="initialCount" label="初始数量" width="100"/>
            <el-table-column prop="currentCount" label="当前数量" width="100"/><el-table-column prop="checkedAt" label="核验时间" width="200"/>
            <el-table-column label="业务入口" width="100"><template #default="{row:item}"><router-link :to="item.route">查看</router-link></template></el-table-column>
          </el-table>
          <p>逐实体处置：责任分派不会改变业务状态。业务页面仍独立校验其数据权限；下列编号不包含人员证件等敏感明文。</p>
          <el-table v-loading="entityPages[row.id]?.loading??false" :data="entityPages[row.id]?.items??row.entities??[]" row-key="id">
            <el-table-column label="事项类型" width="140"><template #default="{row:entity}">{{labels[entity.code]??entity.code}}</template></el-table-column>
            <el-table-column prop="sourceId" label="业务记录 ID" width="180"/>
            <el-table-column label="核验状态" width="130"><template #default="{row:entity}"><el-tag :type="entity.state==='CLEARED'?'success':'warning'">{{entity.state==='CLEARED'?'已核验结清':'待处置'}}</el-tag></template></el-table-column>
            <el-table-column prop="assigneeId" label="责任人账号 ID" width="180"/>
            <el-table-column prop="note" label="处理说明" min-width="200" show-overflow-tooltip/>
            <el-table-column label="处置期限" width="150"><template #default="{row:entity}">{{entity.dueDate??'未设置'}}<el-tag v-if="entity.overdue" type="danger">已逾期</el-tag></template></el-table-column>
            <el-table-column prop="lastRemindedAt" label="上次催办（UTC）" width="200"/>
            <el-table-column prop="clearedAt" label="核验结清时间" width="180"/>
            <el-table-column label="操作" width="320"><template #default="{row:entity}"><router-link :to="entity.route">业务入口</router-link><template v-if="row.status==='SUBMITTED'&&entity.state==='OPEN'"><el-button link type="primary" :disabled="busy" @click="openAssign(row,entity)">责任/说明</el-button><el-button link :disabled="busy" @click="openDeadline(row,entity)">期限</el-button><el-button link type="warning" :disabled="busy||!entity.assigneeId" @click="remind(row,entity)">催办</el-button></template></template></el-table-column>
          </el-table>
          <el-pagination :current-page="(entityPages[row.id]?.page??0)+1" :total="entityPages[row.id]?.total??row.entityTotal" :page-size="20" layout="total, prev, pager, next" :disabled="busy||!!entityPages[row.id]?.loading" @current-change="(value:number)=>loadEntities(row,value-1)"/>
          <el-timeline style="margin-top:16px"><el-timeline-item v-for="event in row.events" :key="event.id" :timestamp="event.createdAt">{{event.action==='AUTO_ENTITY_REMIND'?'自动催办':event.action}} · 操作人 {{event.actorId==='0'?'系统任务':event.actorId}} · {{event.comment}}</el-timeline-item></el-timeline>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90"><template #default="{row}">{{row.type==='NORMAL'?'正常退出':'淘汰'}}</template></el-table-column>
      <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip/>
      <el-table-column label="状态" width="180"><template #default="{row}">{{states[row.status as keyof typeof states]}}</template></el-table-column>
      <el-table-column prop="createdBy" label="申请人" width="90"/><el-table-column prop="reviewComment" label="审批意见" min-width="140" show-overflow-tooltip/>
      <el-table-column label="操作" width="260">
        <template #default="{row}"><template v-if="row.status==='SUBMITTED'">
          <el-button link :disabled="busy" @click="act(row,'recheck')">重新核验</el-button>
          <el-button v-if="row.createdBy===session.actorId" link :disabled="busy" @click="act(row,'cancel')">撤回</el-button>
          <template v-else><el-button link type="success" :disabled="busy||!row.localReady" @click="review(row,'APPROVE')">批准</el-button><el-button link type="danger" :disabled="busy" @click="review(row,'REJECT')">驳回</el-button></template>
        </template></template>
      </el-table-column>
    </el-table>
    <el-pagination :current-page="page+1" :total="total" :page-size="20" layout="total, prev, pager, next" @current-change="(value:number)=>{page=value-1;load()}"/>
    <template #footer><el-button :disabled="busy" @click="load">刷新</el-button><el-button :disabled="busy" @click="visible=false">关闭</el-button></template>
  </el-dialog>
  <el-dialog v-model="assignment" title="退出事项责任与处理说明" width="600px" append-to-body :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <el-alert title="保存会向本租户有效责任人发送站内通知；通知失败则不会保存。本操作不能手工完成或移交未结业务。" type="warning" :closable="false"/>
    <el-form label-position="top"><el-form-item label="责任人账号 ID"><el-input v-model="disposition.assigneeId" maxlength="19" :disabled="busy"/><el-button link :disabled="busy" @click="disposition.assigneeId=session.actorId??''">分派给我</el-button></el-form-item><el-form-item label="处理说明"><el-input v-model="disposition.note" type="textarea" maxlength="1800" show-word-limit :disabled="busy"/></el-form-item></el-form>
    <template #footer><el-button :disabled="busy" @click="assignment=false">取消</el-button><el-button type="primary" :loading="busy" @click="saveAssignment">保存责任与说明</el-button></template>
  </el-dialog>
  <el-dialog v-model="deadlineDialog" title="退出事项处置期限" width="560px" append-to-body :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <el-alert title="按中国业务日期计算，今天至一年内；清空日期表示取消期限。所有变更须记录原因，不自动关闭业务。" type="info" :closable="false"/>
    <el-form label-position="top"><el-form-item label="处置期限"><el-date-picker v-model="deadlineForm.dueDate" type="date" value-format="YYYY-MM-DD" clearable :disabled="busy"/></el-form-item><el-form-item label="变更原因"><el-input v-model="deadlineForm.reason" type="textarea" maxlength="1000" show-word-limit :disabled="busy"/></el-form-item></el-form>
    <template #footer><el-button :disabled="busy" @click="deadlineDialog=false">取消</el-button><el-button type="primary" :loading="busy" @click="saveDeadline">保存期限</el-button></template>
  </el-dialog>
  <el-dialog v-model="archiveVisible" title="本地退出证据封存校验" width="620px" append-to-body>
    <template v-if="archive">
      <el-alert :title="archive.integrityVerified?'本地记录校验一致':'本地记录校验不一致，请联系管理员核查'" :type="archive.integrityVerified?'success':'error'" :closable="false"/>
      <p>此清单仅封存本地数据库记录的摘要和数量，不复制依据文件，也不证明外部账号、门禁或接口凭证已回收。</p>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="申请 ID">{{archive.applicationId}}</el-descriptions-item>
        <el-descriptions-item label="依据文件 ID">{{archive.evidenceFileId}}</el-descriptions-item>
        <el-descriptions-item label="处置检查 / 逐实体 / 事件">{{archive.itemCount}} / {{archive.entityCount}} / {{archive.eventCount}}</el-descriptions-item>
        <el-descriptions-item label="封存时间">{{archive.sealedAt}}</el-descriptions-item>
        <el-descriptions-item label="SHA-256 摘要"><span style="overflow-wrap:anywhere">{{archive.digestSha256}}</span></el-descriptions-item>
        <el-descriptions-item label="外部访问回收">未核验</el-descriptions-item>
      </el-descriptions>
    </template>
  </el-dialog>
  <el-dialog v-model="recoveryVisible" title="外部访问核查任务" width="620px" append-to-body>
    <template v-if="recovery">
      <el-alert title="仅生成核查目录：尚未发现或绑定外部账号，也未执行门户、门禁或凭证回收。不得将本地退出视为外部回收完成。" type="warning" :closable="false"/>
      <p>退出申请 {{recovery.applicationId}} · 外部访问状态：未核验</p>
      <el-alert v-if="recovery.tasks.length===0" title="历史退出记录未建立核查目录，外部访问仍未核验；不得视为无需回收。" type="error" :closable="false"/>
      <el-table :data="recovery.tasks" row-key="id">
        <el-table-column type="expand"><template #default="{row:task}"><p v-if="task.assignmentNote">分派说明：{{task.assignmentNote}}</p><el-timeline><el-timeline-item v-for="event in task.assignments" :key="event.id" :timestamp="event.createdAt">责任人 {{event.assigneeId}} · 期限 {{event.dueDate??'未设置'}} · 分派人 {{event.actorId}}<p>{{event.note}}</p></el-timeline-item><el-timeline-item v-for="event in task.events" :key="event.id" :timestamp="event.createdAt">{{event.finding==='PRESENT'?'发现访问记录':'未发现访问记录'}} · 证据 {{event.evidenceFileId}} · 操作人 {{event.actorId}}<p>{{event.note}}</p></el-timeline-item></el-timeline></template></el-table-column>
        <el-table-column label="核查渠道"><template #default="{row:task}">{{recoveryChannels[task.channel]??task.channel}}</template></el-table-column>
        <el-table-column label="责任人 / 期限" width="190"><template #default="{row:task}">{{task.assigneeId??'未分派'}} / {{task.dueDate??'未设置'}}</template></el-table-column>
        <el-table-column label="发现记录" width="150"><template #default="{row:task}">{{task.finding==='PRESENT'?'发现访问记录':task.finding==='ABSENT'?'未发现访问记录':'待核查'}}</template></el-table-column>
        <el-table-column label="操作" width="160"><template #default="{row:task}"><el-button link type="primary" @click="openRecoveryAssign(task)">分派</el-button><el-button link type="primary" @click="openDiscovery(task)">记录发现</el-button></template></el-table-column>
      </el-table>
    </template>
  </el-dialog>
  <el-dialog v-model="recoveryAssignDialog" :title="`${recoveryChannels[recoveryAssignTask?.channel??'']??'外部访问'} · 核查分派`" width="560px" append-to-body :close-on-click-modal="false">
    <el-alert title="分派成功会发送站内通知；通知失败则整笔分派回滚。不等于外部账号或权限已回收。" type="warning" :closable="false"/>
    <el-form label-position="top"><el-form-item label="责任人账号 ID"><el-input v-model="recoveryAssignForm.assigneeId" maxlength="19"/><el-button link @click="recoveryAssignForm.assigneeId=session.actorId??''">分派给我</el-button></el-form-item><el-form-item label="核查期限"><el-date-picker v-model="recoveryAssignForm.dueDate" type="date" value-format="YYYY-MM-DD" clearable/></el-form-item><el-form-item label="分派说明"><el-input v-model="recoveryAssignForm.note" type="textarea" maxlength="1000" show-word-limit/></el-form-item></el-form>
    <template #footer><el-button :disabled="recoveryAssignBusy" @click="recoveryAssignDialog=false">取消</el-button><el-button type="primary" :loading="recoveryAssignBusy" @click="saveRecoveryAssign">保存分派</el-button></template>
  </el-dialog>
  <el-dialog v-model="discoveryDialog" :title="`${recoveryChannels[discoveryTask?.channel??'']??'外部访问'} · 核查发现`" width="560px" append-to-body :close-on-click-modal="false">
    <el-alert title="仅记录是否发现外部账号或权限，不代表账号已停用或回收；不得填写口令、密钥等秘密。" type="warning" :closable="false"/>
    <el-form label-position="top">
      <el-form-item label="核查结论"><el-radio-group v-model="discoveryForm.finding"><el-radio value="PRESENT">发现访问记录</el-radio><el-radio value="ABSENT">未发现访问记录</el-radio></el-radio-group></el-form-item>
      <el-form-item label="证据文件 ID"><el-input v-model="discoveryForm.evidenceFileId" maxlength="19"/></el-form-item>
      <el-form-item label="核查说明"><el-input v-model="discoveryForm.note" type="textarea" maxlength="1000" show-word-limit/></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="discoveryBusy" @click="discoveryDialog=false">取消</el-button><el-button type="primary" :loading="discoveryBusy" @click="saveDiscovery">保存发现记录</el-button></template>
  </el-dialog>
</template>

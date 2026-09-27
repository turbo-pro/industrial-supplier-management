<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
type Application=components['schemas']['ExitApplication'];
type Entity=components['schemas']['ExitEntity'];
const props=defineProps<{modelValue:boolean;supplier:components['schemas']['SupplierSummary']|null}>();
const emit=defineEmits<{ 'update:modelValue':[value:boolean];changed:[] }>();
const visible=computed({get:()=>props.modelValue,set:(value:boolean)=>emit('update:modelValue',value)});
const session=useSessionStore(),rows=ref<Application[]>([]),page=ref(0),total=ref(0),loading=ref(false),busy=ref(false);
const form=reactive({type:'NORMAL' as 'NORMAL'|'ELIMINATION',reason:'',evidenceFileId:''});
const states={SUBMITTED:'处置中 / 待独立审批',REJECTED:'已驳回',CANCELLED:'已撤回',BUSINESS_CLOSED:'本地业务已关闭'};
const canApply=ref(false);
const labels:Record<string,string>={OPEN_CONTRACT:'合同',OPEN_PROJECT:'项目',OPEN_PERSON:'人员',OPEN_ASSET:'车辆设备',OPEN_SAFETY:'安全隐患',OPEN_ATTENDANCE:'现场签到',OPEN_QUALITY:'质量不符合项',OPEN_IMPROVEMENT:'绩效改进'};
const assignment=ref(false),selectedApplication=ref<Application>(),selectedEntity=ref<Entity>();
const disposition=reactive({assigneeId:'',note:''});
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
    assignment.value=false;ElMessage.success('责任与处理说明已记录；请在业务页面处置后重新核验');await load();
  }catch{ElMessage.error('保存失败，请刷新核对结果');}finally{busy.value=false;}
}
let requestVersion=0;
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
watch(()=>[props.modelValue,props.supplier?.id],()=>{requestVersion++;entityPages.value={};entityRequests.clear();assignment.value=false;rows.value=[];total.value=0;canApply.value=false;loading.value=false;if(props.modelValue){page.value=0;Object.assign(form,{type:'NORMAL',reason:'',evidenceFileId:''});void load();}});
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
    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column type="expand">
        <template #default="{row}">
          <el-alert v-if="row.result" title="完成范围：本地业务。外部访问回收：未核验。" type="warning" :closable="false"/>
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
            <el-table-column prop="clearedAt" label="核验结清时间" width="180"/>
            <el-table-column label="操作" width="200"><template #default="{row:entity}"><router-link :to="entity.route">业务入口</router-link><el-button v-if="row.status==='SUBMITTED'&&entity.state==='OPEN'" link type="primary" :disabled="busy" @click="openAssign(row,entity)">责任/说明</el-button></template></el-table-column>
          </el-table>
          <el-pagination :current-page="(entityPages[row.id]?.page??0)+1" :total="entityPages[row.id]?.total??row.entityTotal" :page-size="20" layout="total, prev, pager, next" :disabled="busy||!!entityPages[row.id]?.loading" @current-change="(value:number)=>loadEntities(row,value-1)"/>
          <el-timeline style="margin-top:16px"><el-timeline-item v-for="event in row.events" :key="event.id" :timestamp="event.createdAt">{{event.action}} · 操作人 {{event.actorId}} · {{event.comment}}</el-timeline-item></el-timeline>
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
    <el-alert title="只能分派给本租户有效账号；本操作不能手工完成或移交未结业务。" type="warning" :closable="false"/>
    <el-form label-position="top"><el-form-item label="责任人账号 ID"><el-input v-model="disposition.assigneeId" maxlength="19" :disabled="busy"/><el-button link :disabled="busy" @click="disposition.assigneeId=session.actorId??''">分派给我</el-button></el-form-item><el-form-item label="处理说明"><el-input v-model="disposition.note" type="textarea" maxlength="1800" show-word-limit :disabled="busy"/></el-form-item></el-form>
    <template #footer><el-button :disabled="busy" @click="assignment=false">取消</el-button><el-button type="primary" :loading="busy" @click="saveAssignment">保存责任与说明</el-button></template>
  </el-dialog>
</template>

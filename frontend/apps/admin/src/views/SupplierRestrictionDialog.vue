<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
const props=defineProps<{modelValue:boolean;supplier:components['schemas']['SupplierSummary']|null}>();
const emit=defineEmits<{ 'update:modelValue':[value:boolean] }>();
const visible=computed({get:()=>props.modelValue,set:(value:boolean)=>emit('update:modelValue',value)});
const session=useSessionStore();
const action=ref<components['schemas']['RestrictionExplainAction']>('PROJECT_CREATE');
const result=ref<components['schemas']['RestrictionExplanation']|null>(null);
const busy=ref(false);
const gateHits=ref<components['schemas']['RestrictionGateHit'][]>([]),gateTotal=ref(0),gatePage=ref(0),gateLoading=ref(false);
let requestVersion=0;
const actions={CONTRACT_CREATE:'新建合同',CONTRACT_ACTIVATE:'合同生效',QUALIFICATION_APPLY:'准入申请',PROJECT_CREATE:'新建项目',PERSON_REGISTER:'人员登记',PERSON_ACTIVATE:'人员启用',ASSET_REGISTER:'车辆设备登记',ASSET_USE:'车辆设备投入使用',RESOURCE_ASSIGN:'资源操作（兼容旧查询）',SITE_ENTER:'现场进场',START_WORK:'项目开工',RESUME_WORK:'项目复工'};
const decisions={ALLOW:'企业级检查未命中阻断',WARN:'观察提醒',DENY:'禁止新增业务'};
async function explain(){
  if(!props.supplier)return;
  const version=++requestVersion;
  busy.value=true;result.value=null;
  try{
    const {data,error}=await session.client.POST('/suppliers/{supplierId}/restriction-explanation',{
      params:{path:{supplierId:props.supplier.id},query:{action:action.value}}
    });
    if(version!==requestVersion)return;
    if(error){ElMessage.error(error.error.message);return;}result.value=data?.data??null;
  }catch{if(version===requestVersion)ElMessage.error('限制解释查询失败，请重试');}finally{if(version===requestVersion)busy.value=false;}
}
async function loadGateHits(targetPage=0){const supplierId=props.supplier?.id;if(!supplierId)return;
  const version=requestVersion;gateLoading.value=true;
  try{const {data,error}=await session.client.GET('/suppliers/{supplierId}/restriction-gate-hits',{params:{path:{supplierId},query:{page:targetPage,size:20}}});
    if(version!==requestVersion||supplierId!==props.supplier?.id||!props.modelValue)return;
    if(error||!data?.data){ElMessage.error(error?.error.message??'命中台账读取失败');return;}
    gateHits.value=data.data.items;gateTotal.value=data.data.total;gatePage.value=targetPage;
  }catch{if(version===requestVersion)ElMessage.error('命中台账读取失败，请重试');}finally{if(version===requestVersion)gateLoading.value=false;}}
watch(()=>props.modelValue,(open)=>{requestVersion++;busy.value=false;gateLoading.value=false;result.value=null;gateHits.value=[];gateTotal.value=0;gatePage.value=0;if(open)action.value='PROJECT_CREATE';});
watch(()=>props.supplier?.id,()=>{requestVersion++;busy.value=false;gateLoading.value=false;result.value=null;gateHits.value=[];gateTotal.value=0;gatePage.value=0;});
watch(action,()=>{result.value=null;});
</script>
<template>
  <el-dialog v-model="visible" :title="`${supplier?.name??''} · 限制解释`" width="820px" :close-on-click-modal="!busy">
    <el-alert title="只解释当前企业级限制、供应商状态与待处置退出申请；不是业务放行凭证。组织/工厂范围及具体业务前置条件尚未纳入此页。" type="warning" :closable="false"/>
    <el-form inline style="margin-top:16px">
      <el-form-item label="业务动作"><el-select v-model="action" :disabled="busy" style="width:200px"><el-option v-for="(label,key) in actions" :key="key" :label="label" :value="key"/></el-select></el-form-item>
      <el-button type="primary" :loading="busy" @click="explain">查询全部命中</el-button>
    </el-form>
    <template v-if="result">
      <el-alert :title="decisions[result.decision]" :type="result.decision==='DENY'?'error':result.decision==='WARN'?'warning':'info'" :closable="false"/>
      <p>业务日期：{{result.businessDate}}（中国标准时间）。企业级规则与新增业务拦截共用判定接口；查询记录已审计，提交业务时仍会重新校验，不以此快照放行。</p>
      <el-table :data="result.hits" empty-text="当前企业级检查无命中，仍需满足具体业务条件">
        <el-table-column prop="code" label="命中类型" width="160"/>
        <el-table-column prop="sourceId" label="来源记录 ID" width="170"/>
        <el-table-column label="判定" width="90"><template #default="{row}">{{row.decision==='DENY'?'阻断':'提醒'}}</template></el-table-column>
        <el-table-column prop="explanation" label="解释" min-width="260"/>
      </el-table>
    </template>
    <el-divider>实际业务门禁命中</el-divider>
    <p>仅记录业务入口重验时的观察提醒或阻断；上方解释查询不写入此台账。门禁命中不代表业务已提交。</p>
    <el-button :loading="gateLoading" @click="loadGateHits(0)">读取命中台账</el-button>
    <el-table v-if="gateTotal||gateHits.length" v-loading="gateLoading" :data="gateHits" style="margin-top:12px">
      <el-table-column prop="createdAt" label="判定时间" width="185"/><el-table-column label="业务动作" width="170"><template #default="{row}">{{actions[row.action as keyof typeof actions]??row.action}}</template></el-table-column>
      <el-table-column prop="code" label="命中类型" width="140"/><el-table-column prop="sourceId" label="来源 ID" width="155"/>
      <el-table-column label="结果" width="85"><template #default="{row}"><el-tag :type="row.decision==='DENY'?'danger':'warning'">{{row.decision==='DENY'?'阻断':'提醒'}}</el-tag></template></el-table-column>
      <el-table-column prop="actorId" label="操作人 ID" width="120"/>
    </el-table>
    <el-pagination v-if="gateTotal>20" :current-page="gatePage+1" :total="gateTotal" :page-size="20" layout="total, prev, pager, next" @current-change="(value:number)=>loadGateHits(value-1)"/>
    <template #footer><el-button :disabled="busy" @click="visible=false">关闭</el-button></template>
  </el-dialog>
</template>

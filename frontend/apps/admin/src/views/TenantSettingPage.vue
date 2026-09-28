<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
const session=useSessionStore();
const setting=ref<components['schemas']['TenantSetting']>();
const reminderEnabled=ref<components['schemas']['TenantSetting']>();
const reminderInterval=ref<components['schemas']['TenantSetting']>();
const escalationRecipient=ref<components['schemas']['TenantSetting']>();
const escalationDays=ref<components['schemas']['TenantSetting']>();
const recipientId=ref('0'),afterDays=ref(3);
type EscalationUser=components['schemas']['EscalationUser'];
const userOptions=ref<EscalationUser[]>([]),selectedUser=ref<EscalationUser>(),usersLoading=ref(false);
const displayedUsers=computed(()=>selectedUser.value&&!userOptions.value.some(user=>user.id===selectedUser.value?.id)
  ? [selectedUser.value,...userOptions.value] : userOptions.value);
let usersRequest=0;
async function searchEscalationUsers(keyword=''){
  const request=++usersRequest;usersLoading.value=true;
  try{
    const {data,error}=await session.client.GET('/configuration/settings/exit-escalation-users',{params:{query:{keyword,page:0,size:50}}});
    if(request!==usersRequest)return;
    if(error){userOptions.value=[];ElMessage.error(error.error.message);return;}
    userOptions.value=data?.data?.items??[];
    if(recipientId.value!=='0')selectedUser.value=userOptions.value.find(user=>user.id===recipientId.value)??selectedUser.value;
  }catch{if(request===usersRequest){userOptions.value=[];ElMessage.error('升级接收人列表加载失败');}}
  finally{if(request===usersRequest)usersLoading.value=false;}
}
function changeRecipient(value:string){selectedUser.value=displayedUsers.value.find(user=>user.id===value);}
function recipientDropdownVisible(visible:boolean){if(visible)void searchEscalationUsers();}
const days=ref(7),enabled=ref(false),hours=ref(24),loading=ref(false),saving=ref(false);
async function load(){
  loading.value=true;
  try{
    const {data,error}=await session.client.GET('/configuration/settings');
    if(error){ElMessage.error(error.error.message);return;}
    setting.value=data?.data?.find(item=>item.key==='restriction.watchPeriodDays');
    if(setting.value)days.value=Number(setting.value.value);
    reminderEnabled.value=data?.data?.find(item=>item.key==='exit.autoReminderEnabled');
    reminderInterval.value=data?.data?.find(item=>item.key==='exit.autoReminderIntervalHours');
    escalationRecipient.value=data?.data?.find(item=>item.key==='exit.escalationRecipientId');
    escalationDays.value=data?.data?.find(item=>item.key==='exit.escalationAfterDays');
    if(reminderEnabled.value)enabled.value=reminderEnabled.value.value==='1';
    if(reminderInterval.value)hours.value=Number(reminderInterval.value.value);
    if(escalationRecipient.value){recipientId.value=escalationRecipient.value.value;
      if(recipientId.value!=='0')void searchEscalationUsers(recipientId.value);else void searchEscalationUsers();}
    if(escalationDays.value)afterDays.value=Number(escalationDays.value.value);
  }catch{ElMessage.error('网络异常，配置加载失败');}finally{loading.value=false;}
}
async function saveEscalation(){
  if(!escalationRecipient.value||!escalationDays.value||!/^(0|[1-9]\d{0,18})$/.test(recipientId.value)||BigInt(recipientId.value)>9223372036854775807n||!Number.isInteger(afterDays.value)||afterDays.value<1||afterDays.value>365){
    ElMessage.warning('请输入有效用户 ID 和 1 至 365 天的升级阈值');return;
  }
  saving.value=true;
  try{
    // Disable the recipient first; when enabling, set the threshold before activating the recipient.
    const changes:Array<[components['schemas']['TenantSetting'],string]>=recipientId.value==='0'
      ? [[escalationRecipient.value,'0'],[escalationDays.value,String(afterDays.value)]]
      : [[escalationDays.value,String(afterDays.value)],[escalationRecipient.value,recipientId.value]];
    for(const [item,value] of changes){
      const {data,error,response}=await session.client.PUT('/configuration/settings/{settingKey}',{
        params:{path:{settingKey:item.key},header:{'Idempotency-Key':crypto.randomUUID()}},body:{value,version:item.version}
      });
      if(error){ElMessage.error(response.status===409?'配置已被更新，请刷新后重试':error.error.message);await load();return;}
      if(data?.data){if(item.key==='exit.escalationRecipientId')escalationRecipient.value=data.data;else escalationDays.value=data.data;}
    }
    ElMessage.success('退出逾期升级策略已保存');
  }catch{ElMessage.error('网络异常，请刷新核对保存结果');await load();}finally{saving.value=false;}
}
async function saveReminder(){
  if(!reminderEnabled.value||!reminderInterval.value||!Number.isInteger(hours.value)||hours.value<24||hours.value>720){
    ElMessage.warning('催办间隔须为 24 至 720 小时的整数');return;
  }
  saving.value=true;
  try{
    // Enabling saves interval first; disabling saves the switch first so a stale interval cannot keep reminders active.
    const changes:Array<[components['schemas']['TenantSetting'],string]>=enabled.value
      ? [[reminderInterval.value,String(hours.value)],[reminderEnabled.value,'1']]
      : [[reminderEnabled.value,'0'],[reminderInterval.value,String(hours.value)]];
    for(const [item,value] of changes){
      const {data,error,response}=await session.client.PUT('/configuration/settings/{settingKey}',{
        params:{path:{settingKey:item.key},header:{'Idempotency-Key':crypto.randomUUID()}},
        body:{value,version:item.version}
      });
      if(error){ElMessage.error(response.status===409?'配置已被更新，请刷新后重试':error.error.message);await load();return;}
      if(data?.data){if(item.key==='exit.autoReminderEnabled')reminderEnabled.value=data.data;else reminderInterval.value=data.data;}
    }
    ElMessage.success('退出自动催办策略已保存');
  }catch{ElMessage.error('网络异常，请刷新核对保存结果');await load();}finally{saving.value=false;}
}
async function save(){
  if(!setting.value||!Number.isInteger(days.value)||days.value<1||days.value>3650){
    ElMessage.warning('观察天数须为 1 至 3650 的整数');return;
  }
  saving.value=true;
  try{
    const {data,error,response}=await session.client.PUT('/configuration/settings/{settingKey}',{
      params:{path:{settingKey:setting.value.key},header:{'Idempotency-Key':crypto.randomUUID()}},
      body:{value:String(days.value),version:setting.value.version}
    });
    if(error){ElMessage.error(response.status===409?'配置已被更新，请刷新后重新保存':error.error.message);return;}
    if(data?.data)setting.value=data.data;
    ElMessage.success('租户观察期配置已保存');
  }catch{ElMessage.error('网络异常，请刷新核对保存结果');}finally{saving.value=false;}
}
onMounted(load);
</script>
<template>
  <div class="page">
    <div class="page-heading"><div><h1>租户配置 · 风险观察</h1><p>仅影响当前租户，配置修改须经后端权限校验</p></div><el-button :disabled="saving||loading" @click="load">刷新</el-button></div>
    <el-card v-loading="loading" shadow="never">
      <el-alert title="实际观察期取租户配置、部署级最低观察期及待审申请快照中的最大值。下调配置不会缩短已有待审申请的观察期。" type="info" :closable="false"/>
      <el-form v-if="setting" label-position="top">
        <el-form-item label="观察名单最短观察天数"><el-input-number v-model="days" :min="1" :max="3650" :precision="0" :disabled="saving"/></el-form-item>
        <p>默认 7 天，从原观察名单批准时间起算。批准解除时重新检查，质量、安全及绩效整改条件不受此配置影响。</p>
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      </el-form>
      <el-empty v-else-if="!loading" description="配置尚未加载或无权限"/>
    </el-card>
    <el-card shadow="never" style="margin-top:16px">
      <template #header>退出处置 · 自动催办</template>
      <el-alert title="默认关闭。仅对已逾期、仍未结清且已分派有效责任人的事项发送站内消息；多实例共享限频，失败不会记录为已发送。" type="info" :closable="false"/>
      <el-form v-if="reminderEnabled&&reminderInterval" label-position="top" style="margin-top:16px">
        <el-form-item label="启用自动催办"><el-switch v-model="enabled" :disabled="saving"/></el-form-item>
        <el-form-item label="重复催办间隔（小时）"><el-input-number v-model="hours" :min="24" :max="720" :precision="0" :disabled="saving"/></el-form-item>
        <el-button type="primary" :loading="saving" @click="saveReminder">保存催办策略</el-button>
      </el-form>
      <el-empty v-else-if="!loading" description="自动催办策略尚未加载"/>
    </el-card>
    <el-card shadow="never" style="margin-top:16px">
      <template #header>退出处置 · 逾期升级</template>
      <el-alert title="默认关闭。须先启用自动催办；达到逾期天数后，随自动催办向指定本租户有效用户发送站内升级通知。不会自动改派或关闭事项。0 表示关闭升级。" type="info" :closable="false"/>
      <el-form v-if="escalationRecipient&&escalationDays" label-position="top" style="margin-top:16px">
        <el-form-item label="升级接收人（仅本租户有效用户）">
          <el-select v-model="recipientId" filterable remote :remote-method="searchEscalationUsers" :loading="usersLoading" :disabled="saving" style="width:360px" @visible-change="recipientDropdownVisible" @change="changeRecipient">
            <el-option label="关闭升级" value="0"/>
            <el-option v-for="user in displayedUsers" :key="user.id" :label="`${user.displayName} (${user.username})`" :value="user.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="逾期满多少天升级"><el-input-number v-model="afterDays" :min="1" :max="365" :precision="0" :disabled="saving"/></el-form-item>
        <el-button type="primary" :loading="saving" @click="saveEscalation">保存升级策略</el-button>
      </el-form>
      <el-empty v-else-if="!loading" description="升级策略尚未加载"/>
    </el-card>
  </div>
</template>

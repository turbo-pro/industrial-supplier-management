<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
const session=useSessionStore();
const setting=ref<components['schemas']['TenantSetting']>();
const days=ref(7),loading=ref(false),saving=ref(false);
async function load(){
  loading.value=true;
  try{
    const {data,error}=await session.client.GET('/configuration/settings');
    if(error){ElMessage.error(error.error.message);return;}
    setting.value=data?.data?.find(item=>item.key==='restriction.watchPeriodDays');
    if(setting.value)days.value=Number(setting.value.value);
  }catch{ElMessage.error('网络异常，配置加载失败');}finally{loading.value=false;}
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
  </div>
</template>

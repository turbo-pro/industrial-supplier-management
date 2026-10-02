<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type Supplier=components['schemas']['Supplier'];
type Save=components['schemas']['SaveSupplier'];
const props=defineProps<{modelValue:boolean;supplierId?:string}>();
const emit=defineEmits<{(e:'update:modelValue',value:boolean):void;(e:'changed'):void}>();
const session=useSessionStore();
const detail=ref<Supplier|null>(null),form=ref<Save|null>(null),editing=ref(false),busy=ref(false);
const typeNames={MANUFACTURER:'生产商',TRADER:'贸易商',SERVICE_PROVIDER:'服务商',CONTRACTOR:'承包商',OTHER:'其他'};
const statusNames={DRAFT:'草稿',ACTIVE:'有效',SUSPENDED:'停用',EXITED:'已退出'};
const riskNames={LOW:'低',MEDIUM:'中',HIGH:'高'};

async function load(){if(!props.supplierId)return;const id=props.supplierId;
  try{const {data,error}=await session.client.GET('/suppliers/{id}',{params:{path:{id}}});
    if(id!==props.supplierId||!props.modelValue)return;
    if(error||!data?.data){ElMessage.error('供应商不存在或当前账号无权查看');emit('update:modelValue',false);return;}
    detail.value=data.data;
  }catch{if(id===props.supplierId&&props.modelValue){ElMessage.error('供应商读取失败，请重试');emit('update:modelValue',false);}}
}
watch([()=>props.modelValue,()=>props.supplierId],([open])=>{if(open){editing.value=false;form.value=null;void load();}else{detail.value=null;form.value=null;}},{immediate:true});
function beginEdit(){const d=detail.value;if(!d||d.status==='EXITED')return;
  form.value={code:d.code,name:d.name,shortName:d.shortName,unifiedSocialCreditCode:d.unifiedSocialCreditCode,type:d.type,industry:d.industry,countryCode:d.countryCode??'CN',province:d.province,city:d.city,address:d.address,legalRepresentative:d.legalRepresentative,registeredCapital:d.registeredCapital,currency:d.currency,establishedDate:d.establishedDate,website:d.website,riskLevel:d.riskLevel,remark:d.remark,organizationId:d.organizationId,
    contacts:(d.contacts??[]).map(c=>({name:c.name,position:c.position,mobile:c.mobile,telephone:c.telephone,email:c.email,primary:c.primary,sortOrder:c.sortOrder})),version:d.version};editing.value=true;
}
function addContact(){if(!form.value||form.value.contacts.length>=20)return;form.value.contacts.push({name:'',mobile:'',telephone:'',email:'',primary:form.value.contacts.length===0,sortOrder:form.value.contacts.length*10});}
function setPrimary(index:number,value:boolean){if(!form.value||!value)return;form.value.contacts.forEach((c,i)=>{c.primary=i===index;});}
async function save(){const d=detail.value,c=form.value;if(!d||!c)return;
  if(!c.name.trim()||!c.organizationId){ElMessage.warning('请填写名称和归属组织');return;}
  if(c.contacts.length>20||c.contacts.some(x=>!x.name.trim()||!x.mobile?.trim()&&!x.telephone?.trim()&&!x.email?.trim())||c.contacts.filter(x=>x.primary).length>1){ElMessage.warning('请检查联系人姓名、联系方式和主要联系人');return;}
  busy.value=true;try{const {data,error}=await session.client.PUT('/suppliers/{id}',{params:{path:{id:d.id}},body:{...c,name:c.name.trim(),contacts:c.contacts.map((x,i)=>({...x,name:x.name.trim(),mobile:x.mobile?.trim()||undefined,telephone:x.telephone?.trim()||undefined,email:x.email?.trim()||undefined,sortOrder:i*10}))}});
    if(error||!data?.data){ElMessage.error(error?.error.message??'保存失败');return;}detail.value=data.data;editing.value=false;ElMessage.success('供应商档案已保存');emit('changed');
  }catch{ElMessage.error('保存失败，请刷新重试');}finally{busy.value=false;}
}
async function changeStatus(status:'ACTIVE'|'SUSPENDED'){const d=detail.value;if(!d)return;let reason='档案审核通过';
  try{if(status==='SUSPENDED')reason=(await ElMessageBox.prompt('请填写停用原因','停用供应商',{inputValidator:(value:string)=>!!value.trim()||'停用原因不能为空',inputType:'textarea'})).value.trim();
    else await ElMessageBox.confirm(`确认启用供应商“${d.name}”？`,'状态确认',{type:'warning'});
  }catch{return;}
  busy.value=true;try{const {data,error}=await session.client.POST('/suppliers/{id}/status',{params:{path:{id:d.id}},body:{status,reason,version:d.version}});
    if(error||!data?.data){ElMessage.error(error?.error.message??'状态变更失败');return;}detail.value=data.data;ElMessage.success(status==='ACTIVE'?'已启用':'已停用');emit('changed');
  }catch{ElMessage.error('状态变更失败，请刷新重试');}finally{busy.value=false;}
}
</script>
<template>
  <el-drawer :model-value="modelValue" :title="`供应商档案 · ${detail?.code??''}`" size="820px" @update:model-value="emit('update:modelValue',$event)">
    <template v-if="detail">
      <template v-if="!editing">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="供应商名称">{{detail.name}}</el-descriptions-item><el-descriptions-item label="状态">{{statusNames[detail.status]}}</el-descriptions-item>
          <el-descriptions-item label="简称">{{detail.shortName??'—'}}</el-descriptions-item><el-descriptions-item label="统一社会信用代码">{{detail.unifiedSocialCreditCode??'—'}}</el-descriptions-item>
          <el-descriptions-item label="类型">{{typeNames[detail.type]}}</el-descriptions-item><el-descriptions-item label="风险">{{riskNames[detail.riskLevel]}}</el-descriptions-item>
          <el-descriptions-item label="归属组织">{{session.organizationOptions.find(x=>x.id===detail?.organizationId)?.label??detail.organizationId}}</el-descriptions-item><el-descriptions-item label="行业">{{detail.industry??'—'}}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{[detail.province,detail.city,detail.address].filter(Boolean).join(' ')||'—'}}</el-descriptions-item>
          <el-descriptions-item label="法人">{{detail.legalRepresentative??'—'}}</el-descriptions-item><el-descriptions-item label="注册资本">{{detail.registeredCapital??'—'}} {{detail.currency??''}}</el-descriptions-item>
          <el-descriptions-item label="成立日期">{{detail.establishedDate??'—'}}</el-descriptions-item><el-descriptions-item label="网站">{{detail.website??'—'}}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{detail.remark??'—'}}</el-descriptions-item>
        </el-descriptions>
        <h3>联系人</h3><el-table :data="detail.contacts??[]" size="small"><el-table-column prop="name" label="姓名" min-width="100"/><el-table-column prop="position" label="职务" min-width="100"/><el-table-column prop="mobile" label="手机" min-width="130"/><el-table-column prop="telephone" label="电话" min-width="130"/><el-table-column prop="email" label="邮箱" min-width="180"/><el-table-column label="主要" width="70"><template #default="{row}">{{row.primary?'是':'否'}}</template></el-table-column></el-table>
        <div v-if="detail.status!=='EXITED'" style="margin-top:16px"><el-button type="primary" @click="beginEdit">编辑档案</el-button><el-button v-if="detail.status==='ACTIVE'" type="warning" :loading="busy" @click="changeStatus('SUSPENDED')">停用</el-button><el-button v-if="detail.status==='DRAFT'||detail.status==='SUSPENDED'" type="success" :loading="busy" @click="changeStatus('ACTIVE')">启用</el-button></div>
      </template>
      <template v-else-if="form">
        <el-alert title="供应商编码不可修改；保存使用当前版本校验，已退出档案不可编辑。" type="info" :closable="false" style="margin-bottom:16px"/>
        <el-form label-position="top"><el-row :gutter="14"><el-col :span="12"><el-form-item label="供应商名称 *"><el-input v-model="form.name" maxlength="200"/></el-form-item></el-col><el-col :span="12"><el-form-item label="简称"><el-input v-model="form.shortName" maxlength="100"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="统一社会信用代码"><el-input v-model="form.unifiedSocialCreditCode" maxlength="18"/></el-form-item></el-col><el-col :span="12"><el-form-item label="归属组织 *"><el-select v-model="form.organizationId" style="width:100%"><el-option v-for="item in session.organizationOptions" :key="item.id" :label="item.label" :value="item.id"/></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="类型"><el-select v-model="form.type"><el-option v-for="(name,key) in typeNames" :key="key" :label="name" :value="key"/></el-select></el-form-item></el-col><el-col :span="8"><el-form-item label="风险等级"><el-select v-model="form.riskLevel"><el-option v-for="(name,key) in riskNames" :key="key" :label="name" :value="key"/></el-select></el-form-item></el-col><el-col :span="8"><el-form-item label="行业"><el-input v-model="form.industry" maxlength="100"/></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="省"><el-input v-model="form.province" maxlength="100"/></el-form-item></el-col><el-col :span="8"><el-form-item label="市"><el-input v-model="form.city" maxlength="100"/></el-form-item></el-col><el-col :span="8"><el-form-item label="地址"><el-input v-model="form.address" maxlength="300"/></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="法人"><el-input v-model="form.legalRepresentative" maxlength="100"/></el-form-item></el-col><el-col :span="8"><el-form-item label="注册资本"><el-input-number v-model="form.registeredCapital" :min="0" :precision="2" style="width:100%"/></el-form-item></el-col><el-col :span="8"><el-form-item label="币种"><el-input v-model="form.currency" maxlength="3"/></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="成立日期"><el-date-picker v-model="form.establishedDate" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col><el-col :span="8"><el-form-item label="网站"><el-input v-model="form.website" maxlength="300"/></el-form-item></el-col><el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="1000" show-word-limit/></el-form-item></el-col>
        </el-row></el-form>
        <h3>联系人 <el-button link type="primary" :disabled="form.contacts.length>=20" @click="addContact">添加联系人</el-button></h3>
        <el-table :data="form.contacts" size="small"><el-table-column label="姓名 *" min-width="110"><template #default="{row}"><el-input v-model="row.name" maxlength="100"/></template></el-table-column><el-table-column label="职务" min-width="110"><template #default="{row}"><el-input v-model="row.position" maxlength="100"/></template></el-table-column><el-table-column label="手机" min-width="135"><template #default="{row}"><el-input v-model="row.mobile" maxlength="32"/></template></el-table-column><el-table-column label="电话" min-width="135"><template #default="{row}"><el-input v-model="row.telephone" maxlength="32"/></template></el-table-column><el-table-column label="邮箱" min-width="180"><template #default="{row}"><el-input v-model="row.email" maxlength="200"/></template></el-table-column><el-table-column label="主要" width="75"><template #default="{row,$index}"><el-switch v-model="row.primary" @change="setPrimary($index,Boolean($event))"/></template></el-table-column><el-table-column label="操作" width="70"><template #default="{$index}"><el-button link type="danger" @click="form!.contacts.splice($index,1)">删除</el-button></template></el-table-column></el-table>
        <div style="margin-top:18px;text-align:right"><el-button @click="editing=false">取消</el-button><el-button type="primary" :loading="busy" @click="save">保存档案</el-button></div>
      </template>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type Category=components['schemas']['PurchaseCategory'];
const props=defineProps<{modelValue:boolean}>();
const emit=defineEmits<{(e:'update:modelValue',value:boolean):void;(e:'changed'):void}>();
const session=useSessionStore();
const rows=ref<Category[]>([]),busy=ref(false),code=ref(''),name=ref('');
type RequiredMaterial=components['schemas']['PurchaseCategoryRequiredMaterial'];
const policyOpen=ref(false),policyBusy=ref(false),policyCategory=ref<Category|null>(null),policyVersion=ref(0),policyMaterials=ref<RequiredMaterial[]>([]);
async function openPolicy(row:Category){const {data,error}=await session.client.GET('/supplier-purchase-categories/{id}/required-materials',{params:{path:{id:row.id}}});if(error||!data?.data){ElMessage.error('读取必交资料规则失败');return;}policyCategory.value=row;policyVersion.value=data.data.version;policyMaterials.value=data.data.materials.map(m=>({...m}));policyOpen.value=true;}
async function savePolicy(){const row=policyCategory.value;if(!row)return;const materials=policyMaterials.value.map(m=>({type:m.type.trim(),name:m.name.trim()}));if(materials.length>20||materials.some(m=>!/^[A-Z][A-Z0-9_]{1,63}$/.test(m.type)||!m.name||m.name.length>100)||new Set(materials.map(m=>m.type)).size!==materials.length){ElMessage.warning('请检查资料类型、名称及重复项（最多 20 项）');return;}policyBusy.value=true;try{const {data,error}=await session.client.PUT('/supplier-purchase-categories/{id}/required-materials',{params:{path:{id:row.id}},body:{materials,version:policyVersion.value}});if(error||!data?.data){ElMessage.error(error?.error.message??'保存失败');return;}policyVersion.value=data.data.version;policyMaterials.value=data.data.materials.map(m=>({...m}));await load();emit('changed');ElMessage.success('品类必交资料已保存');}finally{policyBusy.value=false;}}
async function load(){const {data,error}=await session.client.GET('/supplier-purchase-categories');if(error){ElMessage.error(error.error.message);return;}rows.value=data?.data??[];}
watch(()=>props.modelValue,open=>{if(open)void load();});
async function create(){if(!/^[A-Z][A-Z0-9_]{1,63}$/.test(code.value)||!name.value.trim()){ElMessage.warning('请填写大写编码和名称');return;}busy.value=true;try{const {error}=await session.client.POST('/supplier-purchase-categories',{body:{code:code.value,name:name.value.trim(),status:'ACTIVE',version:0}});if(error){ElMessage.error(error.error.message);return;}code.value='';name.value='';await load();emit('changed');}finally{busy.value=false;}}
async function save(row:Category){busy.value=true;try{const {error}=await session.client.PUT('/supplier-purchase-categories/{id}',{params:{path:{id:row.id}},body:{code:row.code,name:row.name.trim(),status:row.status,version:row.version}});if(error){ElMessage.error(error.error.message);await load();return;}await load();emit('changed');}finally{busy.value=false;}}
</script>
<template>
  <el-dialog :model-value="modelValue" title="租户采购品类" width="680px" @update:model-value="emit('update:modelValue',$event)">
    <el-alert title="品类编码创建后不可修改；停用后不可新增关联，已有供应商关联保留。" type="info" :closable="false"/>
    <el-form inline style="margin-top:16px"><el-form-item label="编码"><el-input v-model="code" maxlength="64" placeholder="如 CHEMICAL_RAW"/></el-form-item><el-form-item label="名称"><el-input v-model="name" maxlength="100" placeholder="如化工原料"/></el-form-item><el-form-item><el-button type="primary" :loading="busy" @click="create">新增</el-button></el-form-item></el-form>
    <el-table :data="rows" size="small"><el-table-column prop="code" label="编码" width="170"/><el-table-column label="名称" min-width="180"><template #default="{row}"><el-input v-model="row.name" maxlength="100"/></template></el-table-column><el-table-column label="状态" width="115"><template #default="{row}"><el-switch v-model="row.status" active-value="ACTIVE" inactive-value="INACTIVE" active-text="启用"/></template></el-table-column><el-table-column label="操作" width="160"><template #default="{row}"><el-button link type="primary" :disabled="busy" @click="save(row)">保存</el-button><el-button link @click="openPolicy(row)">必交资料</el-button></template></el-table-column></el-table>
  </el-dialog>
  <el-dialog v-model="policyOpen" append-to-body :title="`${policyCategory?.name??''} · 准入必交资料`" width="680px">
    <el-alert title="规则修改后，未提交和待审批申请会按最新必交项复核；已经上传的资料不会自动删除。" type="warning" :closable="false"/>
    <el-table :data="policyMaterials" size="small" style="margin-top:12px"><el-table-column label="资料类型编码" width="210"><template #default="{row}"><el-input v-model="row.type" maxlength="64" placeholder="如 SAFETY_LICENSE"/></template></el-table-column><el-table-column label="资料名称"><template #default="{row}"><el-input v-model="row.name" maxlength="100"/></template></el-table-column><el-table-column label="操作" width="70"><template #default="{$index}"><el-button link type="danger" @click="policyMaterials.splice($index,1)">移除</el-button></template></el-table-column></el-table>
    <el-button style="margin-top:12px" :disabled="policyMaterials.length>=20" @click="policyMaterials.push({type:'',name:''})">新增必交项</el-button>
    <template #footer><el-button @click="policyOpen=false">关闭</el-button><el-button type="primary" :loading="policyBusy" @click="savePolicy">保存规则</el-button></template>
  </el-dialog>
</template>

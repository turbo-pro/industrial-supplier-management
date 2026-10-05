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
async function load(){const {data,error}=await session.client.GET('/supplier-purchase-categories');if(error){ElMessage.error(error.error.message);return;}rows.value=data?.data??[];}
watch(()=>props.modelValue,open=>{if(open)void load();});
async function create(){if(!/^[A-Z][A-Z0-9_]{1,63}$/.test(code.value)||!name.value.trim()){ElMessage.warning('请填写大写编码和名称');return;}busy.value=true;try{const {error}=await session.client.POST('/supplier-purchase-categories',{body:{code:code.value,name:name.value.trim(),status:'ACTIVE',version:0}});if(error){ElMessage.error(error.error.message);return;}code.value='';name.value='';await load();emit('changed');}finally{busy.value=false;}}
async function save(row:Category){busy.value=true;try{const {error}=await session.client.PUT('/supplier-purchase-categories/{id}',{params:{path:{id:row.id}},body:{code:row.code,name:row.name.trim(),status:row.status,version:row.version}});if(error){ElMessage.error(error.error.message);await load();return;}await load();emit('changed');}finally{busy.value=false;}}
</script>
<template>
  <el-dialog :model-value="modelValue" title="租户采购品类" width="680px" @update:model-value="emit('update:modelValue',$event)">
    <el-alert title="品类编码创建后不可修改；停用后不可新增关联，已有供应商关联保留。" type="info" :closable="false"/>
    <el-form inline style="margin-top:16px"><el-form-item label="编码"><el-input v-model="code" maxlength="64" placeholder="如 CHEMICAL_RAW"/></el-form-item><el-form-item label="名称"><el-input v-model="name" maxlength="100" placeholder="如化工原料"/></el-form-item><el-form-item><el-button type="primary" :loading="busy" @click="create">新增</el-button></el-form-item></el-form>
    <el-table :data="rows" size="small"><el-table-column prop="code" label="编码" width="170"/><el-table-column label="名称" min-width="180"><template #default="{row}"><el-input v-model="row.name" maxlength="100"/></template></el-table-column><el-table-column label="状态" width="115"><template #default="{row}"><el-switch v-model="row.status" active-value="ACTIVE" inactive-value="INACTIVE" active-text="启用"/></template></el-table-column><el-table-column label="操作" width="85"><template #default="{row}"><el-button link type="primary" :disabled="busy" @click="save(row)">保存</el-button></template></el-table-column></el-table>
  </el-dialog>
</template>

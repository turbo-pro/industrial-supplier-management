<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
type Column=components['schemas']['PersonalTableColumn'];
type View=components['schemas']['PersonalTableView'];
const props=defineProps<{tableKey:components['schemas']['PersonalTableKey'];defaults:components['schemas']['PersonalTableColumnDefinition'][]}>();
const emit=defineEmits<{change:[columns:Column[]]}>();
const session=useSessionStore();const views=ref<View[]>([]);const catalog=ref(props.defaults);
const selected=ref('');const available=ref(false);const busy=ref(false);const open=ref(false);
const name=ref('');const defaultView=ref(false);const draft=ref<Column[]>([]);
const current=computed(()=>views.value.find(v=>v.id===selected.value));
function system(){return catalog.value.map(c=>({key:c.key,visible:true,width:c.width}));}
function choose(){emit('change',(current.value?.columns??system()).map(c=>({...c})));}
async function load(initial=false){
  const {data,error}=await session.client.GET('/table-views/{tableKey}',{params:{path:{tableKey:props.tableKey}}});
  if(error||!data?.data){available.value=false;return false;}
  available.value=true;views.value=data.data.views;catalog.value=data.data.catalog;
  if(initial)selected.value=views.value.find(v=>v.defaultView)?.id??'';
  if(selected.value&&!current.value)selected.value='';
  choose();return true;
}
function edit(){draft.value=(current.value?.columns??system()).map(c=>({...c}));name.value=current.value?.name??'';defaultView.value=current.value?.defaultView??false;open.value=true;}
function move(index:number,direction:number){const target=index+direction;if(target<0||target>=draft.value.length)return;const list=[...draft.value];[list[index],list[target]]=[list[target]!,list[index]!];draft.value=list;}
async function save(asNew:boolean){
  if(!name.value.trim()){ElMessage.warning('请填写方案名称');return;}
  busy.value=true;
  try{
    const body={name:name.value.trim(),columns:draft.value,defaultView:defaultView.value,version:asNew?0:current.value?.version??0};
    const response=asNew||!current.value
      ?await session.client.POST('/table-views/{tableKey}',{params:{path:{tableKey:props.tableKey}},body})
      :await session.client.PUT('/table-views/{tableKey}/{id}',{params:{path:{tableKey:props.tableKey,id:current.value.id}},body});
    if(response.error){ElMessage.error(`${response.error.error.message}；如有版本冲突，请关闭后刷新方案。`);return;}
    selected.value=response.data?.data?.id??'';
    if(!await load()){ElMessage.warning('方案已保存，但重新加载失败，请刷新页面');open.value=false;return;}
    open.value=false;ElMessage.success('个人列方案已保存');
  }catch{ElMessage.error('保存失败，请刷新核对结果');}finally{busy.value=false;}
}
async function remove(){
  if(!current.value)return;
  try{
    await ElMessageBox.confirm('删除这套个人列方案？其他人的配置不受影响。','删除列方案');busy.value=true;
    const {error}=await session.client.DELETE('/table-views/{tableKey}/{id}',{params:{path:{tableKey:props.tableKey,id:current.value.id},query:{version:current.value.version}}});
    if(error){ElMessage.error(error.error.message);return;}
    selected.value='';choose();await load();open.value=false;ElMessage.success('个人列方案已删除');
  }catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error('删除失败，请刷新核对结果');}finally{busy.value=false;}
}
async function refresh(){busy.value=true;try{if(!await load())ElMessage.warning('暂无列方案权限或加载失败，仍可使用系统列');}catch{ElMessage.error('列方案加载失败');}finally{busy.value=false;}}
onMounted(async()=>{emit('change',system());busy.value=true;try{await load(true);}catch{available.value=false;}finally{busy.value=false;}});
</script>
<template>
  <div style="display:flex;gap:8px;align-items:center;margin-bottom:12px;flex-wrap:wrap">
    <span>个人列方案</span>
    <el-select v-model="selected" style="width:210px" :disabled="busy||!available" @change="choose"><el-option label="系统列（本次）" value=""/><el-option v-for="view in views" :key="view.id" :label="view.name+(view.defaultView?' · 默认':'')" :value="view.id"/></el-select>
    <el-button :disabled="busy||!available" @click="edit">配置列</el-button><el-button :disabled="busy" @click="refresh">刷新方案</el-button>
    <span v-if="!available&&!busy" style="color:var(--el-text-color-secondary)">列方案未授权或不可用，使用系统列</span>
  </div>
  <el-dialog v-model="open" title="个人表格列方案" width="700px" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <el-alert title="只调整当前账号的显示列，不改变数据权限或导出范围。编码、名称与业务操作入口保留。" type="info" :closable="false"/>
    <el-form label-position="top" style="margin-top:12px"><el-form-item label="方案名称"><el-input v-model="name" maxlength="60" :disabled="busy"/></el-form-item><el-checkbox v-model="defaultView" :disabled="busy">下次进入此表时默认使用</el-checkbox></el-form>
    <el-table :data="draft">
      <el-table-column label="列名"><template #default="{row}">{{catalog.find(c=>c.key===row.key)?.label}}</template></el-table-column>
      <el-table-column label="显示" width="80"><template #default="{row}"><el-checkbox v-model="row.visible" :disabled="busy||catalog.find(c=>c.key===row.key)?.required"/></template></el-table-column>
      <el-table-column label="宽度（像素）" width="200"><template #default="{row}"><el-input-number v-model="row.width" :min="80" :max="600" :step="10" :disabled="busy"/></template></el-table-column>
      <el-table-column label="顺序" width="150"><template #default="{$index}"><el-button link :disabled="busy||$index===0" @click="move($index,-1)">上移</el-button><el-button link :disabled="busy||$index===draft.length-1" @click="move($index,1)">下移</el-button></template></el-table-column>
    </el-table>
    <template #footer><el-button :disabled="busy" @click="open=false">取消</el-button><el-button v-if="current" type="danger" :disabled="busy" @click="remove">删除方案</el-button><el-button v-if="current" :loading="busy" @click="save(false)">更新当前方案</el-button><el-button type="primary" :loading="busy" @click="save(true)">{{current?'另存为新方案':'保存新方案'}}</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type Item=components['schemas']['ExitMonitorItem'];
const session=useSessionStore(),rows=ref<Item[]>([]),total=ref(0),page=ref(0),loading=ref(false);
const keyword=ref(''),code=ref<'OPEN_CONTRACT'|'OPEN_PROJECT'|'OPEN_PERSON'|'OPEN_ASSET'|'OPEN_SAFETY'|'OPEN_ATTENDANCE'|'OPEN_QUALITY'|'OPEN_IMPROVEMENT'>();
const unassignedOnly=ref(false),overdueOnly=ref(false);
const labels={OPEN_CONTRACT:'合同',OPEN_PROJECT:'项目',OPEN_PERSON:'人员',OPEN_ASSET:'车辆设备',OPEN_SAFETY:'安全隐患',OPEN_ATTENDANCE:'现场签到',OPEN_QUALITY:'质量不符合项',OPEN_IMPROVEMENT:'绩效改进'};
let sequence=0;
async function load(){const request=++sequence;loading.value=true;rows.value=[];
  try{const {data,error}=await session.client.GET('/supplier-exit-tasks/monitor',{params:{query:{keyword:keyword.value.trim()||undefined,code:code.value,unassignedOnly:unassignedOnly.value,overdueOnly:overdueOnly.value,page:page.value,size:20}}});
    if(request!==sequence)return;
    if(error||!data?.data){total.value=0;ElMessage.error(error?.error.message??'退出处置监控读取失败');return;}
    rows.value=data.data.items;total.value=data.data.total;
  }catch{if(request===sequence){total.value=0;ElMessage.error('退出处置监控读取失败，请刷新重试');}}finally{if(request===sequence)loading.value=false;}}
function search(){page.value=0;void load();}
watch(()=>session.currentOrganization?.id,search,{immediate:true});
onBeforeUnmount(()=>{sequence++;});
function label(value:string){return labels[value as keyof typeof labels]??value;}
</script>
<template>
  <div class="page"><div class="page-heading"><div><h1>退出处置监控</h1><p>当前提交中申请的未结实体，按授权供应商范围集中查看</p></div><el-button :disabled="loading" @click="load">刷新</el-button></div>
    <el-alert title="这是实时状态筛选的处置快照，不是手工完成入口；逾期不会自动通过审批，外部门禁和账号回收不在本页宣称成功。" type="warning" :closable="false"/>
    <el-card shadow="never" class="filter-card"><el-form inline @submit.prevent="search"><el-form-item label="供应商"><el-input v-model="keyword" maxlength="100" clearable placeholder="编码或名称" @keyup.enter="search"/></el-form-item><el-form-item label="事项类型"><el-select v-model="code" clearable placeholder="全部类型" style="width:180px"><el-option v-for="(name,key) in labels" :key="key" :label="name" :value="key"/></el-select></el-form-item><el-form-item><el-checkbox v-model="unassignedOnly">仅未分派</el-checkbox><el-checkbox v-model="overdueOnly">仅逾期</el-checkbox></el-form-item><el-form-item><el-button type="primary" :disabled="loading" @click="search">查询</el-button><el-button :disabled="loading" @click="keyword='';code=undefined;unassignedOnly=false;overdueOnly=false;search()">重置</el-button></el-form-item></el-form></el-card>
    <el-card shadow="never" class="table-card"><el-table v-loading="loading" :data="rows" row-key="id" stripe>
      <el-table-column label="供应商" min-width="200"><template #default="{row}"><strong>{{row.supplierName}}</strong><div class="subtext">{{row.supplierCode}}</div></template></el-table-column>
      <el-table-column label="事项类型" width="130"><template #default="{row}">{{label(row.code)}}</template></el-table-column>
      <el-table-column prop="sourceId" label="业务记录 ID" width="170"/>
      <el-table-column label="责任人 ID" width="150"><template #default="{row}"><el-tag v-if="!row.assigneeId" type="warning">未分派</el-tag><span v-else>{{row.assigneeId}}</span></template></el-table-column>
      <el-table-column label="处置期限" width="150"><template #default="{row}">{{row.dueDate??'未设置'}}<el-tag v-if="row.overdue" type="danger">已逾期</el-tag></template></el-table-column>
      <el-table-column prop="note" label="处理说明" min-width="190" show-overflow-tooltip/>
      <el-table-column prop="lastRemindedAt" label="上次催办（UTC）" width="190"/>
      <el-table-column prop="checkedAt" label="最后核验" width="190"/>
      <el-table-column label="入口" width="150" fixed="right"><template #default="{row}"><router-link :to="row.route">业务台账</router-link><router-link :to="{path:'/suppliers/master',query:{exitSupplierId:row.supplierId}}" style="margin-left:10px">退出申请</router-link></template></el-table-column>
    </el-table><div class="pagination"><span>{{loading?'查询中':`共 ${total} 条`}}</span><el-pagination :disabled="loading" :current-page="page+1" :total="total" :page-size="20" layout="prev, pager, next" @current-change="(value:number)=>{if(!loading&&value!==page+1){page=value-1;load()}}"/></div></el-card>
  </div>
</template>

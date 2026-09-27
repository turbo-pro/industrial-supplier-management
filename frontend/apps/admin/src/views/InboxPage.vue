<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
const session=useSessionStore(),rows=ref<components['schemas']['InboxMessage'][]>([]),unread=ref<number>(),loading=ref(false),busy=ref(false);
let sequence=0;
async function load(){const request=++sequence;loading.value=true;unread.value=undefined;rows.value=[];
  try{const [inbox,count]=await Promise.all([session.client.GET('/messages/inbox'),session.client.GET('/messages/inbox/unread-count')]);
    if(request!==sequence)return;
    if(inbox.error||count.error){ElMessage.error(inbox.error?.error.message??count.error?.error.message??'消息读取失败');return;}
    rows.value=inbox.data?.data??[];unread.value=count.data?.data?.count;
  }catch{if(request===sequence)ElMessage.error('消息读取失败，请重试');}finally{if(request===sequence)loading.value=false;}}
async function markRead(id?:string){
  try{if(!id)await ElMessageBox.confirm('将当前账号的全部未读消息标记为已读？这不会完成任何业务事项。','全部已读',{confirmButtonText:'确定',cancelButtonText:'取消'});
    busy.value=true;
    const result=id?await session.client.PUT('/messages/inbox/{id}/read',{params:{path:{id}}}):await session.client.PUT('/messages/inbox/read-all');
    if(result.error){ElMessage.error(result.error.error.message);return;}
    await load();
  }catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error('更新失败，请刷新核对');}finally{busy.value=false;}}
onMounted(load);onBeforeUnmount(()=>{sequence++;});
</script>
<template>
  <el-card><template #header><div style="display:flex;justify-content:space-between;align-items:center"><h2>我的消息</h2><div><el-button :disabled="busy||loading" @click="load">刷新</el-button><el-button :disabled="busy||loading||!unread" @click="markRead()">全部已读</el-button></div></div></template>
    <el-alert title="消息仅提示业务责任，不是完成或放行凭证。重新分派、申请撤回或业务结清后，旧消息仍保留；以业务页面当前状态为准。" type="info" :closable="false"/>
    <p>未读：{{unread??'未取得'}}。展示最近 200 条消息；未读总数包含更早消息。</p>
    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.readAt?'info':'warning'">{{row.readAt?'已读':'未读'}}</el-tag></template></el-table-column>
      <el-table-column prop="title" label="标题" min-width="180"/>
      <el-table-column prop="content" label="内容" min-width="400"/>
      <el-table-column prop="createdAt" label="发送时间" width="200"/>
      <el-table-column label="操作" width="100"><template #default="{row}"><el-button v-if="!row.readAt" link :disabled="busy||loading" @click="markRead(row.id)">标记已读</el-button></template></el-table-column>
    </el-table>
  </el-card>
</template>

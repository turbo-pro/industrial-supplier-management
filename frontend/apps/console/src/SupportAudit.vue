<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue';
import { createIsmClient, type operations } from '@ism/api-client';
type Sessions = operations['auditSupportSessions']['responses'][200]['content']['application/json']['data'];
type Logs = operations['auditSupportAccessLogs']['responses'][200]['content']['application/json']['data'];
const props=defineProps<{token:string}>();
const client=createIsmClient({getAccessToken:()=>props.token});
const sessions=ref<Sessions>(), logs=ref<Logs>(), selected=ref('');
const page=ref(0), logPage=ref(0), busy=ref(false), error=ref('');
const states={ACTIVE:'已兑换 · 读取时复核',PENDING:'待兑换',EXPIRED:'已到期',REVOKED:'已撤销'};
const results={READ:'读取成功',NOT_VISIBLE:'不可见或不存在',DENIED:'已拒绝'};
let generation=0;
function reset(){generation++;sessions.value=undefined;logs.value=undefined;selected.value='';busy.value=false;error.value='';}
watch(()=>props.token,reset);onBeforeUnmount(reset);
async function load(next=0){
 if(busy.value)return;const started=generation;busy.value=true;error.value='';logs.value=undefined;selected.value='';
 try{const r=await client.GET('/console/support-audit/sessions',{params:{query:{page:next}}});
  if(started!==generation)return;if(!r.data)throw Error();sessions.value=r.data.data;page.value=next;
 }catch{if(started===generation){sessions.value=undefined;error.value='会话审计读取失败，请重试。';}}
 finally{if(started===generation)busy.value=false;}
}
async function readLogs(id:string,next=0){
 if(busy.value)return;const started=generation;busy.value=true;error.value='';logs.value=undefined;selected.value=id;
 try{const r=await client.GET('/console/support-audit/sessions/{sessionId}/access-logs',{params:{path:{sessionId:id},query:{page:next}}});
  if(started!==generation)return;if(!r.data)throw Error();logs.value=r.data.data;logPage.value=next;
 }catch{if(started===generation)error.value='访问记录读取失败，请重试。';}
 finally{if(started===generation)busy.value=false;}
}
</script>
<template>
 <section class="panel" data-console-section="support-audit" aria-label="独立支持审计">
  <h2>支持会话审计</h2><p>查看会话控制信息和可确认归属的访问记录。审计身份不授予业务操作权限。</p>
  <button type="button" :disabled="busy" @click="load()">读取会话审计</button>
  <p v-if="busy" role="status">正在读取…</p><p v-if="error" role="alert">{{error}}</p>
  <p v-if="sessions && !sessions.items.length">暂无支持会话。</p>
  <div v-if="sessions?.items.length" class="table-scroll" tabindex="0" aria-label="支持会话表格">
   <table><thead><tr><th>会话 ID</th><th>申请 ID</th><th>平台操作者 ID</th><th>目标租户 ID</th><th>状态</th><th>截止（UTC）</th><th>操作</th></tr></thead>
    <tbody><tr v-for="row in sessions.items" :key="row.id"><td>{{row.id}}</td><td>{{row.requestId}}</td><td>{{row.operatorId}}</td><td>{{row.tenantId}}</td><td>{{states[row.state]}}</td><td>{{row.expiresAt}}</td><td><button type="button" :disabled="busy" @click="readLogs(row.id)">查看访问记录</button></td></tr></tbody>
   </table>
  </div>
  <button v-if="page>0 && sessions" type="button" :disabled="busy" @click="load(page-1)">上一页会话</button>
  <button v-if="sessions?.hasMore" type="button" :disabled="busy" @click="load(page+1)">下一页会话</button>
  <section v-if="selected" aria-label="审计访问记录"><h3>会话 {{selected}} 的访问记录</h3>
   <p v-if="logs && !logs.items.length">本页暂无访问记录。</p>
   <div v-if="logs?.items.length" class="table-scroll" tabindex="0" aria-label="访问记录表格"><table><thead><tr><th>发生时间（UTC）</th><th>对象 ID</th><th>入口</th><th>结果</th><th>追踪号</th></tr></thead><tbody><tr v-for="row in logs.items" :key="row.id"><td>{{row.occurredAt}}</td><td>{{row.objectId}}</td><td>{{row.surface}}</td><td>{{results[row.result]}}</td><td>{{row.traceId || '—'}}</td></tr></tbody></table></div>
   <button type="button" :disabled="busy" @click="readLogs(selected,logPage)">重新读取访问记录</button>
   <button v-if="logs && logPage>0" type="button" :disabled="busy" @click="readLogs(selected,logPage-1)">上一页记录</button>
   <button v-if="logs?.hasMore" type="button" :disabled="busy" @click="readLogs(selected,logPage+1)">下一页记录</button>
  </section>
 </section>
</template>
<style scoped>
.table-scroll{overflow-x:auto;margin-block:1rem;}table{width:100%;border-collapse:collapse;}th,td{text-align:left;padding:.75rem;border-bottom:1px solid var(--el-border-color);white-space:nowrap;}button{min-height:44px;margin-right:.5rem;}
</style>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue';
import { createIsmClient, type operations } from '@ism/api-client';
type Sessions = operations['auditSupportSessions']['responses'][200]['content']['application/json']['data'];
type Lifecycle = operations['auditSupportLifecycle']['responses'][200]['content']['application/json']['data'];
type Logs = operations['auditSupportAccessLogs']['responses'][200]['content']['application/json']['data'];
type Detail = operations['auditSupportSessionDetail']['responses'][200]['content']['application/json']['data'];
const detail=ref<Detail>(), detailId=ref('');
const props=defineProps<{token:string}>();
const client=createIsmClient({getAccessToken:()=>props.token});
const sessions=ref<Sessions>(), logs=ref<Logs>(), selected=ref('');
const lifecycle=ref<Lifecycle>(), lifecycleId=ref(''), lifecyclePage=ref(0);
const actions={SUPPORT_SESSION_RESERVE:'创建会话',SUPPORT_SESSION_ACTIVATE:'启用会话',SUPPORT_SESSION_REVOKE:'撤销会话',SUPPORT_SESSION_TERMINATE:'管理员强制终止'};
const page=ref(0), logPage=ref(0), busy=ref(false), error=ref('');
const states={ACTIVE:'已兑换 · 读取时复核',PENDING:'待兑换',EXPIRED:'已到期',REVOKED:'已撤销'};
const results={READ:'读取成功',NOT_VISIBLE:'不可见或不存在',DENIED:'已拒绝'};
let generation=0;
function reset(){generation++;detail.value=undefined;detailId.value='';lifecycle.value=undefined;lifecycleId.value='';sessions.value=undefined;logs.value=undefined;selected.value='';busy.value=false;error.value='';}
watch(()=>props.token,reset);onBeforeUnmount(reset);
async function load(next=0){
 if(busy.value)return;const started=generation;busy.value=true;error.value='';detail.value=undefined;detailId.value='';lifecycle.value=undefined;lifecycleId.value='';logs.value=undefined;selected.value='';
 try{const r=await client.GET('/console/support-audit/sessions',{params:{query:{page:next}}});
  if(started!==generation)return;if(!r.data)throw Error();sessions.value=r.data.data;page.value=next;
 }catch{if(started===generation){sessions.value=undefined;error.value='会话审计读取失败，请重试。';}}
 finally{if(started===generation)busy.value=false;}
}
async function readLogs(id:string,next=0){
 if(busy.value)return;const started=generation;busy.value=true;error.value='';detail.value=undefined;detailId.value='';lifecycle.value=undefined;lifecycleId.value='';logs.value=undefined;selected.value=id;
 try{const r=await client.GET('/console/support-audit/sessions/{sessionId}/access-logs',{params:{path:{sessionId:id},query:{page:next}}});
  if(started!==generation)return;if(!r.data)throw Error();logs.value=r.data.data;logPage.value=next;
 }catch{if(started===generation)error.value='访问记录读取失败，请重试。';}
 finally{if(started===generation)busy.value=false;}
}
async function readLifecycle(id:string,next=0){
 if(busy.value)return;const started=generation;busy.value=true;error.value='';detail.value=undefined;detailId.value='';lifecycle.value=undefined;lifecycleId.value=id;lifecyclePage.value=next;logs.value=undefined;selected.value='';
 try{const r=await client.GET('/console/support-audit/sessions/{sessionId}/lifecycle',{params:{path:{sessionId:id},query:{page:next}}});
  if(started!==generation)return;if(!r.data)throw Error();lifecycle.value=r.data.data;lifecyclePage.value=next;
 }catch{if(started===generation)error.value='生命周期读取失败，请重试。';}
 finally{if(started===generation)busy.value=false;}
}
async function readDetail(id:string){
 if(busy.value)return;const started=generation;busy.value=true;error.value='';detail.value=undefined;detailId.value=id;logs.value=undefined;selected.value='';lifecycle.value=undefined;lifecycleId.value='';
 try{const r=await client.GET('/console/support-audit/sessions/{sessionId}',{params:{path:{sessionId:id}}});
  if(started!==generation)return;if(!r.data)throw Error();detail.value=r.data.data;
 }catch{if(started===generation)error.value='会话详情读取失败，请核对权限后重试。';}
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
    <tbody><tr v-for="row in sessions.items" :key="row.id"><td>{{row.id}}</td><td>{{row.requestId}}</td><td>{{row.operatorId}}</td><td>{{row.tenantId}}</td><td>{{states[row.state]}}</td><td>{{row.expiresAt}}</td><td><button type="button" :disabled="busy" @click="readLogs(row.id)">查看访问记录</button><button type="button" :disabled="busy" @click="readDetail(row.id)">查看详情</button><button type="button" :disabled="busy" @click="readLifecycle(row.id)">查看生命周期</button></td></tr></tbody>
   </table>
  </div>
  <button v-if="page>0 && sessions" type="button" :disabled="busy" @click="load(page-1)">上一页会话</button>
  <button v-if="sessions?.hasMore" type="button" :disabled="busy" @click="load(page+1)">下一页会话</button>
  <section v-if="detailId" aria-label="支持会话详情"><h3>会话 {{detailId}} 的详情</h3>
   <p>以下为创建时批准的权限上界，不代表当前有效访问权限。</p>
   <dl v-if="detail"><dt>申请 / 租户 / 操作者</dt><dd>{{detail.requestId}} / {{detail.tenantId}} / {{detail.operatorId}}</dd><dt>租户决定 / 角色</dt><dd>{{detail.decisionId}} / {{detail.roleId}}</dd><dt>记录状态 / 当前状态</dt><dd>{{detail.persistedState}} / {{detail.state}}</dd><dt>创建 / 截止 / 撤销时间（UTC）</dt><dd>{{detail.createdAt}} / {{detail.expiresAt}} / {{detail.revokedAt || '—'}}</dd><dt>冻结组织范围</dt><dd>{{detail.frozenScope.tenantAll ? '全租户' : detail.frozenScope.organizations.join('、') || '无'}}</dd><dt>冻结字段范围</dt><dd>{{detail.frozenScope.fields.join('、') || '无'}}</dd></dl>
   <button type="button" :disabled="busy" @click="readDetail(detailId)">重新读取详情</button>
  </section>
  <section v-if="lifecycleId" aria-label="会话生命周期"><h3>会话 {{lifecycleId}} 的生命周期</h3>
   <p>显示已记录的操作；到期或租户撤回可能使访问失效，不会虚构为人工撤销事件。</p>
   <p v-if="lifecycle && !lifecycle.items.length">本页暂无生命周期记录。</p>
   <div v-if="lifecycle?.items.length" class="table-scroll" tabindex="0" aria-label="生命周期表格"><table><thead><tr><th>发生时间（UTC）</th><th>事件</th><th>实际操作者 ID</th><th>追踪号</th></tr></thead><tbody><tr v-for="row in lifecycle.items" :key="row.id"><td>{{row.occurredAt}}</td><td>{{actions[row.action]}}</td><td>{{row.operatorId}}</td><td>{{row.traceId}}</td></tr></tbody></table></div>
   <button type="button" :disabled="busy" @click="readLifecycle(lifecycleId,lifecyclePage)">重新读取生命周期</button>
   <button v-if="lifecycle && lifecyclePage>0" type="button" :disabled="busy" @click="readLifecycle(lifecycleId,lifecyclePage-1)">上一页事件</button>
   <button v-if="lifecycle?.hasMore" type="button" :disabled="busy" @click="readLifecycle(lifecycleId,lifecyclePage+1)">下一页事件</button>
  </section>
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

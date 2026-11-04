<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,onMounted,reactive,ref,watch} from 'vue';
import type {components} from '@ism/api-client';
import {useSessionStore} from '../stores/session';
const session=useSessionStore();
type Policy=components['schemas']['TenantSupportPolicy'];type Request=components['schemas']['TenantSupportRequest'];
type Decision=components['schemas']['TenantSupportDecision'];type Save=components['schemas']['SaveTenantSupportPolicy'];
const policy=ref<Policy>(),roles=ref<components['schemas']['TenantSupportRole'][]>([]),rows=ref<Request[]>([]),selected=ref<Request>();
const form=reactive<Save>({enabled:false,roleId:null,fields:['code','name'],maxMinutes:30,version:0});
const labels:Record<string,string>={PLATFORM_APPROVED:'平台已批准',CANCELLED:'已取消',EXPIRED:'已过期',APPROVED:'租户已批准',REJECTED:'已拒绝',REVOKED:'已撤回'};
const fields=[{id:'id',name:'ID'},{id:'code',name:'编码'},{id:'name',name:'名称'},{id:'type',name:'类型'},{id:'status',name:'状态'}] as const;
const loading=ref(false),busy=ref(false),unknown=ref(false),error=ref(''),notice=ref(''),comment=ref(''),page=ref(0),total=ref(0),box=ref<HTMLElement>();
const roleChoices=computed(()=>policy.value?.role&&!roles.value.some(r=>r.id===policy.value?.role?.id)?[policy.value.role,...roles.value]:roles.value);
const action=ref<'approve'|'reject'|'revoke'>();const locked=computed(()=>busy.value||unknown.value||loading.value);
let alive=true;let attempt:({kind:'policy';body:Save}|{kind:'approve'|'reject'|'revoke';id:string;body:Decision})&{key:string}|null=null;
watch(error,async v=>{if(v){await nextTick();box.value?.focus();}});
function apply(p:Policy){policy.value=p;Object.assign(form,{enabled:p.enabled,roleId:p.roleId,fields:[...p.fields] as Save['fields'],maxMinutes:p.maxMinutes,version:p.version});}
async function load(next=0){if(locked.value)return;loading.value=true;error.value='';selected.value=undefined;action.value=undefined;policy.value=undefined;rows.value=[];roles.value=[];
 try{const p=await session.client.GET('/tenant-support/policy');if(!alive)return;if(p.error){error.value=p.error.error.message;return;}apply(p.data.data);
 const r=await session.client.GET('/tenant-support/requests',{params:{query:{page:next,size:10}}});if(!alive)return;if(r.error){error.value=r.error.error.message;}else{rows.value=r.data.data.items;total.value=r.data.data.total;page.value=next;}
 if(p.data.data.canManage){const o=await session.client.GET('/tenant-support/role-options');if(!alive)return;if(o.error)error.value=o.error.error.message;else roles.value=o.data.data;}}
 catch{if(alive)error.value='读取失败，请重新读取；当前没有确认任何新授权';}finally{if(alive)loading.value=false;}}
async function detail(id:string){if(locked.value)return;busy.value=true;error.value='';selected.value=undefined;action.value=undefined;comment.value='';try{const r=await session.client.GET('/tenant-support/requests/{id}',{params:{path:{id}}});if(!alive)return;if(r.error)error.value=r.error.error.message;else selected.value=r.data.data;}catch{if(alive)error.value='申请详情读取失败，请重试';}finally{if(alive)busy.value=false;}}
function save(){if(locked.value||!policy.value?.canManage)return;if(!form.fields.length||form.enabled&&!form.roleId){error.value='请选择权限上界角色和至少一个基础字段';return;}attempt={kind:'policy',key:crypto.randomUUID(),body:{...form,fields:[...form.fields]}};void send();}
function decide(){const r=selected.value;if(locked.value||!r||!action.value||!comment.value.trim())return;attempt={kind:action.value,id:r.id,key:crypto.randomUUID(),body:{requestVersion:r.version,policyVersion:r.policyVersion,decisionVersion:r.consent?.version??0,comment:comment.value.trim(),...(action.value==='approve'&&r.scopeHash&&r.proposedExpiresAt?{scopeHash:r.scopeHash,expiresAt:r.proposedExpiresAt}:{})}};void send();}
async function send(){if(busy.value||!attempt)return;const a=attempt,wasUnknown=unknown.value;busy.value=true;error.value='';notice.value='';
 try{const h={'Idempotency-Key':a.key};const r=a.kind==='policy'?await session.client.PUT('/tenant-support/policy',{params:{header:h},body:a.body}):await session.client.POST(a.kind==='approve'?'/tenant-support/requests/{id}/approve':a.kind==='reject'?'/tenant-support/requests/{id}/reject':'/tenant-support/requests/{id}/revoke',{params:{path:{id:a.id},header:h},body:a.body});if(!alive)return;
 if(r.error){unknown.value=wasUnknown||r.response.status>=500;error.value=unknown.value?'结果尚未确认，请使用原请求重试；权限变化时请联系管理员核对':`${r.error.error.message}${r.response.status===409?'，请重新读取后核对范围再决定':''}`;if(!unknown.value){attempt=null;selected.value=undefined;action.value=undefined;}return;}
 if(!r.data?.data)throw Error('missing receipt');if(a.kind==='policy')apply(r.data.data as Policy);else selected.value=r.data.data as Request;
 attempt=null;unknown.value=false;action.value=undefined;comment.value='';notice.value='操作回执已收到，请重新读取核对当前策略和授权。批准不代表支持会话已启用。';
 }catch{if(alive){unknown.value=true;error.value='结果尚未确认，请使用原请求重试，勿重复提交';}}finally{if(alive)busy.value=false;}}
onMounted(()=>void load());onBeforeUnmount(()=>{alive=false;attempt=null;});
</script>
<template><section class="tenant-support" aria-label="租户支持授权"><h1>支持授权</h1><p>由本租户决定平台人员的临时支持上界。默认关闭，每次申请均需明确批准；不扮演租户用户。目前仅校验授权，不签发支持令牌，不开放业务访问。</p>
<p v-if="error" ref="box" role="alert" tabindex="-1">{{error}}</p><p v-if="notice" role="status">{{notice}}</p><el-button :disabled="locked" @click="load(page)">重新读取支持授权</el-button><p v-if="loading" role="status">正在读取策略和申请…</p>
<el-card v-if="policy" shadow="never"><template #header>租户授权策略 · 版本 {{policy.version}}</template>
<p v-if="!policy.canManage">当前账号仅可查看策略，不能修改。</p>
<el-form label-position="top" @submit.prevent="save"><fieldset :disabled="locked||!policy.canManage">
<el-form-item label="允许支持授权"><el-switch v-model="form.enabled" aria-label="允许支持授权" :disabled="locked||!policy.canManage" /></el-form-item>
<el-form-item label="权限上界角色"><el-select v-model="form.roleId" clearable aria-label="权限上界角色" :disabled="locked||!policy.canManage" placeholder="选择具备供应商只读权限的角色"><el-option v-for="r in roleChoices" :key="r.id" :value="r.id" :label="`${r.name}（${r.tenantAll?'租户全部':r.organizations.length+'个组织'}）`" /></el-select></el-form-item>
<p v-if="policy.roleId&&!policy.role">原角色当前不可用，不能据此批准或访问。</p><p v-if="policy.canManage&&!roleChoices.length">没有可用的角色：需具备供应商查看权限及租户全部或有效组织集合范围。</p>
<el-form-item label="允许的基础字段"><el-checkbox-group v-model="form.fields" aria-label="允许的基础字段" :disabled="locked||!policy.canManage"><el-checkbox v-for="f in fields" :key="f.id" :value="f.id">{{f.name}}</el-checkbox></el-checkbox-group></el-form-item>
<el-form-item label="最长授权分钟数"><el-input-number v-model="form.maxMinutes" :min="5" :max="60" aria-label="最长授权分钟数" :disabled="locked||!policy.canManage" /></el-form-item></fieldset>
<p>策略任何变更均使旧批准失效，必须重新审批。角色扩大不会扩大已批准范围；收紧、组织停用、撤回或到期会限制授权。</p>
<el-button type="primary" native-type="submit" :disabled="locked||!policy.canManage">保存支持策略</el-button></el-form></el-card>
<h2>平台已批准的申请与租户决定</h2><div class="scroll"><table><thead><tr><th>申请 / 工单</th><th>平台申请人</th><th>平台状态</th><th>申请到期（UTC）</th><th>操作</th></tr></thead><tbody><tr v-for="r in rows" :key="r.id"><td>{{r.bizNo}} / {{r.ticketNo}}</td><td>{{r.operatorName}}</td><td>{{labels[r.status]??r.status}}</td><td>{{r.requestedExpireAt}}</td><td><el-button :disabled="locked" @click="detail(r.id)">查看租户审批</el-button></td></tr><tr v-if="!rows.length"><td colspan="5">{{loading?'正在读取':'当前未加载到可见申请，请核对或重新读取。'}}</td></tr></tbody></table></div>
<div class="actions"><el-button :disabled="locked||page===0" @click="load(page-1)">上一页</el-button><span>第 {{page+1}} 页</span><el-button :disabled="locked||page>=10000||(page+1)*10>=total" @click="load(page+1)">下一页</el-button></div>
<el-card v-if="selected" shadow="never" aria-label="租户审批详情"><h3>{{selected.bizNo}}</h3><p>申请人：{{selected.operatorName}}；工单：{{selected.ticketNo}}；原因：{{selected.reason}}</p>
<p>平台状态：{{labels[selected.status]??selected.status}}；策略版本：{{selected.policyVersion}}。申请到期：{{selected.requestedExpireAt}} UTC。</p>
<p v-if="selected.consent?.scope">冻结批准快照（历史上界，不代表当前有效）：{{selected.consent.scope.tenantAll?'租户全部':selected.consent.scope.organizations.join('、')}}；基础字段：{{selected.consent.scope.fields.join('、')}}；批准截止：{{selected.consent.expiresAt}} UTC。</p>
<p v-else-if="selected.proposedScope&&!selected.consent">待批准快照：{{selected.proposedScope.tenantAll?'租户全部':selected.proposedScope.organizations.join('、')}}；基础字段：{{selected.proposedScope.fields.join('、')}}；截止：{{selected.proposedExpiresAt}} UTC。</p><p v-else>当前没有可批准的权限交集，或本策略版本已处理。</p>
<p v-if="selected.consent">本策略版本决定：{{labels[selected.consent.status]??selected.consent.status}} · {{selected.consent.reviewerName}} · {{selected.consent.comment}}。批准记录不表示当前有效或会话已启用。</p>
<div class="actions"><el-button v-if="selected.canApprove" :disabled="locked" @click="action='approve'">批准租户授权</el-button><el-button v-if="selected.canReject" :disabled="locked" @click="action='reject'">拒绝租户授权</el-button><el-button v-if="selected.canRevoke" :disabled="locked" @click="action='revoke'">撤回租户授权</el-button><el-button :disabled="locked" @click="detail(selected.id)">重新读取审批详情</el-button></div>
<el-form v-if="action" label-position="top" @submit.prevent="decide"><p>{{action==='approve'?'批准上述快照和截止时间，不启用会话':action==='reject'?'拒绝当前申请':'撤回当前批准，不能恢复'}}</p><el-form-item label="租户处理意见"><el-input v-model="comment" type="textarea" aria-label="租户处理意见" :maxlength="1000" :disabled="locked" /></el-form-item><el-button native-type="submit" type="primary" :disabled="locked||!comment.trim()">确认租户决定</el-button></el-form>
<h4>历史决定（不代表当前有效）</h4><ol><li v-for="d in selected.history" :key="d.id">策略 {{d.policyVersion}} · {{labels[d.status]??d.status}} · {{d.reviewerName}} · {{d.decidedAt}} UTC · {{d.comment}}<span v-if="d.revokedAt">；撤回：{{d.revokedAt}} UTC · {{d.revokeReason}}</span></li></ol></el-card>
<el-button v-if="unknown" :disabled="busy" @click="send">使用原请求重试支持授权</el-button></section></template>
<style scoped>
.tenant-support{max-width:1100px;min-width:0;color:var(--ism-text-primary,#25384a)}h1{font-size:24px}h2{font-size:19px;margin-top:24px}p,li{line-height:1.7;overflow-wrap:anywhere}.el-card{margin-top:18px}.el-select{width:100%;max-width:560px}fieldset{border:0;padding:0;min-width:0}.actions{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:16px 0}.actions .el-button{margin-left:0}.scroll{max-width:100%;overflow:auto}table{width:100%;min-width:700px;border-collapse:collapse}th,td{text-align:left;padding:12px;border-bottom:1px solid var(--ism-border,#dce3e9)}th{background:var(--ism-bg-muted,#f3f6f8)}[role=alert]{color:var(--ism-danger,#a52828);padding:12px;border:1px solid currentColor;border-radius:6px}:deep(.el-checkbox){min-height:44px}
</style>

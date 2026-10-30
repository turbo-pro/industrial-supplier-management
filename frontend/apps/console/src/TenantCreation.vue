<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { createIsmClient, type components } from '@ism/api-client';
const props=defineProps<{token?:string}>(),emit=defineEmits<{close:[];created:[tenant:components['schemas']['Tenant']]}>();
const client=createIsmClient({getAccessToken:()=>props.token});
const form=reactive<components['schemas']['CreateTenant']>({code:'',name:'',timezone:'Asia/Shanghai',locale:'zh-CN',packageVersionId:''});
const options=ref<components['schemas']['TenantPackageOption'][]>([]),selected=ref<components['schemas']['TenantPackageOption']>(),q=ref(''),page=ref(0),total=ref(0);
const busy=ref(false),searching=ref(false),unknown=ref(false),error=ref(''),searchError=ref(''),codeInput=ref<HTMLInputElement>(),errorSummary=ref<HTMLElement>();
const choices=computed(()=>selected.value&&!options.value.some(o=>o.id===selected.value?.id)?[selected.value,...options.value]:options.value);
let attempt:{key:string;body:components['schemas']['CreateTenant']}|null=null,epoch=0,mounted=true;
watch(error,async value=>{if(value){await nextTick();errorSummary.value?.focus();}});
async function search(next=0){if(busy.value||unknown.value)return;const generation=++epoch,token=props.token;searching.value=true;searchError.value='';try{const r=await client.GET('/console/tenants/package-options',{params:{query:{q:q.value,page:next,size:20}}});if(!mounted||generation!==epoch||token!==props.token)return;if(r.error){searchError.value=r.error.error.message;if(r.response.status===401||r.response.status===403){options.value=[];selected.value=undefined;form.packageVersionId='';}return;}options.value=r.data.data.items;page.value=next;total.value=r.data.data.total;}catch{if(mounted&&generation===epoch)searchError.value='套餐候选读取失败，请重新查询';}finally{if(mounted&&generation===epoch)searching.value=false;}}
function choose(){selected.value=choices.value.find(o=>o.id===form.packageVersionId);}
async function submit(){if(busy.value||searching.value)return;
 if(!attempt&&(!/^[A-Z][A-Z0-9_-]{1,49}$/.test(form.code.trim())||!form.name.trim()||!form.packageVersionId||!form.timezone.trim())){error.value='请填写合法租户编码、名称、时区，并选择已发布套餐版本';return;}
 attempt??={key:crypto.randomUUID(),body:{...form,code:form.code.trim(),name:form.name.trim(),timezone:form.timezone.trim()}};
 const token=props.token,wasUnknown=unknown.value;busy.value=true;error.value='';
 try{const r=await client.POST('/console/tenants',{params:{header:{'Idempotency-Key':attempt.key}},body:attempt.body});if(!mounted||token!==props.token)return;
  if(r.error){unknown.value=wasUnknown||r.response.status>=500;error.value=unknown.value?(r.response.status===401||r.response.status===403?'访问权限已变化，原创建结果仍未确认，请联系管理员按租户编码核对':'创建结果尚未确认，请使用原请求重试，勿重复创建租户'):r.error.error.message;if(!unknown.value)attempt=null;return;}
  if(!r.data?.data){unknown.value=true;error.value='创建结果尚未确认，请使用原请求重试';return;}
  attempt=null;unknown.value=false;emit('created',r.data.data);
 }catch{if(mounted&&token===props.token){unknown.value=true;error.value='创建结果尚未确认，请使用原请求重试，勿重复创建租户';}}finally{if(mounted&&token===props.token)busy.value=false;}
}
onMounted(()=>{void search();codeInput.value?.focus();});onBeforeUnmount(()=>{mounted=false;epoch++;attempt=null;});
</script>
<template>
 <section class="panel tenant-creation" aria-labelledby="tenant-create-title"><h2 id="tenant-create-title">新建租户</h2><p>先绑定有效套餐的已发布版本，再初始化组织和首管理员。创建完成前不能登录租户。</p>
 <p v-if="error" ref="errorSummary" role="alert" tabindex="-1">{{error}}</p>
 <form @submit.prevent="search(0)"><label>搜索套餐名称、编码或版本名<input v-model="q" maxlength="100" :disabled="busy||unknown"/></label><div class="actions"><button type="submit" :disabled="busy||unknown||searching">{{searching?'查询中…':'查询可选套餐'}}</button><button type="button" :disabled="busy||unknown||searching||page===0" @click="search(page-1)">上一页</button><button type="button" :disabled="busy||unknown||searching||page>=10000||(page+1)*20>=total" @click="search(page+1)">下一页</button></div><p v-if="searchError" role="alert">{{searchError}}</p><p v-else-if="!searching&&total===0">本次查询无可选的已发布版本，请调整查询或联系套餐管理员。</p></form>
 <form @submit.prevent="submit"><fieldset :disabled="busy||unknown">
  <label>租户编码<input ref="codeInput" v-model="form.code" required maxlength="50" pattern="[A-Z][A-Z0-9_-]{1,49}" autocomplete="off" placeholder="大写字母开头，可用数字、下划线和短横线"/></label>
  <label>租户名称<input v-model="form.name" required maxlength="200"/></label>
  <label for="tenant-create-package">初始套餐版本</label><select id="tenant-create-package" v-model="form.packageVersionId" required :disabled="searching" @change="choose"><option value="" disabled>请选择有效套餐的已发布版本</option><option v-for="o in choices" :key="o.id" :value="o.id">{{o.packageName}}（{{o.packageCode}}） · V{{o.versionNo}} {{o.versionName}}</option></select>
  <label>时区<input v-model="form.timezone" required maxlength="50" placeholder="Asia/Shanghai"/></label><p>默认语言：简体中文。套餐可用性会在保存时再次核对。</p>
 </fieldset><div class="actions"><button type="submit" :disabled="busy||searching">{{busy?'创建中…':unknown?'使用原请求重试':'创建并继续开通'}}</button><button type="button" class="secondary-button" :disabled="busy||unknown" @click="emit('close')">取消新建</button></div></form>
 </section>
</template>
<style scoped>
.tenant-creation{margin-top:20px;max-width:760px;min-width:0;}fieldset{border:0;padding:0;min-width:0;}label{display:block;margin-top:14px;}input,select{width:100%;box-sizing:border-box;min-height:44px;}p{line-height:1.6;overflow-wrap:anywhere;}.actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px;}.actions button{width:auto;min-height:44px;padding:0 18px;margin:0;}[role=alert]{color:#a52828;}
</style>

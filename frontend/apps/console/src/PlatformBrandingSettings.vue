<script setup lang="ts">
import {nextTick,onBeforeUnmount,onMounted,reactive,ref,watch} from 'vue';
import {createIsmClient,type components} from '@ism/api-client';
import {defaultBranding,isBranding} from './platformBranding';
type Settings=components['schemas']['PlatformBrandingSettings'];type Save=components['schemas']['SavePlatformBranding'];
const props=defineProps<{token:string}>(),emit=defineEmits<{close:[];changed:[]}>();
const client=createIsmClient({getAccessToken:()=>props.token});
const form=reactive<Save>({...defaultBranding,version:0});const loaded=ref(false),canManage=ref(false),busy=ref(false),unknown=ref(false),conflict=ref(false),error=ref(''),notice=ref(''),summary=ref<HTMLElement>();
let mounted=true,attempt:{key:string;body:Save}|null=null;
watch(error,async v=>{if(v){await nextTick();summary.value?.focus();}});
function valid(s:unknown):s is Settings{const v=s as Settings;return !!v&&isBranding(v.branding)&&Number.isInteger(v.version)&&v.version>=0&&typeof v.canManage==='boolean';}
async function load(){if(busy.value||unknown.value)return;busy.value=true;loaded.value=false;error.value='';const token=props.token;try{const r=await client.GET('/console/settings/branding');if(!mounted||token!==props.token)return;if(r.error)throw new Error(r.error.error.message);if(!valid(r.data?.data))throw new Error('配置响应格式异常');const s=r.data.data;Object.assign(form,s.branding,{version:s.version});canManage.value=s.canManage;loaded.value=true;conflict.value=false;attempt=null;}catch(e){if(mounted&&token===props.token)error.value=`读取品牌配置失败：${e instanceof Error?e.message:'网络不可用'}。请重新读取；已确认的保存结果不变。`;}finally{if(mounted&&token===props.token)busy.value=false;}}
async function save(){if(busy.value||(!attempt&&(!loaded.value||!canManage.value||conflict.value)))return;
 attempt??={key:crypto.randomUUID(),body:{...form}};const wasUnknown=unknown.value,token=props.token;busy.value=true;error.value='';notice.value='';let saved=false;
 try{const r=await client.PUT('/console/settings/branding',{params:{header:{'Idempotency-Key':attempt.key}},body:attempt.body});if(!mounted||token!==props.token)return;
 if(r.error){unknown.value=wasUnknown||r.response.status>=500;if(unknown.value)error.value='保存结果尚未确认，请使用原请求重试；权限变化时请联系管理员核对原请求。';else{conflict.value=r.response.status===409;error.value=r.error.error.message;if(r.response.status===401||r.response.status===403)loaded.value=false;attempt=null;}return;}
 if(!valid(r.data?.data)){unknown.value=true;error.value='保存结果尚未确认，请使用原请求重试';return;}
 unknown.value=false;attempt=null;saved=true;notice.value='平台品牌已保存；正在重新读取当前配置';
 }catch{if(mounted&&token===props.token){unknown.value=true;error.value='保存结果尚未确认，请使用原请求重试';}}finally{if(mounted&&token===props.token)busy.value=false;}
 if(saved&&mounted){emit('changed');await load();if(loaded.value)notice.value='平台品牌已保存并核对当前配置';else notice.value='平台品牌已保存，当前配置读取失败';}
}
onMounted(()=>void load());onBeforeUnmount(()=>{mounted=false;attempt=null;});
</script>
<template><section class="panel branding-settings" aria-labelledby="branding-title"><h2 id="branding-title">平台品牌配置</h2><p>仅影响 Console 登录页和平台工作区。租户品牌单独配置；以下名称、图片地址和页脚是公开展示信息。</p><p v-if="error" ref="summary" role="alert" tabindex="-1">{{error}}</p><p v-if="notice" role="status">{{notice}}</p><p v-if="loaded&&!canManage">当前为只读查看，无品牌修改权限。</p>
<form @submit.prevent="save"><fieldset :disabled="busy||unknown||!loaded||!canManage||conflict"><label>平台名称<input v-model="form.systemName" required maxlength="100"/></label><label>平台 Logo 地址<input v-model="form.logoUrl" maxlength="2000" placeholder="/assets/logo.png 或 https://…"/></label><label>站点图标地址<input v-model="form.faviconUrl" maxlength="2000"/></label><label>平台页脚文字<input v-model="form.footerText" maxlength="300"/></label><button type="button" class="secondary-button" @click="Object.assign(form,defaultBranding)">填入平台默认值</button><p>填入默认值后，点击保存才会生效。</p></fieldset><p>图片可留空，失败时显示文字标识；不支持 HTML。地址须可公开访问，系统不会代理下载或检查外部站点。当前版本：{{loaded?form.version:'未读取'}}</p><div class="actions"><button v-if="canManage||unknown" type="submit" :disabled="busy||(!unknown&&(!loaded||conflict))">{{busy?'处理中…':unknown?'使用原请求重试品牌保存':'保存平台品牌'}}</button><button type="button" class="secondary-button" :disabled="busy||unknown" @click="load">重新读取品牌配置</button><button type="button" class="secondary-button" :disabled="busy||unknown" @click="emit('close')">关闭品牌配置</button></div></form></section></template>
<style scoped>.branding-settings{max-width:820px;margin-bottom:24px;min-width:0;}fieldset{border:0;padding:0;min-width:0;}input{width:100%;min-width:0;}p{overflow-wrap:anywhere;line-height:1.6;}.actions{display:flex;flex-wrap:wrap;gap:8px;}.actions button{width:auto;padding:0 18px;min-height:44px;height:auto;}[role=alert]{color:#a52828;}</style>

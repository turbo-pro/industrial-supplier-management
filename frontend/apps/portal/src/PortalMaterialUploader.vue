<script setup lang="ts">
import { onUnmounted, ref } from 'vue';
import { type components, type createIsmClient } from '@ism/api-client';
const props=defineProps<{client:ReturnType<typeof createIsmClient>; index:number; disabled:boolean; remaining:number}>();
const emit=defineEmits<{change:[value:{items:{requirementIndex:number;uploadId:string}[];pending:boolean;count:number}];busy:[value:boolean];unauthorized:[]}>();
type Upload=components['schemas']['PortalCertificateUpload'];
type Item={key:string;file:File;upload?:Upload;progress:number};
const rows=ref<Item[]>([]),busy=ref(false),error=ref('');
let disposed=false,active:AbortController|undefined;
function changed(){emit('change',{items:rows.value.filter(r=>r.upload?.status==='COMPLETED').map(r=>({requirementIndex:props.index,uploadId:r.upload!.id})),pending:rows.value.some(r=>r.upload?.status!=='COMPLETED'),count:rows.value.length});}
function choose(event:Event){const input=event.target as HTMLInputElement,selected=Array.from(input.files??[]);input.value='';if(props.disabled||busy.value)return;if(rows.value.length+selected.length>Math.min(3,props.remaining)){error.value='每项最多3份、每轮最多5份附件，请移除多余文件。';return;}if(!crypto.subtle||!crypto.randomUUID){error.value='请使用HTTPS或本机安全地址上传。';return;}rows.value.push(...selected.map(file=>({key:crypto.randomUUID(),file,progress:0})));error.value='';changed();}
function remove(key:string){if(props.disabled||busy.value)return;rows.value=rows.value.filter(r=>r.key!==key);changed();}
function take<T>(r:{data?:{data:T};response:Response}):T{if(r.data)return r.data.data;if(r.response.status===401)emit('unauthorized');throw new Error(r.response.status===429?'上传额度已用完，请稍后重试。':r.response.status===410?'上传已过期，请移除后重新选择。':'附件上传失败；可继续上传或移除重选。');}
async function hash(bytes:ArrayBuffer){return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),n=>n.toString(16).padStart(2,'0')).join('');}
async function upload(row:Item){
 if(props.disabled||busy.value||disposed)return;
 const mime=/\.pdf$/i.test(row.file.name)?'application/pdf':/\.png$/i.test(row.file.name)?'image/png':/\.jpe?g$/i.test(row.file.name)?'image/jpeg':undefined;
 if(!mime||row.file.size<8||row.file.size>10485760){error.value='附件须为8字节至10MB的PDF、PNG或JPEG。';return;}
 busy.value=true;emit('busy',true);error.value='';active=new AbortController();
 try{
  const bytes=await row.file.arrayBuffer();if(disposed)return;
  let current=take(await props.client.POST('/portal/certificate-uploads',{body:{requestKey:row.key,fileName:row.file.name,contentType:mime,totalSize:row.file.size,sha256:await hash(bytes)},signal:active.signal}));if(disposed)return;row.upload=current;
  for(let i=0;i<current.totalChunks&&current.status!=='COMPLETED';i++){
   if(current.uploadedChunks.includes(i))continue;
   const chunk=bytes.slice(i*current.chunkSize,(i+1)*current.chunkSize);
   current=take(await props.client.PUT('/portal/certificate-uploads/{id}/chunks/{index}',{params:{path:{id:current.id,index:i},header:{'X-Chunk-SHA256':await hash(chunk)}},headers:{'Content-Type':'application/octet-stream'},body:chunk as unknown as string,bodySerializer:()=>chunk,signal:active.signal}));if(disposed)return;row.upload=current;row.progress=Math.round(current.uploadedChunks.length/current.totalChunks*100);
  }
  if(current.status!=='COMPLETED')current=take(await props.client.POST('/portal/certificate-uploads/{id}/complete',{params:{path:{id:current.id}},body:{version:current.version},signal:active.signal}));
  if(!disposed){row.upload=current;row.progress=100;changed();}
 }catch(e){if(!disposed)error.value=e instanceof Error?e.message:'附件上传中断，可在本页继续。';}
 finally{if(!disposed){busy.value=false;emit('busy',false);}}
}
onUnmounted(()=>{disposed=true;active?.abort();});
</script>
<template>
 <div class="materials"><label>要求 {{index+1}} 的附件（每项最多3份）<input type="file" multiple accept=".pdf,.png,.jpg,.jpeg" :disabled="disabled||busy" :aria-label="`要求 ${index+1} 的附件`" @change="choose"/></label><p class="help">每轮总共最多5份补正附件，另须上传证照原件；账号上传额度共同计算。本页失败可续传，刷新不保留文件。</p><p v-if="error" role="alert">{{error}}</p><div v-for="row in rows" :key="row.key"><p>{{row.file.name}} · {{row.upload?.status==='COMPLETED'?'附件上传完成':'尚未完成上传'}}</p><progress :value="row.progress" max="100" :aria-label="`${row.file.name}上传进度`"/><button type="button" :disabled="disabled||busy||row.upload?.status==='COMPLETED'" @click="upload(row)">{{row.upload?'继续上传附件':'上传附件'}}</button><button type="button" :disabled="disabled||busy" @click="remove(row.key)">移除附件</button></div></div>
</template>
<style scoped>
.materials{border-left:3px solid #dce4ed;padding-left:12px;margin:12px 0;overflow-wrap:anywhere}label{display:block}input{max-width:100%}button{min-height:44px;margin:6px 8px 6px 0}progress{max-width:100%}.help{line-height:1.5}
</style>

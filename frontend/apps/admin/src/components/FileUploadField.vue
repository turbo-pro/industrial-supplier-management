<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useSessionStore } from '../stores/session';

type Checkpoint={id?:string;initializeKey:string;completeKey:string};
const props=defineProps<{modelValue:string;accept?:string}>();
const emit=defineEmits<{(e:'update:modelValue',value:string):void}>();
const session=useSessionStore();
const busy=ref(false),paused=ref(false),progress=ref(0),fileName=ref(''),phase=ref('');
onBeforeUnmount(()=>{paused.value=true;});
function checkpointKey(hash:string,size:number){return `ism.upload.${session.actorId??'unknown'}.${hash}.${size}`;}
function readCheckpoint(key:string):Checkpoint|undefined{try{const raw=localStorage.getItem(key);return raw?JSON.parse(raw) as Checkpoint:undefined;}catch{return undefined;}}
function saveCheckpoint(key:string,value:Checkpoint){try{localStorage.setItem(key,JSON.stringify(value));}catch{}}
function clearCheckpoint(key:string){try{localStorage.removeItem(key);}catch{}}
async function sha256(bytes:ArrayBuffer){const digest=await crypto.subtle.digest('SHA-256',bytes);return Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,'0')).join('');}
async function selectFile(event:Event){const input=event.target as HTMLInputElement;const file=input.files?.[0];input.value='';if(!file||busy.value)return;
  if(file.size===0){ElMessage.warning('请选择非空文件');return;}
  if(!crypto.subtle){ElMessage.error('当前浏览器无法计算文件摘要，请使用安全环境访问');return;}
  emit('update:modelValue','');busy.value=true;paused.value=false;progress.value=0;fileName.value=file.name;phase.value='计算文件摘要';
  try{
    // 默认部署上限为 100 MB。WebCrypto 需要完整缓冲文件；后续大文件需改流式摘要实现。
    const hash=await sha256(await file.arrayBuffer());const key=checkpointKey(hash,file.size);
    let checkpoint=readCheckpoint(key)??{initializeKey:crypto.randomUUID(),completeKey:crypto.randomUUID()};saveCheckpoint(key,checkpoint);
    let upload;
    if(checkpoint.id){const resumed=await session.client.GET('/files/uploads/{id}',{params:{path:{id:checkpoint.id}}});
      if(!resumed.error&&resumed.data?.data?.sha256===hash&&resumed.data.data.totalSize===file.size)upload=resumed.data.data;
      else{checkpoint={initializeKey:crypto.randomUUID(),completeKey:crypto.randomUUID()};saveCheckpoint(key,checkpoint);}
    }
    if(!upload){const initialized=await session.client.POST('/files/uploads',{params:{header:{'Idempotency-Key':checkpoint.initializeKey}},body:{fileName:file.name,contentType:file.type||'application/octet-stream',totalSize:file.size,sha256:hash}});
      if(initialized.error||!initialized.data?.data)throw new Error(initialized.error?.error.message??'初始化上传失败');upload=initialized.data.data;checkpoint.id=upload.id;saveCheckpoint(key,checkpoint);
    }
    if(upload.status==='COMPLETED'&&upload.fileId){emit('update:modelValue',upload.fileId);progress.value=100;phase.value='上传完成';clearCheckpoint(key);return;}
    if(upload.status!=='UPLOADING')throw new Error('上传会话不可继续，请重新选择文件');
    if(!session.accessToken)throw new Error('登录状态已失效');
    const completed=new Set(upload.uploadedChunks);progress.value=Math.round(completed.size/upload.totalChunks*100);
    for(let index=0;index<upload.totalChunks;index++){
      if(completed.has(index))continue;
      if(paused.value){phase.value='已暂停；重新选择同一文件可续传';return;}
      phase.value=`上传分片 ${index+1}/${upload.totalChunks}`;
      const chunk=await file.slice(index*upload.chunkSize,Math.min(file.size,(index+1)*upload.chunkSize)).arrayBuffer();const chunkHash=await sha256(chunk);
      const response=await fetch(`/api/files/uploads/${upload.id}/chunks/${index}`,{method:'PUT',headers:{Authorization:`Bearer ${session.accessToken}`,'Content-Type':'application/octet-stream','X-Chunk-SHA256':chunkHash},body:chunk});
      if(!response.ok){let message=`第 ${index+1} 片上传失败（${response.status}）`;try{const error=await response.json();message=error.error?.message??message;}catch{}throw new Error(message);}
      completed.add(index);progress.value=Math.round(completed.size/upload.totalChunks*100);
    }
    if(paused.value){phase.value='已暂停；重新选择同一文件可续传';return;}
    phase.value='校验并合并文件';const latest=await session.client.GET('/files/uploads/{id}',{params:{path:{id:upload.id}}});
    if(latest.error||!latest.data?.data)throw new Error('读取上传进度失败');
    if(paused.value){phase.value='已暂停；重新选择同一文件可续传';return;}
    if(latest.data.data.status==='COMPLETED'&&latest.data.data.fileId){emit('update:modelValue',latest.data.data.fileId);clearCheckpoint(key);phase.value='上传完成';return;}
    const completedFile=await session.client.POST('/files/uploads/{id}/complete',{params:{path:{id:upload.id},query:{version:latest.data.data.version},header:{'Idempotency-Key':checkpoint.completeKey}}});
    if(completedFile.error||!completedFile.data?.data)throw new Error(completedFile.error?.error.message??'文件合并失败');
    emit('update:modelValue',completedFile.data.data.id);clearCheckpoint(key);phase.value='上传完成';ElMessage.success('文件已上传并关联');
  }catch(error){phase.value='上传中断；重新选择同一文件可续传';ElMessage.error(error instanceof Error?error.message:'上传失败');}
  finally{busy.value=false;}
}
</script>
<template>
  <div class="file-upload-field">
    <el-input :model-value="modelValue" placeholder="已上传文件 ID，也可选择本地文件上传" @update:model-value="emit('update:modelValue',$event)"/>
    <div class="file-upload-actions"><label class="file-upload-select"><input type="file" :accept="accept" :disabled="busy" @change="selectFile"/>选择文件上传</label><el-button v-if="busy" link type="warning" @click="paused=true">暂停</el-button><span v-if="fileName">{{fileName}} · {{phase}}</span></div>
    <el-progress v-if="busy||progress>0" :percentage="progress" :stroke-width="6"/>
  </div>
</template>
<style scoped>
.file-upload-actions{display:flex;align-items:center;gap:12px;margin-top:7px;color:var(--el-text-color-secondary);font-size:12px}
.file-upload-select{position:relative;color:var(--el-color-primary);cursor:pointer;white-space:nowrap}
.file-upload-select input{position:absolute;inset:0;opacity:0;width:100%;cursor:pointer}
.file-upload-select:has(input:disabled){opacity:.5;cursor:not-allowed}
.file-upload-field :deep(.el-progress){margin-top:7px}
</style>

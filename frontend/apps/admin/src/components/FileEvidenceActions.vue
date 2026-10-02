<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useSessionStore } from '../stores/session';

const props=defineProps<{fileId:string|null|undefined}>();
const session=useSessionStore();
const busy=ref(false),previewOpen=ref(false),previewUrl=ref(''),previewName=ref('');
let alive=true;
function releasePreview(){if(previewUrl.value){URL.revokeObjectURL(previewUrl.value);previewUrl.value='';}}
watch(()=>props.fileId,()=>{previewOpen.value=false;releasePreview();});
onBeforeUnmount(()=>{alive=false;releasePreview();});
async function openFile(preview:boolean){
  const id=props.fileId;
  if(!id||!(/^[1-9][0-9]{0,18}$/.test(id))||busy.value)return;
  busy.value=true;
  try{
    const metadata=await session.client.GET('/files/{id}',{params:{path:{id}}});
    if(metadata.error||!metadata.data?.data)throw new Error(metadata.error?.error.message??'文件不存在或无权查看');
    const info=metadata.data.data;
    const safeType=({'image/png':'image/png','image/jpeg':'image/jpeg','image/webp':'image/webp'} as Record<string,string>)[info.contentType.toLowerCase()];
    if(preview&&(!safeType||info.size>10*1024*1024)){ElMessage.warning('仅支持预览 10 MB 以内的 PNG、JPEG、WebP 图片；其他文件请下载');return;}
    if(!session.accessToken)throw new Error('登录状态已失效');
    const response=await fetch(`/api/files/${id}/content`,{headers:{Authorization:`Bearer ${session.accessToken}`}});
    if(!response.ok)throw new Error(response.status===403?'没有文件下载权限':response.status===404?'文件不存在或已失效':`读取文件失败（${response.status}）`);
    const bytes=await response.blob();
    if(!alive||props.fileId!==id)return;
    const url=URL.createObjectURL(preview?new Blob([bytes],{type:safeType}):bytes);
    if(preview){releasePreview();previewUrl.value=url;previewName.value=info.originalName;previewOpen.value=true;}
    else{const anchor=document.createElement('a');anchor.href=url;anchor.download=info.originalName;document.body.append(anchor);anchor.click();anchor.remove();setTimeout(()=>URL.revokeObjectURL(url),60_000);}
  }catch(error){ElMessage.error(error instanceof Error?error.message:'文件读取失败');}
  finally{busy.value=false;}
}
</script>
<template>
  <span v-if="fileId" class="file-evidence-actions"><span>{{fileId}}</span><el-button link type="primary" :loading="busy" @click="openFile(true)">图片预览</el-button><el-button link type="primary" :disabled="busy" @click="openFile(false)">下载</el-button></span>
  <span v-else>—</span>
  <el-dialog v-model="previewOpen" :title="previewName" width="min(90vw, 980px)" append-to-body @closed="releasePreview"><img v-if="previewUrl" :src="previewUrl" :alt="previewName" class="file-evidence-preview"/></el-dialog>
</template>
<style scoped>
.file-evidence-actions{display:inline-flex;align-items:center;gap:8px;white-space:nowrap}
.file-evidence-preview{display:block;max-width:100%;max-height:75vh;margin:auto;object-fit:contain}
</style>

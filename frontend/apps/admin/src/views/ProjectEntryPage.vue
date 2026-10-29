<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { paths } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
import ContractProjectPage from './ContractProjectPage.vue';
import SiteProjectPage from './SiteProjectPage.vue';
import OrganizationProjectOverview from '../components/OrganizationProjectOverview.vue';
type Access=paths['/projects/access-mode']['get']['responses'][200]['content']['application/json']['data'];
const session=useSessionStore(),access=ref<Access>(),error=ref(''),loading=ref(false),overview=ref(false);
let epoch=0,mounted=true;
async function load(){const generation=++epoch;access.value=undefined;error.value='';overview.value=false;loading.value=true;
 try{const r=await session.client.GET('/projects/access-mode');if(!mounted||generation!==epoch)return;if(r.error||!r.data?.data){error.value=r.error?.error.message??'项目入口未返回完整策略，请重试';return;}access.value=r.data.data;overview.value=access.value.siteContextRequired&&!access.value.canEnterSiteWorkspace&&access.value.canViewOrganizationProjects;}
 catch{if(mounted&&generation===epoch)error.value='项目入口读取失败，请重试';}finally{if(mounted&&generation===epoch)loading.value=false;}}
watch(()=>session.accessToken,()=>{void load();});onMounted(load);onBeforeUnmount(()=>{mounted=false;epoch++;});
</script>
<template>
 <section class="project-entry">
  <p v-if="loading" role="status">正在读取项目访问方式…</p><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-button v-if="error" @click="load">重新读取项目入口</el-button>
  <template v-if="access">
   <ContractProjectPage v-if="!access.siteContextRequired&&access.legacyWritesAllowed"/>
   <template v-else-if="access.siteContextRequired">
    <nav class="entry-views" aria-label="项目视角"><el-button v-if="access.canEnterSiteWorkspace" :type="!overview?'primary':'default'" :aria-pressed="!overview" @click="overview=false">场站项目</el-button><el-button v-if="access.canViewOrganizationProjects" :type="overview?'primary':'default'" :aria-pressed="overview" @click="overview=true">组织项目总览（只读）</el-button></nav>
    <OrganizationProjectOverview v-if="overview&&access.canViewOrganizationProjects" :can-view-details="access.canViewOrganizationProjectDetails"/><SiteProjectPage v-else-if="access.canEnterSiteWorkspace"/><el-alert v-else title="未获得场站或组织项目视角，请联系管理员授权" type="warning" :closable="false"/>
   </template><el-alert v-else title="项目入口状态不一致，请联系管理员核对" type="error" :closable="false"/>
  </template>
 </section>
</template>
<style scoped>.project-entry{min-width:0;}.entry-views{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:20px;}.entry-views .el-button{margin-left:0;}</style>

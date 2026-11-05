<script setup lang="ts">
import {computed,ref} from 'vue';
import {ElAvatar,ElDrawer,ElIcon,ElMenu,ElMenuItem,ElTag} from 'element-plus';
import {Box,OfficeBuilding,User,Setting,Service,Expand,Close,Lock} from '@element-plus/icons-vue';
import PlatformBrand from './PlatformBrand.vue';
import type {Branding} from './platformBranding';
const props=defineProps<{branding:Branding;displayName:string;permissions:string[];activeSection:string}>();
const emit=defineEmits<{navigate:[section:string]}>();const mobileOpen=ref(false),active=computed(()=>props.activeSection);
const entries=[{key:'packages',label:'套餐与授权',icon:Box,permissions:['platform:package:view','platform:package:manage']},{key:'tenants',label:'租户管理',icon:OfficeBuilding,permissions:['platform:tenant:view','platform:tenant:create']},{key:'support',label:'支持访问',icon:Service,permissions:['platform:support:view']},{key:'accounts',label:'平台账号',icon:User,permissions:['platform:user:view']},{key:'branding',label:'平台配置',icon:Setting,permissions:['platform:branding:view']}];
function select(key:string){mobileOpen.value=false;emit('navigate',key);}
</script>
<template><div class="console-frame">
 <aside class="console-sidebar" aria-label="平台主导航"><div class="sidebar-brand"><PlatformBrand :branding="branding"/><span class="console-edition">PLATFORM CONSOLE</span></div><p class="nav-caption">平台运营</p><ElMenu :default-active="active" @select="select"><template v-for="item in entries" :key="item.key"><ElMenuItem v-if="item.permissions.some(p=>permissions.includes(p))" :index="item.key"><ElIcon><component :is="item.icon"/></ElIcon><span>{{item.label}}</span></ElMenuItem></template></ElMenu><div class="sidebar-boundary"><ElIcon><Lock/></ElIcon><span>独立平台控制面<br/><small>租户业务数据受授权保护</small></span></div></aside>
 <div class="console-body"><div class="console-topbar"><div class="topbar-location"><button type="button" class="mobile-menu-toggle" aria-label="打开平台导航" @click="mobileOpen=true"><ElIcon><Expand/></ElIcon></button><span>平台管理</span><span class="breadcrumb-divider">/</span><strong>运营工作台</strong></div><div class="topbar-account"><ElTag effect="plain" size="small">平台账号</ElTag><ElAvatar :size="30">{{displayName.slice(0,1)||'管'}}</ElAvatar><span>{{displayName||'正在读取账号'}}</span></div></div><slot/><footer v-if="branding.footerText" class="workspace-footer">{{branding.footerText}}</footer></div>
 <ElDrawer v-model="mobileOpen" title="平台导航" direction="ltr" size="min(280px, 86vw)" :show-close="false" class="console-mobile-drawer"><template #header><strong>平台导航</strong><button type="button" class="drawer-close" aria-label="关闭平台导航" @click="mobileOpen=false"><ElIcon><Close/></ElIcon></button></template><PlatformBrand :branding="branding"/><ElMenu :default-active="active" @select="select"><template v-for="item in entries" :key="item.key"><ElMenuItem v-if="item.permissions.some(p=>permissions.includes(p))" :index="item.key"><ElIcon><component :is="item.icon"/></ElIcon>{{item.label}}</ElMenuItem></template></ElMenu><p class="drawer-note">Console 管理平台运营。租户业务访问需独立授权。</p></ElDrawer>
</div></template>

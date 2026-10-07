<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Fold, FullScreen, Search, SwitchButton } from '@element-plus/icons-vue';
import { useSessionStore } from './stores/session';
const session=useSessionStore();const router=useRouter();const collapsed=ref(false);const submitting=ref(false);const changing=ref(false);const showPasswordDialog=ref(false);const passwordForm=reactive({oldPassword:'',newPassword:'',confirmPassword:''});const form=reactive({tenantCode:'demo',username:'admin',password:'Admin@123456'});
async function login(){submitting.value=true;try{await session.login(form);if(!session.passwordChangeRequired)await router.replace('/suppliers/master');showPasswordDialog.value=session.passwordChangeRequired||session.passwordChangeRecommended;ElMessage.success('登录成功');}catch(e){ElMessage.error(e instanceof Error?e.message:'登录失败');}finally{submitting.value=false;}}
async function changePassword(){if(passwordForm.newPassword!==passwordForm.confirmPassword){ElMessage.error('两次输入的新密码不一致');return;}changing.value=true;try{await session.changePassword(passwordForm.oldPassword,passwordForm.newPassword);showPasswordDialog.value=false;passwordForm.oldPassword='';passwordForm.newPassword='';passwordForm.confirmPassword='';await router.replace('/');ElMessage.success('密码已修改，请重新登录');}catch(e){ElMessage.error(e instanceof Error?e.message:'修改密码失败');}finally{changing.value=false;}}
function dismissPasswordDialog(){if(!session.passwordChangeRequired){showPasswordDialog.value=false;session.passwordChangeRecommended=false;}}
async function logout(){session.logout();await router.replace('/');}
function route(path:string){return router.resolve(path).matched.some(r=>r.path===path)?path:'/coming-soon';}
</script>
<template>
  <div v-if="!session.accessToken" class="login-page"><section class="brand-panel"><div class="brand-mark">ISM</div><p>INDUSTRIAL SUPPLIER MANAGEMENT</p><h1>工业供应商管理平台</h1><span>面向制造业与化工企业的供应商全生命周期协同平台</span></section><el-card class="login-card" shadow="always"><template #header><div><h2>欢迎登录</h2><p>请输入租户和账号信息</p></div></template><el-form label-position="top" @submit.prevent="login"><el-form-item label="租户编码"><el-input v-model="form.tenantCode" size="large" /></el-form-item><el-form-item label="用户名"><el-input v-model="form.username" size="large" /></el-form-item><el-form-item label="密码"><el-input v-model="form.password" type="password" show-password size="large" @keyup.enter="login" /></el-form-item><el-button type="primary" size="large" :loading="submitting" class="login-button" @click="login">登录系统</el-button></el-form></el-card></div>
  <el-container v-else class="app-shell">
    <el-dialog :model-value="showPasswordDialog||session.passwordChangeRequired" title="修改登录密码" width="440px" :close-on-click-modal="!session.passwordChangeRequired" :close-on-press-escape="!session.passwordChangeRequired" :show-close="!session.passwordChangeRequired" @close="dismissPasswordDialog">
      <p>{{session.passwordChangeRequired?'首次登录或管理员要求修改密码后才能继续使用。':'此账号长期未登录，建议修改密码以保障安全。'}}</p>
      <el-form label-position="top" @submit.prevent="changePassword">
        <el-form-item label="当前密码"><el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="确认新密码"><el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
      </el-form>
      <p>新密码至少 12 位，包含大小写字母、数字和特殊字符。</p>
      <template #footer><el-button v-if="!session.passwordChangeRequired" @click="dismissPasswordDialog">稍后再说</el-button><el-button type="primary" :loading="changing" @click="changePassword">确认修改</el-button></template>
    </el-dialog>
    <el-aside :width="collapsed?'68px':'232px'" class="sidebar">
      <div class="logo"><img v-if="session.branding.logoUrl" :src="session.branding.logoUrl" alt="租户 Logo" style="max-width:44px;max-height:36px;object-fit:contain"/><strong v-else>ISM</strong><span v-if="!collapsed">{{session.branding.systemName}}</span></div>
      <nav class="sidebar-menu-scroll" aria-label="主导航">
        <el-menu router :collapse="collapsed" :default-active="$route.path" background-color="#102a43" text-color="#b8c7d5" active-text-color="#fff">
          <el-sub-menu v-for="menu in session.menus" :key="menu.id" :index="menu.route">
            <template #title><el-icon><FullScreen /></el-icon><span>{{menu.name}}</span></template>
            <el-menu-item v-for="child in menu.children" :key="child.id" :index="route(child.route)">{{child.name}}</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </nav>
    </el-aside>
    <el-container><el-header class="topbar"><div class="topbar-left"><el-button text :icon="Fold" @click="collapsed=!collapsed"/><el-breadcrumb separator="/"><el-breadcrumb-item>{{session.branding.systemName}}</el-breadcrumb-item><el-breadcrumb-item>{{ $route.meta.title }}</el-breadcrumb-item></el-breadcrumb></div><div class="topbar-right"><el-button text :icon="Search" @click="router.push('/search')">业务搜索</el-button><el-select :model-value="session.currentOrganization?.id" style="width:220px" @change="session.switchOrganization"><el-option v-for="item in session.organizationOptions" :key="item.id" :label="item.label" :value="item.id"/></el-select><span class="user-name">租户管理员</span><el-button text @click="showPasswordDialog=true">修改密码</el-button><el-button text :icon="SwitchButton" @click="logout">退出</el-button></div></el-header><el-main class="content"><router-view v-if="!session.passwordChangeRequired" /><footer v-if="session.branding.footerText" style="margin-top:24px;text-align:center;color:#71808f">{{session.branding.footerText}}</footer></el-main></el-container>
  </el-container>
</template>

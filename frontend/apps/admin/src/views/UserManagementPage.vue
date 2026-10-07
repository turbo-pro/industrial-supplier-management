<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const users = ref<components['schemas']['AccessUser'][]>([]);
const roles = ref<components['schemas']['Role'][]>([]);
const loading = ref(false);
const saving = ref(false);
const showCreate = ref(false);
const form = reactive({ username: '', displayName: '', initialPassword: '', primaryOrganizationId: '', roleIds: [] as string[] });

async function load() {
  loading.value = true;
  try {
    const [userResult, roleResult] = await Promise.all([
      session.client.GET('/access/users'), session.client.GET('/access/roles'),
    ]);
    if (userResult.error) throw new Error('用户列表加载失败');
    users.value = userResult.data.data;
    if (roleResult.error) { roles.value = []; ElMessage.warning('角色列表无权限或加载失败，暂不能创建用户'); }
    else roles.value = roleResult.data.data.filter(role => role.status === 'ACTIVE');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function createUser() {
  if (!/^[a-zA-Z][a-zA-Z0-9._-]{2,99}$/.test(form.username) || !form.displayName.trim() ||
      form.initialPassword.length < 12 || !form.primaryOrganizationId || form.roleIds.length === 0) {
    ElMessage.warning('请填写有效账号、至少 12 位初始密码、主组织和角色');
    return;
  }
  saving.value = true;
  try {
    const { error } = await session.client.POST('/access/users', {
      params: { header: { 'Idempotency-Key': crypto.randomUUID() } },
      body: { username: form.username, displayName: form.displayName,
        initialPassword: form.initialPassword, primaryOrganizationId: form.primaryOrganizationId,
        roleIds: form.roleIds },
    });
    if (error) throw new Error(error.error.message);
    showCreate.value = false;
    form.username = ''; form.displayName = ''; form.initialPassword = ''; form.primaryOrganizationId = ''; form.roleIds = [];
    ElMessage.success('用户已创建，首次登录须修改密码；请通过安全渠道交付初始密码');
    await load();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建失败，请刷新核对结果');
  } finally {
    saving.value = false;
  }
}

async function changeStatus(user: components['schemas']['AccessUser']) {
  const status = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  try {
    await ElMessageBox.confirm(`确认${status === 'DISABLED' ? '停用' : '恢复'}账号 ${user.username}？${status === 'DISABLED' ? '已登录会话将立即失效。' : ''}`, '账号状态变更', { type: 'warning' });
  } catch { return; }
  saving.value = true;
  try {
    const { error, response } = await session.client.PUT('/access/users/{id}/status', {
      params: { path: { id: user.id }, header: { 'Idempotency-Key': crypto.randomUUID() } },
      body: { status, version: user.version },
    });
    if (error) {
      ElMessage.error(response.status === 409 ? '账号状态或版本已变化，请刷新后重试' : error.error.message);
      await load();
      return;
    }
    ElMessage.success(status === 'DISABLED' ? '账号已停用' : '账号已恢复');
    await load();
  } catch {
    ElMessage.error('操作结果不确定，请刷新核对');
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-heading"><div><h1>用户管理</h1><p>账号状态由后端按租户、权限和版本校验；不能停用自己或最后一名有效租户管理员。</p></div><div><el-button :loading="loading" @click="load">刷新</el-button><el-button type="primary" @click="showCreate=true">新建用户</el-button></div></div>
    <el-card shadow="never" v-loading="loading">
      <el-table :data="users" row-key="id">
        <el-table-column prop="username" label="用户名" min-width="180"/>
        <el-table-column prop="displayName" label="姓名" min-width="180"/>
        <el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="row.status==='ACTIVE'?'success':'info'">{{row.status==='ACTIVE'?'启用':'停用'}}</el-tag></template></el-table-column>
        <el-table-column label="首次改密" width="120"><template #default="{row}">{{row.passwordChangeRequired?'待完成':'已完成'}}</template></el-table-column>
        <el-table-column label="操作" width="150"><template #default="{row}"><el-button link :type="row.status==='ACTIVE'?'danger':'primary'" :disabled="saving||row.id===session.actorId" @click="changeStatus(row)">{{row.status==='ACTIVE'?'停用':'恢复'}}</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="showCreate" title="新建租户用户" width="560px" @closed="form.initialPassword=''">
      <el-alert title="初始密码仅用于首次登录，创建后须修改；请通过安全渠道交付，不要在消息或日志中明文传播。" type="info" :closable="false"/>
      <el-form label-position="top" style="margin-top:16px">
        <el-form-item label="用户名"><el-input v-model="form.username" maxlength="100"/></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.displayName" maxlength="100"/></el-form-item>
        <el-form-item label="初始密码"><el-input v-model="form.initialPassword" type="password" show-password autocomplete="new-password" maxlength="128"/></el-form-item>
        <el-form-item label="主组织"><el-select v-model="form.primaryOrganizationId" filterable style="width:100%"><el-option v-for="org in session.organizationOptions" :key="org.id" :label="org.label" :value="org.id"/></el-select></el-form-item>
        <el-form-item label="角色"><el-select v-model="form.roleIds" multiple filterable style="width:100%"><el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id"/></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="roles.length===0" @click="createUser">创建</el-button></template>
    </el-dialog>
  </div>
</template>

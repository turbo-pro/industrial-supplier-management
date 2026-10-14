<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type Role = components['schemas']['Role'];
type ScopeType = components['schemas']['DataScopeGrant']['scopeType'];
type ScopeForm = { resourceCode: string; scopeType: ScopeType; organizationIds: string };
const scopeTypes: ScopeType[] = ['TENANT_ALL', 'ORGANIZATION_SET', 'PROJECT_SET', 'OWNED', 'CREATED', 'NONE'];
const session = useSessionStore();
const roles = ref<Role[]>([]);
const catalog = ref<components['schemas']['RoleGrantCatalog']>();
const detail = ref<components['schemas']['RoleGrants']>();
const selectedId = ref('');
const busy = ref(false);
const loading = ref(false);
const showCreate = ref(false);
const createForm = reactive({ code: '', name: '' });
const roleName = ref('');
const permissionCodes = ref<string[]>([]);
const menuCodes = ref<string[]>([]);
const scopes = ref<ScopeForm[]>([]);
const selectedRole = computed(() => roles.value.find(role => role.id === selectedId.value));
const canManage = computed(() => catalog.value?.canManage === true);
const editable = computed(() => canManage.value && !!detail.value && detail.value.role.status === 'ACTIVE' && !detail.value.role.builtIn);

function resetForm(value: components['schemas']['RoleGrants']) {
  roleName.value = value.role.name;
  permissionCodes.value = [...value.permissionCodes];
  menuCodes.value = [...value.menuCodes];
  scopes.value = value.dataScopes.map(row => ({
    resourceCode: row.resourceCode, scopeType: row.scopeType, organizationIds: row.organizationIds.join(','),
  }));
}

async function loadRoles(preferredId = selectedId.value) {
  loading.value = true;
  try {
    const [roleResult, catalogResult] = await Promise.all([
      session.client.GET('/access/roles'), session.client.GET('/access/role-grant-catalog'),
    ]);
    if (roleResult.error || catalogResult.error) throw new Error('角色或授权目录加载失败，请检查角色查看权限');
    roles.value = roleResult.data.data;
    catalog.value = catalogResult.data.data;
    const nextId = roles.value.find(role => role.id === preferredId)?.id ?? roles.value[0]?.id ?? '';
    await selectRole(nextId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色加载失败');
  } finally { loading.value = false; }
}

async function selectRole(id: string) {
  selectedId.value = id;
  detail.value = undefined;
  if (!id) return;
  const { data, error } = await session.client.GET('/access/roles/{id}/grants', { params: { path: { id } } });
  if (error) { ElMessage.error(error.error.message); return; }
  detail.value = data.data;
  resetForm(data.data);
}

async function createRole() {
  if (!canManage.value) return;
  createForm.code = createForm.code.trim().toUpperCase();
  createForm.name = createForm.name.trim();
  if (!/^[A-Z][A-Z0-9_]{1,63}$/.test(createForm.code) || !createForm.name || createForm.name.length > 100) {
    ElMessage.warning('角色编码须为 2–64 位大写字母、数字或下划线，名称不能超过 100 字'); return;
  }
  busy.value = true;
  try {
    const { data, error } = await session.client.POST('/access/roles', {
      params: { header: { 'Idempotency-Key': crypto.randomUUID() } }, body: { ...createForm },
    });
    if (error) throw new Error(error.error.message);
    showCreate.value = false;
    createForm.code = ''; createForm.name = '';
    await loadRoles(data.data.id);
    ElMessage.success('角色已创建，请配置权限、菜单和数据范围');
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败'); }
  finally { busy.value = false; }
}

async function saveName() {
  if (!editable.value || !detail.value) return;
  const name = roleName.value.trim();
  if (!name || name.length > 100) { ElMessage.warning('请输入不超过 100 字的角色名称'); return; }
  busy.value = true;
  try {
    const { data, error, response } = await session.client.PUT('/access/roles/{id}', {
      params: { path: { id: detail.value.role.id }, header: { 'Idempotency-Key': crypto.randomUUID() } },
      body: { name, version: detail.value.role.version },
    });
    if (error) throw new Error(response.status === 409 ? '角色已被修改，请刷新后重试' : error.error.message);
    await loadRoles(data.data.id);
    ElMessage.success('角色名称已更新');
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败'); }
  finally { busy.value = false; }
}

async function saveGrants() {
  if (!editable.value || !detail.value || !catalog.value) return;
  const resourceCodes = scopes.value.map(row => row.resourceCode);
  if (scopes.value.length === 0 || resourceCodes.some(code => !code) || new Set(resourceCodes).size !== resourceCodes.length) {
    ElMessage.warning('至少配置一个数据范围，且资源不能重复'); return;
  }
  if (permissionCodes.value.some(code => !catalog.value?.permissionCodes.includes(code)) ||
      menuCodes.value.some(code => !catalog.value?.menuCodes.includes(code))) {
    ElMessage.warning('权限或菜单代码不在有效目录中，请刷新后重试'); return;
  }
  const dataScopes = scopes.value.map(row => ({
    resourceCode: row.resourceCode, scopeType: row.scopeType,
    organizationIds: row.organizationIds.split(/[,，\s]+/).filter(Boolean),
  }));
  if (dataScopes.some(row => (row.scopeType === 'ORGANIZATION_SET') !== (row.organizationIds.length > 0))) {
    ElMessage.warning('仅组织集合范围需填写组织 ID，且至少填写一个'); return;
  }
  try { await ElMessageBox.confirm('授权将整体替换。请核对权限、菜单和每个资源的数据范围；已登录用户的有效权限可能立即变化。', '确认替换角色授权', { type: 'warning' }); }
  catch { return; }
  busy.value = true;
  try {
    const { error, response } = await session.client.PUT('/access/roles/{id}/grants', {
      params: { path: { id: detail.value.role.id }, header: { 'Idempotency-Key': crypto.randomUUID() } },
      body: { version: detail.value.role.version, permissionCodes: permissionCodes.value,
        menuCodes: menuCodes.value, dataScopes },
    });
    if (error) throw new Error(response.status === 409 ? '角色授权版本已变化，请刷新并重新核对' : error.error.message);
    await loadRoles(detail.value.role.id);
    ElMessage.success('角色授权已更新');
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '授权失败'); }
  finally { busy.value = false; }
}

onMounted(() => { void loadRoles(); });
</script>

<template>
  <div class="role-page">
    <div class="page-heading"><div><h1>角色权限</h1><p>按当前租户配置角色；内置角色只读，授权变更需按版本确认。</p></div>
      <div><el-button :loading="loading" @click="loadRoles()">刷新</el-button><el-button type="primary" :disabled="!canManage" @click="showCreate=true">新建角色</el-button></div>
    </div>
    <div class="role-layout">
      <el-card class="role-list" shadow="never"><template #header>租户角色</template>
        <el-table :data="roles" v-loading="loading" highlight-current-row @row-click="(row: Role) => selectRole(row.id)">
          <el-table-column prop="name" label="名称" min-width="140" /><el-table-column prop="code" label="编码" min-width="140" />
          <el-table-column label="类型" width="90"><template #default="{row}">{{row.builtIn?'内置':'自定义'}}</template></el-table-column>
        </el-table>
      </el-card>
      <el-card class="role-detail" shadow="never" v-loading="loading"><template #header>{{selectedRole?.name || '选择角色'}}</template>
        <template v-if="detail">
          <el-alert v-if="!canManage" type="info" :closable="false" title="当前账号仅可查看角色授权" />
          <el-alert v-if="detail.role.builtIn" type="info" :closable="false" title="内置角色不可修改授权" />
          <el-form label-position="top" class="role-form">
            <el-form-item label="角色编码"><el-input :model-value="detail.role.code" disabled /></el-form-item>
            <el-form-item label="角色名称"><div class="inline-field"><el-input v-model="roleName" :disabled="!editable||busy" maxlength="100" /><el-button :disabled="!editable||busy" @click="saveName">保存名称</el-button></div></el-form-item>
            <el-form-item label="操作权限"><el-select v-model="permissionCodes" multiple filterable collapse-tags :disabled="!editable||busy" style="width:100%" placeholder="选择权限代码"><el-option v-for="code in catalog?.permissionCodes || []" :key="code" :label="code" :value="code" /></el-select></el-form-item>
            <el-form-item label="可见菜单"><el-select v-model="menuCodes" multiple filterable collapse-tags :disabled="!editable||busy" style="width:100%" placeholder="选择菜单代码"><el-option v-for="code in catalog?.menuCodes || []" :key="code" :label="code" :value="code" /></el-select></el-form-item>
            <div class="section-title">数据范围 <el-button link type="primary" :disabled="!editable||busy" @click="scopes.push({resourceCode:'',scopeType:'NONE',organizationIds:''})">新增资源</el-button></div>
            <div v-for="(scope,index) in scopes" :key="index" class="scope-row">
              <el-select v-model="scope.resourceCode" filterable :disabled="!editable||busy" placeholder="资源代码"><el-option v-for="code in catalog?.resourceCodes || []" :key="code" :label="code" :value="code" /></el-select>
              <el-select v-model="scope.scopeType" :disabled="!editable||busy" placeholder="范围类型"><el-option v-for="type in scopeTypes" :key="type" :label="type" :value="type" /></el-select>
              <el-input v-model="scope.organizationIds" :disabled="!editable||busy||scope.scopeType!=='ORGANIZATION_SET'" placeholder="组织 ID，逗号分隔" />
              <el-button :disabled="!editable||busy" @click="scopes.splice(index,1)">移除</el-button>
            </div>
            <p class="help">数据范围为授权原始配置；实际可见数据仍由各业务接口复核。组织集合需要填写当前租户有效组织 ID。</p>
            <el-button type="primary" :loading="busy" :disabled="!editable||scopes.length===0" @click="saveGrants">保存授权</el-button>
          </el-form>
        </template>
        <el-empty v-else description="请选择角色查看授权" />
      </el-card>
    </div>
    <el-dialog v-model="showCreate" title="新建租户角色" width="480px"><el-form label-position="top"><el-form-item label="角色编码"><el-input v-model="createForm.code" placeholder="例如 QUALITY_REVIEWER" /></el-form-item><el-form-item label="角色名称"><el-input v-model="createForm.name" maxlength="100" /></el-form-item></el-form><template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" :loading="busy" @click="createRole">创建</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.role-page{padding:24px;max-width:1600px;margin:auto}.page-heading{display:flex;justify-content:space-between;gap:20px;align-items:start;margin-bottom:20px}.page-heading h1{margin:0 0 6px;font-size:24px}.page-heading p,.help{color:#657580;font-size:13px}.role-layout{display:grid;grid-template-columns:minmax(330px,40%) minmax(500px,1fr);gap:16px}.role-form{padding-top:12px}.inline-field{display:flex;width:100%;gap:8px}.inline-field .el-input{flex:1}.section-title{display:flex;align-items:center;gap:12px;font-weight:600;margin:8px 0}.scope-row{display:grid;grid-template-columns:1.2fr 1fr 1.2fr auto;gap:8px;margin-bottom:8px}@media(max-width:1000px){.role-layout{grid-template-columns:1fr}.scope-row{grid-template-columns:1fr 1fr}.page-heading{flex-wrap:wrap}}
</style>

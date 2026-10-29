<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';
type Command = components['schemas']['BatchAssignAccessUserRoles'];
const props = defineProps<{ users: components['schemas']['AccessUser'][]; roles: components['schemas']['Role'][]; busy: boolean }>();
const emit = defineEmits<{ changed: [] }>();
const session = useSessionStore();
const open = ref(false), previewing = ref(false), saving = ref(false);
const targets = ref<Command['targets']>([]), names = ref<string[]>([]), roleIds = ref<string[]>([]);
const preview = ref<components['schemas']['AccessBatchRolePreview'] | null>(null);
const pending = ref<{ key: string; body: Command } | null>(null);
const error = ref('');
let generation = 0;
const command = (): Command => ({ targets: targets.value.map(row => ({ ...row })), roleIds: [...roleIds.value].sort() });
const roleName = (id: string) => props.roles.find(role => role.id === id)?.name ?? id;
const blockers: Record<string, string> = { NO_CHANGE: '角色未变化', SELF_TARGET: '不能批量调整本人角色', LAST_ADMIN: '需保留最后一名有效管理员', ADMIN_GRANT_REQUIRES_ADMIN: '管理员角色只能由租户管理员调整' };
const ready = computed(() => !!preview.value?.canApply && !previewing.value && roleIds.value.length > 0);
watch(roleIds, () => { generation++; previewing.value = false; preview.value = null; error.value = ''; }, { deep: true });
function begin() {
  if (!pending.value) {
    if (!props.users.length || props.users.length > 100) { ElMessage.warning('请选择 1–100 个账号'); return; }
    targets.value = props.users.map(user => ({ id: user.id, version: user.version }));
    names.value = props.users.map(user => user.username);
    roleIds.value = []; preview.value = null; error.value = ''; generation++;
  }
  open.value = true;
}
async function inspect() {
  if (!roleIds.value.length) { error.value = '至少选择一个目标角色'; return; }
  const ticket = ++generation, body = command();
  previewing.value = true; preview.value = null; error.value = '';
  try {
    const result = await session.client.POST('/access/users/batch-role-impact', { body });
    if (ticket !== generation) return;
    if (result.error) { error.value = result.response.status === 409 ? '账号版本已变化，请关闭后刷新列表并重新选择。' : result.error.error.message; return; }
    preview.value = result.data.data;
  } catch { if (ticket === generation) error.value = '预览加载失败，保留当前选择，可重试。'; }
  finally { if (ticket === generation) previewing.value = false; }
}
async function save() {
  if (!pending.value) {
    if (!ready.value) return;
    const body = command();
    try { await ElMessageBox.confirm(`将 ${names.value.join('、')} 的角色替换为 ${body.roleIds.map(roleName).join('、')}。实际变更 ${preview.value!.changeCount} 个账号；旧会话将失效。相同角色账号保持不变。`, '确认批量调整角色', { type: 'warning' }); }
    catch { return; }
    if (!ready.value || JSON.stringify(body) !== JSON.stringify(command())) { error.value = '选择已变化，请重新预览。'; return; }
    pending.value = { key: crypto.randomUUID(), body };
  }
  saving.value = true; error.value = '';
  const request = pending.value;
  try {
    const result = await session.client.PUT('/access/users/batch-roles', { params: { header: { 'Idempotency-Key': request.key } }, body: request.body });
    if (result.error) {
      if (result.response.status >= 500) error.value = '结果尚未确认，原请求已保留，请重试同一请求或稍后核对。';
      else { pending.value = null; preview.value = null; error.value = `${result.error.error.message}。请关闭后刷新列表核对并重新选择。`; }
      return;
    }
    pending.value = null; open.value = false;
    ElMessage.success(`本请求已确认：变更 ${result.data.data.changes.length} 个账号，未变更 ${result.data.data.unchangedUserIds.length} 个。`);
    emit('changed');
  } catch { error.value = '结果不确定，原请求已保留；重试将使用同一幂等键。'; }
  finally { saving.value = false; }
}
</script>

<template>
  <el-button :disabled="busy || (!pending && (!users.length || users.length > 100))" @click="begin">{{ pending ? '重试批量角色请求' : '批量调整角色' }}</el-button>
  <el-dialog v-model="open" title="批量调整账号角色" width="min(960px, calc(100vw - 24px))" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
    <el-alert title="替换角色，不是追加。预览是当前时点信息；提交重新检查所有版本及权限，任一失败整批回滚。数据范围为角色配置，不代表最终可见数据。" type="warning" :closable="false" />
    <p class="target-list">本次账号（版本已固定）：{{ names.join('、') }}</p>
    <el-form label-position="top"><el-form-item label="替换为以下角色" :error="!roleIds.length && error ? error : ''">
      <el-select v-model="roleIds" multiple filterable aria-label="批量目标角色" :disabled="saving || !!pending" style="width:100%">
        <el-option v-for="role in roles.filter(item => item.status === 'ACTIVE')" :key="role.id" :value="role.id" :label="role.name" />
      </el-select>
    </el-form-item></el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <section v-if="preview" aria-label="批量角色影响预览" class="batch-preview">
      <p>将变更 {{ preview.changeCount }} 个账号，{{ preview.items.length - preview.changeCount }} 个保持不变。</p>
      <el-alert v-if="!preview.canApply" :title="preview.blockers.map(code => blockers[code] ?? code).join('；')" type="error" :closable="false" />
      <el-collapse><el-collapse-item v-for="item in preview.items" :key="item.userId" :name="item.userId" :title="`${item.username}：${item.blockers.includes('NO_CHANGE') ? '保持不变' : '查看权限变化'}`">
        <p>当前角色：{{ item.currentRoleIds.map(roleName).join('、') || '无' }}；拟定角色：{{ item.proposedRoleIds.map(roleName).join('、') }}</p>
        <p>新增权限：{{ item.addedPermissions.join('、') || '无' }}</p><p>移除权限：{{ item.removedPermissions.join('、') || '无' }}</p>
        <p>新增菜单：{{ item.addedMenus.join('、') || '无' }}；移除菜单：{{ item.removedMenus.join('、') || '无' }}</p>
        <p>当前范围配置：{{ item.currentScopeGrants.map(scope => `${roleName(scope.roleId)} / ${scope.resourceCode} / ${scope.scopeType} / ${scope.organizationIds.join('、')}`).join('；') || '无' }}</p>
        <p>拟定范围配置：{{ item.proposedScopeGrants.map(scope => `${roleName(scope.roleId)} / ${scope.resourceCode} / ${scope.scopeType} / ${scope.organizationIds.join('、')}`).join('；') || '无' }}</p>
      </el-collapse-item></el-collapse>
    </section>
    <template #footer><el-button :disabled="saving" @click="open=false">关闭</el-button><el-button :loading="previewing" :disabled="!!pending || saving" @click="inspect">预览整批影响</el-button><el-button type="primary" :loading="saving" :disabled="!pending && !ready" @click="save">{{ pending ? '重试同一角色请求' : '确认整批调整' }}</el-button></template>
  </el-dialog>
</template>

<style scoped>
.target-list, .batch-preview { overflow-wrap: anywhere; line-height: 1.6; }
.batch-preview { margin-block: 16px; }
</style>

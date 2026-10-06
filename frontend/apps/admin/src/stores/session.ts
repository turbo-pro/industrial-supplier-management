import { computed, markRaw, ref } from 'vue';
import { defineStore } from 'pinia';
import { createIsmClient, type components } from '@ism/api-client';

const TOKEN_KEY = 'ism.admin.accessToken';
const ACTOR_KEY = 'ism.admin.actorId';
const REQUIRED_KEY = 'ism.admin.passwordChangeRequired';

export const useSessionStore = defineStore('session', () => {
  const accessToken = ref<string | undefined>(sessionStorage.getItem(TOKEN_KEY) ?? undefined);
  const actorId = ref<string | undefined>(sessionStorage.getItem(ACTOR_KEY) ?? undefined);
  const passwordChangeRequired = ref(sessionStorage.getItem(REQUIRED_KEY) === 'true');
  const passwordChangeRecommended = ref(false);
  const organizations = ref<components['schemas']['OrganizationNode'][]>([]);
  const currentOrganization = ref<components['schemas']['CurrentOrganization']>();
  const menus = ref<components['schemas']['MenuNode'][]>([]);
  const client = markRaw(createIsmClient({ getAccessToken: () => accessToken.value }));
  const organizationOptions = computed(() => {
    const result: { id: string; label: string }[] = [];
    function walk(nodes: components['schemas']['OrganizationNode'][], depth = 0) {
      nodes.forEach(n => {
        if (n.status === 'ACTIVE') result.push({ id: n.id, label: `${'—'.repeat(depth)}${n.name}` });
        walk(n.children, depth + 1);
      });
    }
    walk(organizations.value);
    return result;
  });

  async function login(form: { tenantCode: string; username: string; password: string }) {
    const { data, error } = await client.POST('/auth/login', { body: { ...form, deviceId: crypto.randomUUID() } });
    if (error) throw new Error(error.error.message);
    accessToken.value = data.data.accessToken;
    actorId.value = data.data.user.id;
    passwordChangeRequired.value = data.data.user.passwordChangeRequired;
    passwordChangeRecommended.value = data.data.user.passwordChangeRecommended;
    sessionStorage.setItem(TOKEN_KEY, data.data.accessToken);
    sessionStorage.setItem(ACTOR_KEY, data.data.user.id);
    sessionStorage.setItem(REQUIRED_KEY, String(passwordChangeRequired.value));
    if (!passwordChangeRequired.value) await loadWorkspace();
  }

  async function loadWorkspace() {
    if (!accessToken.value || passwordChangeRequired.value) return;
    const [tree, current, navigation] = await Promise.all([
      client.GET('/organizations/tree'), client.GET('/organizations/current'), client.GET('/navigation/menus'),
    ]);
    if (tree.error || current.error || navigation.error) {
      logout();
      throw new Error('登录状态已失效');
    }
    organizations.value = tree.data.data;
    currentOrganization.value = current.data.data;
    menus.value = navigation.data.data;
  }

  async function changePassword(oldPassword: string, newPassword: string) {
    const { error } = await client.POST('/auth/change-password', { body: { oldPassword, newPassword } });
    if (error) throw new Error(error.error.message);
    // 改密会撤销当前会话，必须重新登录。
    logout();
  }

  async function switchOrganization(id: string) {
    const { data, error } = await client.POST('/organizations/switch', { body: { organizationId: id } });
    if (error) throw new Error(error.error.message);
    currentOrganization.value = data.data;
  }

  function logout() {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(ACTOR_KEY);
    sessionStorage.removeItem(REQUIRED_KEY);
    accessToken.value = undefined;
    actorId.value = undefined;
    passwordChangeRequired.value = false;
    passwordChangeRecommended.value = false;
    organizations.value = [];
    menus.value = [];
    currentOrganization.value = undefined;
  }

  if (accessToken.value) void loadWorkspace().catch(() => undefined);
  return { accessToken, actorId, passwordChangeRequired, passwordChangeRecommended,
    organizations, currentOrganization, menus, organizationOptions, client,
    login, loadWorkspace, changePassword, switchOrganization, logout };
});

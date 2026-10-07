<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type BrandingKey = 'branding.systemName' | 'branding.logoUrl' | 'branding.faviconUrl' | 'branding.footerText';
type Field = { key: BrandingKey; label: string; hint: string; setting?: components['schemas']['TenantSetting']; value: string };
const session = useSessionStore();
const loading = ref(false);
const saving = ref<BrandingKey>();
const fields = ref<Field[]>([
  { key: 'branding.systemName', label: '系统名称', hint: '显示在管理端标题和导航栏', value: '' },
  { key: 'branding.logoUrl', label: 'Logo 地址', hint: '支持站内相对地址或 HTTP(S) 图片地址；留空显示 ISM 标识', value: '' },
  { key: 'branding.faviconUrl', label: '浏览器图标地址', hint: '支持站内相对地址或 HTTP(S) 图片地址；留空使用默认图标', value: '' },
  { key: 'branding.footerText', label: '页脚文字', hint: '仅作为纯文本显示；留空则不显示页脚', value: '' },
]);

async function load() {
  loading.value = true;
  try {
    const { data, error } = await session.client.GET('/configuration/settings');
    if (error) throw new Error(error.error.message);
    for (const field of fields.value) {
      field.setting = data.data.find(item => item.key === field.key);
      field.value = field.setting?.value ?? '';
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '品牌配置加载失败');
  } finally {
    loading.value = false;
  }
}

function validUrl(value: string) {
  if (!value) return true;
  if (value.startsWith('/') && !value.startsWith('//')) return true;
  try { return ['http:', 'https:'].includes(new URL(value).protocol); } catch { return false; }
}

async function save(field: Field) {
  const maxLength = field.key === 'branding.systemName' ? 100 : field.key === 'branding.footerText' ? 300 : 2000;
  if (!field.setting || !field.value.trim() && field.key === 'branding.systemName' || field.value.length > maxLength ||
      ((field.key === 'branding.logoUrl' || field.key === 'branding.faviconUrl') && !validUrl(field.value))) {
    ElMessage.warning('请输入有效的品牌配置或图片地址');
    return;
  }
  saving.value = field.key;
  try {
    const { data, error, response } = await session.client.PUT('/configuration/settings/{settingKey}', {
      params: { path: { settingKey: field.key }, header: { 'Idempotency-Key': crypto.randomUUID() } },
      body: { value: field.value, version: field.setting.version },
    });
    if (error) {
      ElMessage.error(response.status === 409 ? '配置已被其他人修改，请刷新后重试' : error.error.message);
      if (response.status === 409) await load();
      return;
    }
    field.setting = data.data;
    field.value = data.data.value;
    await session.loadBranding();
    ElMessage.success(`${field.label}已保存`);
  } catch {
    ElMessage.error('保存结果不确定，请刷新核对');
  } finally {
    saving.value = undefined;
  }
}

onMounted(load);
</script>

<template>
  <el-card v-loading="loading" shadow="never">
    <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span>租户品牌</span><el-button :disabled="!!saving" @click="load">刷新</el-button></div></template>
    <p>仅影响当前租户。每项单独保存，修改后当前页面立即更新；其他登录会话刷新页面后生效。</p>
    <el-form label-position="top">
      <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
        <div style="width:100%"><div style="display:flex;gap:8px"><el-input v-model="field.value" :maxlength="field.key==='branding.systemName'?100:field.key==='branding.footerText'?300:2000" :disabled="!field.setting||!!saving" :aria-label="field.label"/><el-button type="primary" :loading="saving===field.key" :disabled="!field.setting||!!saving" @click="save(field)">保存</el-button></div><small>{{field.hint}}</small></div>
      </el-form-item>
    </el-form>
    <img v-if="fields[1]?.value&&validUrl(fields[1].value)" :src="fields[1].value" alt="Logo 预览" style="max-width:180px;max-height:72px;object-fit:contain"/>
  </el-card>
</template>

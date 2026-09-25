<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type Attendance = components['schemas']['SafetyAttendance'];
type Person = components['schemas']['SupplierPerson'];
const session = useSessionStore();
const loading = ref(false);
const dialog = ref(false);
const saving = ref(false);
const rows = ref<Attendance[]>([]);
const persons = ref<Person[]>([]);
const total = ref(0);
const query = reactive({ personId: '', openOnly: false, page: 0, size: 20 });
const form = reactive({ personId: '', siteName: '' });

async function load() {
  loading.value = true;
  try {
    const { data, error } = await session.client.GET('/safety/attendance', {
      params: { query: { personId: query.personId || undefined, openOnly: query.openOnly,
        page: query.page, size: query.size } }
    });
    if (error) { ElMessage.error(error.error.message); return; }
    rows.value = data?.data?.items ?? [];
    total.value = data?.data?.total ?? 0;
  } finally { loading.value = false; }
}

async function searchPersons(keyword: string) {
  const { data } = await session.client.GET('/resources/persons', {
    params: { query: { keyword: keyword || undefined, page: 0, size: 100 } }
  });
  persons.value = data?.data?.items ?? [];
}

async function open() {
  Object.assign(form, { personId: '', siteName: '' });
  await searchPersons('');
  dialog.value = true;
}

async function checkIn() {
  if (!form.personId || !form.siteName.trim()) { ElMessage.warning('请选择人员并填写现场位置'); return; }
  saving.value = true;
  try {
    const { error } = await session.client.POST('/safety/attendance/check-in', {
      body: { personId: form.personId, siteName: form.siteName.trim() }
    });
    if (error) { ElMessage.error(error.error.message); return; }
    dialog.value = false;
    ElMessage.success('签到成功');
    await load();
  } finally { saving.value = false; }
}

async function checkOut(row: Attendance) {
  let note = '';
  try {
    note = (await ElMessageBox.prompt('可填写签退说明', `签退 ${row.personName}`,
      { inputValue: '', inputValidator: value => value.length <= 500 || '说明不能超过 500 字' })).value;
  } catch { return; }
  const { error } = await session.client.POST('/safety/attendance/{id}/check-out', {
    params: { path: { id: row.id } }, body: { note, version: row.version }
  });
  if (error) { ElMessage.error(error.error.message); return; }
  ElMessage.success('签退成功');
  await load();
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-heading">
      <div><h1>现场出入</h1><p>签到时实时校验人员状态、培训及特种作业凭证</p></div>
      <el-button type="primary" @click="open">人员签到</el-button>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="人员 ID"><el-input v-model="query.personId" clearable /></el-form-item>
        <el-form-item><el-checkbox v-model="query.openOnly">仅看在场</el-checkbox></el-form-item>
        <el-button type="primary" @click="query.page=0;load()">查询</el-button>
      </el-form>
    </el-card>
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="人员" min-width="160"><template #default="{row}">{{ row.personName }}<div class="subtext">{{ row.personCode }}</div></template></el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="160" />
        <el-table-column prop="projectName" label="项目" min-width="160" />
        <el-table-column prop="siteName" label="现场位置" min-width="150" />
        <el-table-column prop="checkInAt" label="签到时间" min-width="170" />
        <el-table-column prop="checkOutAt" label="签退时间" min-width="170" />
        <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.checkOutAt?'info':'success'">{{row.checkOutAt?'已签退':'在场'}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{row}"><el-button v-if="!row.checkOutAt" link type="primary" @click="checkOut(row)">签退</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination :current-page="query.page+1" :total="total" :page-size="query.size" layout="total, prev, pager, next" @current-change="(page: number) => {query.page=page-1;load()}" /></div>
    </el-card>
    <el-dialog v-model="dialog" title="人员签到" width="540px">
      <el-form label-position="top">
        <el-form-item label="人员 *"><el-select v-model="form.personId" filterable remote :remote-method="searchPersons" style="width:100%" placeholder="搜索人员编号或姓名"><el-option v-for="p in persons" :key="p.id" :label="`${p.name} · ${p.code}`" :value="p.id" /></el-select></el-form-item>
        <el-form-item label="现场位置 *"><el-input v-model="form.siteName" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="checkIn">签到</el-button></template>
    </el-dialog>
  </div>
</template>

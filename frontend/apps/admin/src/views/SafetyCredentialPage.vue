<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, Search } from '@element-plus/icons-vue';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type Credential = components['schemas']['SafetyCredential'];
type Person = components['schemas']['SupplierPerson'];
type Eligibility = components['schemas']['SafetyEligibility'];
type Kind = components['schemas']['SafetyCredentialKind'];
type WorkType = components['schemas']['SpecialWorkType'];
type Decision = components['schemas']['ReviewSafetyCredential']['decision'];

const session = useSessionStore();
const route = useRoute();
const loading = ref(false);
const saving = ref(false);
const dialog = ref(false);
const items = ref<Credential[]>([]);
const persons = ref<Person[]>([]);
const eligibility = ref<Eligibility | null>(null);
const total = ref(0);
const filter = reactive({ personId: '', kind: '' as '' | Kind, status: '', page: 0, size: 20 });
const form = reactive({ credentialNo: '', personId: '', kind: 'TRAINING' as Kind,
  workType: '' as '' | WorkType, title: '', examScore: undefined as number | undefined,
  passed: true, effectiveDate: '', expiryDate: '', fileId: '' });

const kinds = { TRAINING: '安全培训', SPECIAL_WORK: '特种作业凭证' };
const states = { PENDING: '待审核', VERIFIED: '已审核', REJECTED: '已驳回', REVOKED: '已撤销' };
const workTypes = { ELECTRICAL: '电工作业', WELDING: '焊接作业',
  WORK_AT_HEIGHT: '高处作业', OTHER: '其他' };
const selectedPerson = computed(() => persons.value.find(p => p.id === form.personId));

async function load() {
  loading.value = true;
  try {
    const { data, error } = await session.client.GET('/safety/credentials', {
      params: { query: { personId: filter.personId || undefined, kind: filter.kind || undefined,
        status: (filter.status || undefined) as components['schemas']['SafetyCredentialStatus'] | undefined,
        page: filter.page, size: filter.size } }
    });
    if (error) { ElMessage.error(error.error.message); return; }
    items.value = data?.data?.items ?? [];
    total.value = data?.data?.total ?? 0;
  } finally { loading.value = false; }
}

async function searchPersons(keyword: string) {
  const { data } = await session.client.GET('/resources/persons', {
    params: { query: { keyword: keyword || undefined, page: 0, size: 100 } }
  });
  persons.value = data?.data?.items ?? [];
}

async function checkEligibility(personId: string) {
  const { data, error } = await session.client.GET('/safety/persons/{personId}/eligibility', {
    params: { path: { personId } }
  });
  if (error) { ElMessage.error(error.error.message); return; }
  eligibility.value = data?.data ?? null;
}

async function openCreate() {
  await searchPersons('');
  Object.assign(form, { credentialNo: '', personId: filter.personId, kind: 'TRAINING',
    workType: '', title: '', examScore: undefined, passed: true,
    effectiveDate: '', expiryDate: '', fileId: '' });
  dialog.value = true;
}

async function save() {
  if (!form.credentialNo || !form.personId || !form.title || !form.effectiveDate
      || !form.expiryDate || !form.fileId || (form.kind === 'SPECIAL_WORK' && !form.workType)) {
    ElMessage.warning('请填写凭证编号、人员、名称、有效期和证据文件'); return;
  }
  saving.value = true;
  try {
    const { error } = await session.client.POST('/safety/credentials', {
      body: { credentialNo: form.credentialNo, personId: form.personId, kind: form.kind,
        workType: form.kind === 'SPECIAL_WORK' ? form.workType as WorkType : undefined,
        title: form.title, examScore: form.kind === 'TRAINING' ? form.examScore : undefined,
        passed: form.kind === 'TRAINING' ? form.passed : undefined,
        effectiveDate: form.effectiveDate, expiryDate: form.expiryDate, fileId: form.fileId }
    });
    if (error) { ElMessage.error(error.error.message); return; }
    dialog.value = false;
    ElMessage.success('凭证已登记，等待审核');
    await load();
  } finally { saving.value = false; }
}

async function review(row: Credential, decision: Decision) {
  let comment: string | undefined;
  try {
    if (decision === 'APPROVE') {
      await ElMessageBox.confirm(`确认审核通过“${row.title}”？`, '凭证审核');
    } else {
      comment = (await ElMessageBox.prompt('请输入原因', decision === 'REVOKE' ? '撤销凭证' : '驳回凭证',
        { inputPattern: /\S+/ })).value;
    }
  } catch { return; }
  const { error } = await session.client.POST('/safety/credentials/{id}/review', {
    params: { path: { id: row.id } }, body: { decision, comment, version: row.version }
  });
  if (error) { ElMessage.error(error.error.message); return; }
  ElMessage.success('审核结果已保存');
  await load();
  if (eligibility.value?.personId === row.personId) await checkEligibility(row.personId);
}

function reset() { filter.personId = ''; filter.kind = ''; filter.status = ''; filter.page = 0; eligibility.value = null; void load(); }
function changePage(value: number) { filter.page = value - 1; void load(); }

watch(() => route.query.personId, value => {
  filter.personId = typeof value === 'string' ? value : '';
  filter.page = 0;
  if (filter.personId) void checkEligibility(filter.personId);
  void load();
});
onMounted(() => {
  filter.personId = typeof route.query.personId === 'string' ? route.query.personId : '';
  if (filter.personId) void checkEligibility(filter.personId);
  void load();
});
</script>

<template>
  <div class="page">
    <div class="page-heading">
      <div><h1>培训与作业凭证</h1><p>记录考试结果、审核有效凭证，并核对人员入场资格</p></div>
      <el-button type="primary" :icon="Plus" @click="openCreate">登记凭证</el-button>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="人员 ID"><el-input v-model="filter.personId" clearable :prefix-icon="Search" placeholder="输入人员 ID" /></el-form-item>
        <el-form-item label="类别"><el-select v-model="filter.kind" clearable style="width:150px"><el-option v-for="(name,key) in kinds" :key="key" :label="name" :value="key" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="filter.status" clearable style="width:150px"><el-option v-for="(name,key) in states" :key="key" :label="name" :value="key" /></el-select></el-form-item>
        <el-button type="primary" @click="filter.page=0;load()">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
        <el-button v-if="filter.personId" @click="checkEligibility(filter.personId)">核对入场资格</el-button>
      </el-form>
    </el-card>
    <el-alert v-if="eligibility" :closable="false" :type="eligibility.eligible ? 'success' : 'warning'" class="filter-card">
      <template #title>{{ eligibility.personName }}：{{ eligibility.eligible ? '凭证满足入场条件' : '暂不满足入场条件' }}</template>
      培训：{{ eligibility.trainingValid ? '有效' : '缺失或失效' }}；特种作业：{{ eligibility.specialWorkValid ? '符合要求' : '缺失或失效' }}；人员状态：{{ eligibility.personStatus }}
    </el-alert>
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="items">
        <el-table-column prop="credentialNo" label="凭证编号" width="155" />
        <el-table-column label="人员/凭证" min-width="250"><template #default="{row}"><strong>{{ row.personName }} · {{ row.title }}</strong><div class="subtext">{{ row.personCode }} · {{ kinds[row.kind as keyof typeof kinds] }}</div></template></el-table-column>
        <el-table-column label="作业类型" width="120"><template #default="{row}">{{ row.workType ? workTypes[row.workType as keyof typeof workTypes] : '—' }}</template></el-table-column>
        <el-table-column label="有效期" width="220"><template #default="{row}">{{ row.effectiveDate }} 至 {{ row.expiryDate }}</template></el-table-column>
        <el-table-column label="状态" width="125"><template #default="{row}"><el-tag :type="row.currentlyValid ? 'success' : 'warning'">{{ states[row.status as keyof typeof states] }}{{ row.status==='VERIFIED'&&!row.currentlyValid ? '（失效）' : '' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="210"><template #default="{row}">
          <template v-if="row.status==='PENDING'"><el-button link type="success" @click="review(row,'APPROVE')">通过</el-button><el-button link type="danger" @click="review(row,'REJECT')">驳回</el-button></template>
          <el-button v-if="row.status==='VERIFIED'" link type="danger" @click="review(row,'REVOKE')">撤销</el-button>
          <el-button link type="primary" @click="checkEligibility(row.personId)">资格</el-button>
        </template></el-table-column>
      </el-table>
      <el-pagination class="pagination" :current-page="filter.page+1" :page-size="filter.size" :total="total" layout="total, prev, pager, next" @current-change="changePage" />
    </el-card>
    <el-dialog v-model="dialog" title="登记人员安全凭证" width="680px">
      <el-form label-position="top"><el-row :gutter="18">
        <el-col :span="12"><el-form-item label="凭证编号 *"><el-input v-model="form.credentialNo" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="人员 *"><el-select v-model="form.personId" filterable remote :remote-method="searchPersons" placeholder="搜索姓名或人员编码" style="width:100%"><el-option v-for="person in persons" :key="person.id" :label="`${person.name} · ${person.code}`" :value="person.id" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="凭证类别 *"><el-select v-model="form.kind" style="width:100%"><el-option label="安全培训" value="TRAINING" /><el-option label="特种作业凭证" value="SPECIAL_WORK" /></el-select></el-form-item></el-col>
        <el-col v-if="form.kind==='SPECIAL_WORK'" :span="12"><el-form-item label="作业类型 *"><el-select v-model="form.workType" style="width:100%"><el-option v-for="(name,key) in workTypes" :key="key" :label="name" :value="key" /></el-select></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="课程或证书名称 *"><el-input v-model="form.title" /></el-form-item></el-col>
        <template v-if="form.kind==='TRAINING'"><el-col :span="12"><el-form-item label="考试结果 *"><el-switch v-model="form.passed" active-text="通过" inactive-text="未通过" /></el-form-item></el-col><el-col :span="12"><el-form-item label="考试分数"><el-input-number v-model="form.examScore" :min="0" :max="100" :precision="2" /></el-form-item></el-col></template>
        <el-col :span="12"><el-form-item label="生效日期 *"><el-date-picker v-model="form.effectiveDate" value-format="YYYY-MM-DD" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="到期日期 *"><el-date-picker v-model="form.expiryDate" value-format="YYYY-MM-DD" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="证据文件 ID *"><el-input v-model="form.fileId" /></el-form-item></el-col>
        <el-col v-if="selectedPerson?.specialWorkType" :span="12"><el-form-item label="人员登记作业类型"><el-input :model-value="workTypes[selectedPerson.specialWorkType as keyof typeof workTypes]" disabled /></el-form-item></el-col>
      </el-row></el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">登记待审核</el-button></template>
    </el-dialog>
  </div>
</template>

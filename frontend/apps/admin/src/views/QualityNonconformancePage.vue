<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type Ncr = components['schemas']['QualityNcr'];
type Project = components['schemas']['ProjectSummary'];
type Event = components['schemas']['QualityNcrEvent'];
const session = useSessionStore();
const rows = ref<Ncr[]>([]);
const projects = ref<Project[]>([]);
const events = ref<Event[]>([]);
const loading = ref(false);
const saving = ref(false);
const createDialog = ref(false);
const actionDialog = ref(false);
const eventsDialog = ref(false);
const selected = ref<Ncr | null>(null);
const total = ref(0);
const filter = reactive({ keyword: '', status: '' as '' | components['schemas']['QualityNcrStatus'], page: 0, size: 20 });
const form = reactive({ ncrNo: '', projectId: '', title: '', category: 'MATERIAL' as components['schemas']['QualityNcrCategory'],
  severity: 'MEDIUM' as components['schemas']['QualityNcrSeverity'], description: '', inspectionDate: '',
  inspectedQuantity: 1, defectiveQuantity: 1, unit: '件', evidenceFileId: '', deadline: '', responsibleUserId: '' });
const action = reactive({ rootCause: '', correction: '', preventiveAction: '', fileId: '' });
const states = { OPEN: '待整改', PENDING_REVIEW: '待复验', CLOSED: '已关闭' };
const severities = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '重大' };
const categories = { MATERIAL: '来料', PROCESS: '过程', DELIVERY: '交付', DOCUMENT: '文件', OTHER: '其他' };

async function load() {
  loading.value = true;
  try {
    const { data, error } = await session.client.GET('/quality/nonconformances', {
      params: { query: { keyword: filter.keyword || undefined, status: filter.status || undefined,
        page: filter.page, size: filter.size } }
    });
    if (error) { ElMessage.error(error.error.message); return; }
    rows.value = data?.data?.items ?? [];
    total.value = data?.data?.total ?? 0;
  } finally { loading.value = false; }
}

async function openCreate() {
  const { data } = await session.client.GET('/projects', { params: { query: { status: 'ACTIVE', page: 0, size: 100 } } });
  projects.value = data?.data?.items ?? [];
  Object.assign(form, { ncrNo: '', projectId: '', title: '', category: 'MATERIAL', severity: 'MEDIUM',
    description: '', inspectionDate: '', inspectedQuantity: 1, defectiveQuantity: 1,
    unit: '件', evidenceFileId: '', deadline: '', responsibleUserId: session.actorId ?? '' });
  createDialog.value = true;
}

async function create() {
  if (!form.ncrNo || !form.projectId || !form.title || !form.description || !form.inspectionDate
      || !form.deadline || !form.evidenceFileId || !form.responsibleUserId) {
    ElMessage.warning('请填写全部必填项'); return;
  }
  saving.value = true;
  try {
    const { error } = await session.client.POST('/quality/nonconformances', { body: { ...form } });
    if (error) { ElMessage.error(error.error.message); return; }
    createDialog.value = false;
    ElMessage.success('已登记不符合项');
    await load();
  } finally { saving.value = false; }
}

function openAction(row: Ncr) {
  selected.value = row;
  Object.assign(action, { rootCause: row.rootCause || '', correction: row.correction || '',
    preventiveAction: row.preventiveAction || '', fileId: row.actionFileId || '' });
  actionDialog.value = true;
}

async function submitAction() {
  if (!selected.value || !action.rootCause.trim() || !action.correction.trim()
      || !action.preventiveAction.trim() || !action.fileId) { ElMessage.warning('请完整填写原因、措施和证据文件'); return; }
  saving.value = true;
  try {
    const { error } = await session.client.POST('/quality/nonconformances/{id}/rectification', {
      params: { path: { id: selected.value.id } }, body: { ...action, version: selected.value.version }
    });
    if (error) { ElMessage.error(error.error.message); return; }
    actionDialog.value = false;
    ElMessage.success('纠正措施已提交复验');
    await load();
  } finally { saving.value = false; }
}

async function verify(row: Ncr, decision: 'PASS' | 'REJECT') {
  let comment = '';
  try { comment = (await ElMessageBox.prompt('请输入复验结论', decision === 'PASS' ? '复验通过' : '驳回复验',
    { inputPattern: /\S+/, inputErrorMessage: '复验结论不能为空' })).value; }
  catch { return; }
  const { error } = await session.client.POST('/quality/nonconformances/{id}/verification', {
    params: { path: { id: row.id } }, body: { decision, comment, version: row.version }
  });
  if (error) { ElMessage.error(error.error.message); return; }
  ElMessage.success(decision === 'PASS' ? '复验通过，已关闭' : '已驳回，重新整改');
  await load();
}

async function showEvents(row: Ncr) {
  selected.value = row;
  const { data, error } = await session.client.GET('/quality/nonconformances/{id}/events', {
    params: { path: { id: row.id } }
  });
  if (error) { ElMessage.error(error.error.message); return; }
  events.value = data?.data ?? [];
  eventsDialog.value = true;
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-heading"><div><h1>质量不符合项</h1><p>验收登记、原因分析、纠正预防和复验关闭</p></div><el-button type="primary" @click="openCreate">登记不符合项</el-button></div>
    <el-card shadow="never" class="filter-card"><el-form inline>
      <el-form-item label="关键词"><el-input v-model="filter.keyword" clearable placeholder="编号、标题或供应商" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="filter.status" clearable style="width:140px"><el-option v-for="(label,key) in states" :key="key" :label="label" :value="key" /></el-select></el-form-item>
      <el-button type="primary" @click="filter.page=0;load()">查询</el-button>
    </el-form></el-card>
    <el-card shadow="never" class="table-card"><el-table v-loading="loading" :data="rows">
      <el-table-column prop="ncrNo" label="编号" width="150" />
      <el-table-column label="不符合项" min-width="230"><template #default="{row}"><strong>{{row.title}}</strong><div class="subtext">{{row.projectName}} · {{row.supplierName}}</div></template></el-table-column>
      <el-table-column label="等级" width="80"><template #default="{row}">{{severities[row.severity as keyof typeof severities]}}</template></el-table-column>
      <el-table-column label="缺陷/验收" width="140"><template #default="{row}">{{row.defectiveQuantity}} / {{row.inspectedQuantity}} {{row.unit}}</template></el-table-column>
      <el-table-column prop="deadline" label="整改期限" width="120" />
      <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.overdue?'danger':row.status==='CLOSED'?'success':'warning'">{{row.overdue?'已逾期':states[row.status as keyof typeof states]}}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="210"><template #default="{row}"><el-button v-if="row.status==='OPEN'" link type="primary" @click="openAction(row)">提交措施</el-button><template v-if="row.status==='PENDING_REVIEW'"><el-button link type="success" @click="verify(row,'PASS')">通过</el-button><el-button link type="danger" @click="verify(row,'REJECT')">驳回</el-button></template><el-button link @click="showEvents(row)">历程</el-button></template></el-table-column>
    </el-table><div class="pagination"><el-pagination :current-page="filter.page+1" :total="total" :page-size="filter.size" layout="total, prev, pager, next" @current-change="(page: number) => {filter.page=page-1;load()}" /></div></el-card>
    <el-dialog v-model="createDialog" title="登记质量不符合项" width="760px"><el-form label-position="top"><el-row :gutter="16">
      <el-col :span="12"><el-form-item label="编号 *"><el-input v-model="form.ncrNo" maxlength="64" /></el-form-item></el-col><el-col :span="12"><el-form-item label="标题 *"><el-input v-model="form.title" maxlength="200" /></el-form-item></el-col>
      <el-col :span="12"><el-form-item label="所属项目 *"><el-select v-model="form.projectId" style="width:100%"><el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" /></el-select></el-form-item></el-col>
      <el-col :span="6"><el-form-item label="类别"><el-select v-model="form.category"><el-option v-for="(label,key) in categories" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col>
      <el-col :span="6"><el-form-item label="等级"><el-select v-model="form.severity"><el-option v-for="(label,key) in severities" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col>
      <el-col :span="24"><el-form-item label="问题描述 *"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" /></el-form-item></el-col>
      <el-col :span="8"><el-form-item label="验收数量 *"><el-input-number v-model="form.inspectedQuantity" :min="0.001" :precision="3" /></el-form-item></el-col>
      <el-col :span="8"><el-form-item label="缺陷数量 *"><el-input-number v-model="form.defectiveQuantity" :min="0.001" :precision="3" /></el-form-item></el-col>
      <el-col :span="8"><el-form-item label="单位 *"><el-input v-model="form.unit" maxlength="32" /></el-form-item></el-col>
      <el-col :span="12"><el-form-item label="验收日期 *"><el-date-picker v-model="form.inspectionDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
      <el-col :span="12"><el-form-item label="整改期限 *"><el-date-picker v-model="form.deadline" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
      <el-col :span="12"><el-form-item label="验收证据文件 ID *"><el-input v-model="form.evidenceFileId" /></el-form-item></el-col>
      <el-col :span="12"><el-form-item label="责任人 ID *"><el-input v-model="form.responsibleUserId" /></el-form-item></el-col>
    </el-row></el-form><template #footer><el-button @click="createDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="create">登记</el-button></template></el-dialog>
    <el-dialog v-model="actionDialog" title="提交纠正预防措施" width="680px"><el-form label-position="top">
      <el-form-item label="根本原因 *"><el-input v-model="action.rootCause" type="textarea" :rows="2" maxlength="2000" /></el-form-item>
      <el-form-item label="纠正措施 *"><el-input v-model="action.correction" type="textarea" :rows="2" maxlength="2000" /></el-form-item>
      <el-form-item label="预防措施 *"><el-input v-model="action.preventiveAction" type="textarea" :rows="2" maxlength="2000" /></el-form-item>
      <el-form-item label="措施证据文件 ID *"><el-input v-model="action.fileId" /></el-form-item>
    </el-form><template #footer><el-button @click="actionDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitAction">提交复验</el-button></template></el-dialog>
    <el-dialog v-model="eventsDialog" :title="`${selected?.ncrNo ?? ''} · 处理历程`" width="700px"><el-timeline><el-timeline-item v-for="event in events" :key="event.id" :timestamp="event.createdAt"><strong>{{event.action}}</strong> · {{event.fromStatus || '新建'}} → {{event.toStatus}}<p style="white-space:pre-wrap">{{event.note}}</p><small>操作人 {{event.actorId}}<template v-if="event.fileId"> · 文件 {{event.fileId}}</template></small></el-timeline-item></el-timeline></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { components } from '@ism/api-client';
import { useSessionStore } from '../stores/session';

type Evaluation = components['schemas']['PerformanceEvaluation'];
type Supplier = components['schemas']['SupplierSummary'];
type Event = components['schemas']['PerformanceEvent'];
type Dimension = components['schemas']['PerformanceDimension'];
type Rule = components['schemas']['PerformanceRule'];
type Improvement = components['schemas']['ImprovementPlan'];
const session = useSessionStore();
const dimensions: Dimension[] = ['QUALITY', 'DELIVERY', 'SAFETY', 'SERVICE'];
const labels = { QUALITY: '质量', DELIVERY: '交付', SAFETY: '安全', SERVICE: '服务' };
const statuses = { DRAFT: '草稿', SUBMITTED: '待审核', APPROVED: '已批准', REJECTED: '已驳回' };
const rows = ref<Evaluation[]>([]);
const suppliers = ref<Supplier[]>([]);
const events = ref<Event[]>([]);
const current = ref<Evaluation | null>(null);
const improvement = ref<Improvement | null>(null);
const rule = ref<Rule | null>(null);
const total = ref(0);
const loading = ref(false);
const saving = ref(false);
const evaluationDialog = ref(false);
const ruleDialog = ref(false);
const detailDrawer = ref(false);
const filter = reactive({ supplierId: '', status: '' as '' | components['schemas']['PerformanceStatus'], page: 0, size: 20 });
const form = reactive({ id: '', supplierId: '', periodStart: '', periodEnd: '', version: 0,
  items: dimensions.map(dimension => ({ dimension, score: 80, comment: '', evidenceFileId: '' })) });
const ruleForm = reactive({ qualityWeight: 35, deliveryWeight: 25, safetyWeight: 25, serviceWeight: 15, version: 0 });

async function load() {
  loading.value = true;
  try {
    const { data, error } = await session.client.GET('/performance/evaluations', {
      params: { query: { supplierId: filter.supplierId || undefined, status: filter.status || undefined,
        page: filter.page, size: filter.size } }
    });
    if (error) { ElMessage.error(error.error.message); return; }
    rows.value = data?.data?.items ?? [];
    total.value = data?.data?.total ?? 0;
  } finally { loading.value = false; }
}
async function loadRule() {
  const { data, error } = await session.client.GET('/performance/rule');
  if (error) { ElMessage.error(error.error.message); return; }
  rule.value = data?.data ?? null;
}
async function searchSuppliers(keyword: string) {
  const { data } = await session.client.GET('/suppliers', {
    params: { query: { keyword: keyword || undefined, status: 'ACTIVE', page: 0, size: 100 } }
  });
  suppliers.value = data?.data?.items ?? [];
}
async function openCreate() {
  await Promise.all([loadRule(), searchSuppliers('')]);
  Object.assign(form, { id: '', supplierId: '', periodStart: '', periodEnd: '', version: 0,
    items: dimensions.map(dimension => ({ dimension, score: 80, comment: '', evidenceFileId: '' })) });
  evaluationDialog.value = true;
}
async function openEdit(row: Evaluation) {
  await loadRule();
  current.value = row;
  Object.assign(form, { id: row.id, supplierId: row.supplierId, periodStart: row.periodStart,
    periodEnd: row.periodEnd, version: row.version,
    items: dimensions.map(dimension => { const item = row.items.find(it => it.dimension === dimension);
      return { dimension, score: item?.score ?? 80, comment: item?.comment ?? '', evidenceFileId: item?.evidenceFileId ?? '' }; }) });
  evaluationDialog.value = true;
}
function shownWeight(dimension: Dimension) {
  if (form.id) return current.value?.items.find(item => item.dimension === dimension)?.weight ?? 0;
  if (!rule.value) return 0;
  return { QUALITY: rule.value.qualityWeight, DELIVERY: rule.value.deliveryWeight,
    SAFETY: rule.value.safetyWeight, SERVICE: rule.value.serviceWeight }[dimension];
}
async function save() {
  if (!form.supplierId || !form.periodStart || !form.periodEnd
      || form.items.some(item => !item.comment.trim() || !item.evidenceFileId)) {
    ElMessage.warning('请填写供应商、周期和四项评分说明及证据文件'); return;
  }
  saving.value = true;
  try {
    const body = { supplierId: form.supplierId, periodStart: form.periodStart,
      periodEnd: form.periodEnd, version: form.version, items: form.items };
    const result = form.id
      ? await session.client.PUT('/performance/evaluations/{id}', { params: { path: { id: form.id } }, body })
      : await session.client.POST('/performance/evaluations', { body });
    if (result.error) { ElMessage.error(result.error.error.message); return; }
    evaluationDialog.value = false;
    ElMessage.success('评价草稿已保存');
    await load();
  } finally { saving.value = false; }
}
async function openRule() {
  await loadRule();
  if (!rule.value) return;
  Object.assign(ruleForm, { qualityWeight: rule.value.qualityWeight, deliveryWeight: rule.value.deliveryWeight,
    safetyWeight: rule.value.safetyWeight, serviceWeight: rule.value.serviceWeight, version: rule.value.version });
  ruleDialog.value = true;
}
async function saveRule() {
  if (ruleForm.qualityWeight + ruleForm.deliveryWeight + ruleForm.safetyWeight + ruleForm.serviceWeight !== 100) {
    ElMessage.warning('权重之和必须为 100'); return;
  }
  const { data, error } = await session.client.PUT('/performance/rule', { body: { ...ruleForm } });
  if (error) { ElMessage.error(error.error.message); return; }
  rule.value = data?.data ?? null;
  ruleDialog.value = false;
  ElMessage.success('租户评价权重已保存');
}
async function submit(row: Evaluation) {
  try { await ElMessageBox.confirm(`确认提交 ${row.supplierName} 的评价？`, '提交审核'); } catch { return; }
  const { error } = await session.client.POST('/performance/evaluations/{id}/submit', {
    params: { path: { id: row.id }, query: { version: row.version } }
  });
  if (error) { ElMessage.error(error.error.message); return; }
  ElMessage.success('已提交审核'); await load();
}
async function review(row: Evaluation, decision: 'APPROVE' | 'REJECT') {
  let comment = '';
  try {
    comment = (await ElMessageBox.prompt('填写审核意见', decision === 'APPROVE' ? '批准评价' : '驳回评价',
      { inputPattern: decision === 'REJECT' ? /\S+/ : undefined, inputErrorMessage: '驳回必须填写原因' })).value;
  } catch { return; }
  const { error } = await session.client.POST('/performance/evaluations/{id}/review', {
    params: { path: { id: row.id } }, body: { decision, comment, version: row.version }
  });
  if (error) { ElMessage.error(error.error.message); return; }
  ElMessage.success(decision === 'APPROVE' ? '评价已批准' : '评价已驳回'); await load();
}
async function showDetail(row: Evaluation) {
  const [history, plan] = await Promise.all([
    session.client.GET('/performance/evaluations/{id}/events', { params: { path: { id: row.id } } }),
    session.client.GET('/performance/evaluations/{evaluationId}/improvement', { params: { path: { evaluationId: row.id } } })
  ]);
  if (history.error || plan.error) { ElMessage.error(history.error?.error.message ?? plan.error?.error.message ?? '读取失败'); return; }
  events.value = history.data?.data ?? [];
  improvement.value = plan.data?.data ?? null;
  current.value = row;
  detailDrawer.value = true;
}
async function createImprovement() {
  if (!current.value) return;
  try {
    const rootCause = (await ElMessageBox.prompt('请输入根因分析', '创建改进计划', { inputPattern: /\S+/ })).value;
    const actionPlan = (await ElMessageBox.prompt('请输入改进措施', '创建改进计划', { inputPattern: /\S+/ })).value;
    const dueDate = (await ElMessageBox.prompt('请输入期限 YYYY-MM-DD', '创建改进计划', { inputPattern: /^\d{4}-\d{2}-\d{2}$/ })).value;
    const { data, error } = await session.client.POST('/performance/evaluations/{evaluationId}/improvement', {
      params: { path: { evaluationId: current.value.id } }, body: { rootCause, actionPlan, dueDate }
    });
    if (error) { ElMessage.error(error.error.message); return; }
    improvement.value = data?.data ?? null; ElMessage.success('改进计划已创建');
  } catch { /* user cancelled */ }
}
async function submitImprovement() {
  if (!current.value || !improvement.value) return;
  try {
    const completionNote = (await ElMessageBox.prompt('请输入完成情况', '提交改进结果', { inputPattern: /\S+/ })).value;
    const evidenceFileId = (await ElMessageBox.prompt('请输入已上传的证据文件 ID', '提交改进结果', { inputPattern: /^[1-9]\d*$/ })).value;
    const { data, error } = await session.client.POST('/performance/evaluations/{evaluationId}/improvement/submit', {
      params: { path: { evaluationId: current.value.id } }, body: { completionNote, evidenceFileId, version: improvement.value.version }
    });
    if (error) { ElMessage.error(error.error.message); return; }
    improvement.value = data?.data ?? null; ElMessage.success('改进结果已提交');
  } catch { /* user cancelled */ }
}
async function reviewImprovement(decision: 'ACCEPT' | 'REWORK') {
  if (!current.value || !improvement.value) return;
  try {
    const comment = (await ElMessageBox.prompt('请输入验收意见', decision === 'ACCEPT' ? '验收通过' : '退回整改', { inputPattern: /\S+/ })).value;
    const { data, error } = await session.client.POST('/performance/evaluations/{evaluationId}/improvement/review', {
      params: { path: { evaluationId: current.value.id } }, body: { decision, comment, version: improvement.value.version }
    });
    if (error) { ElMessage.error(error.error.message); return; }
    improvement.value = data?.data ?? null; ElMessage.success(decision === 'ACCEPT' ? '改进已验收' : '已退回整改');
  } catch { /* user cancelled */ }
}
onMounted(() => { load(); loadRule(); });
</script>

<template>
  <div class="page">
    <div class="page-heading"><div><h1>供应商绩效</h1><p>四维评分、事实快照、权重配置与独立审核</p></div><div><el-button @click="openRule">权重配置</el-button><el-button type="primary" @click="openCreate">新建评价</el-button></div></div>
    <el-card shadow="never" class="filter-card"><el-form inline><el-form-item label="供应商 ID"><el-input v-model="filter.supplierId" clearable /></el-form-item><el-form-item label="状态"><el-select v-model="filter.status" clearable style="width:140px"><el-option v-for="(label,key) in statuses" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-button type="primary" @click="filter.page=0;load()">查询</el-button></el-form></el-card>
    <el-card shadow="never" class="table-card"><el-table v-loading="loading" :data="rows">
      <el-table-column label="供应商" min-width="180"><template #default="{row}"><strong>{{row.supplierName}}</strong><div class="subtext">{{row.supplierCode}}</div></template></el-table-column>
      <el-table-column label="评价周期" width="230"><template #default="{row}">{{row.periodStart}} 至 {{row.periodEnd}}</template></el-table-column>
      <el-table-column label="总分/等级" width="115"><template #default="{row}">{{row.totalScore}} / {{row.grade}}</template></el-table-column>
      <el-table-column label="质量/安全事实" min-width="170"><template #default="{row}">质量 {{row.qualityNcrOpen}}/{{row.qualityNcrTotal}} 未关闭<br />安全 {{row.safetyIssueOpen}}/{{row.safetyIssueTotal}} 未关闭</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='APPROVED'?'success':row.status==='REJECTED'?'danger':'warning'">{{statuses[row.status as keyof typeof statuses]}}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="250"><template #default="{row}"><el-button link @click="showDetail(row)">详情</el-button><el-button v-if="row.status==='DRAFT'||row.status==='REJECTED'" link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="row.status==='DRAFT'" link type="primary" @click="submit(row)">提交</el-button><template v-if="row.status==='SUBMITTED' && row.createdBy !== session.actorId"><el-button link type="success" @click="review(row,'APPROVE')">批准</el-button><el-button link type="danger" @click="review(row,'REJECT')">驳回</el-button></template><span v-else-if="row.status==='SUBMITTED'">待其他用户审核</span></template></el-table-column>
    </el-table><div class="pagination"><el-pagination :current-page="filter.page+1" :total="total" :page-size="filter.size" layout="total, prev, pager, next" @current-change="(page: number) => {filter.page=page-1;load()}" /></div></el-card>
    <el-dialog v-model="ruleDialog" title="租户绩效权重配置" width="570px"><p>四项权重合计必须为 100。修改后只影响新建评价，历史评价保留原权重。</p><el-form label-position="top"><el-row :gutter="16"><el-col :span="12"><el-form-item label="质量权重"><el-input-number v-model="ruleForm.qualityWeight" :min="0" :max="100" /></el-form-item></el-col><el-col :span="12"><el-form-item label="交付权重"><el-input-number v-model="ruleForm.deliveryWeight" :min="0" :max="100" /></el-form-item></el-col><el-col :span="12"><el-form-item label="安全权重"><el-input-number v-model="ruleForm.safetyWeight" :min="0" :max="100" /></el-form-item></el-col><el-col :span="12"><el-form-item label="服务权重"><el-input-number v-model="ruleForm.serviceWeight" :min="0" :max="100" /></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="ruleDialog=false">取消</el-button><el-button type="primary" @click="saveRule">保存权重</el-button></template></el-dialog>
    <el-dialog v-model="evaluationDialog" :title="form.id?'编辑绩效评价':'新建绩效评价'" width="800px"><el-form label-position="top"><el-row :gutter="16"><el-col :span="24"><el-form-item label="供应商 *"><el-select v-if="!form.id" v-model="form.supplierId" filterable remote :remote-method="searchSuppliers" style="width:100%" placeholder="搜索供应商"><el-option v-for="s in suppliers" :key="s.id" :label="`${s.name} · ${s.code}`" :value="s.id" /></el-select><el-input v-else :model-value="form.supplierId" disabled /></el-form-item></el-col><el-col :span="12"><el-form-item label="周期开始 *"><el-date-picker v-model="form.periodStart" :disabled="!!form.id" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="12"><el-form-item label="周期结束 *"><el-date-picker v-model="form.periodEnd" :disabled="!!form.id" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row><el-divider>评分明细</el-divider><el-row v-for="item in form.items" :key="item.dimension" :gutter="16"><el-col :span="24"><strong>{{labels[item.dimension]}} · 权重 {{shownWeight(item.dimension)}}%</strong></el-col><el-col :span="7"><el-form-item label="分数 *"><el-input-number v-model="item.score" :min="0" :max="100" :precision="2" /></el-form-item></el-col><el-col :span="17"><el-form-item label="评分说明 *"><el-input v-model="item.comment" maxlength="1000" /></el-form-item></el-col><el-col :span="24"><el-form-item label="证据文件 ID *"><el-input v-model="item.evidenceFileId" /></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="evaluationDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存草稿</el-button></template></el-dialog>
    <el-drawer v-model="detailDrawer" :title="`${current?.supplierName ?? ''} · 绩效详情`" size="640px"><template v-if="current"><p>{{current.periodStart}} 至 {{current.periodEnd}} · {{current.totalScore}} 分 / {{current.grade}} 级</p><p>质量不符合项 {{current.qualityNcrOpen}}/{{current.qualityNcrTotal}} 未关闭；安全隐患 {{current.safetyIssueOpen}}/{{current.safetyIssueTotal}} 未关闭</p><el-table :data="current.items"><el-table-column label="维度"><template #default="{row}">{{labels[row.dimension as Dimension]}}</template></el-table-column><el-table-column prop="weight" label="权重" width="70" /><el-table-column prop="score" label="分数" width="70" /><el-table-column prop="comment" label="说明" min-width="150" /><el-table-column prop="evidenceFileId" label="文件 ID" width="110" /></el-table><h3>评价审核历程</h3><el-timeline><el-timeline-item v-for="event in events" :key="event.id" :timestamp="event.createdAt">{{event.action}} · {{event.fromStatus||'新建'}} → {{event.toStatus}}<p>{{event.comment}}</p><small>操作人 {{event.actorId}}</small></el-timeline-item></el-timeline><el-divider>绩效改进</el-divider><template v-if="current.status==='APPROVED'"><el-button v-if="!improvement" type="primary" @click="createImprovement">创建改进计划</el-button><template v-else><p>状态：{{ improvement.status }} · 期限：{{ improvement.dueDate }}</p><p>根因：{{ improvement.rootCause }}</p><p>措施：{{ improvement.actionPlan }}</p><p v-if="improvement.completionNote">完成情况：{{ improvement.completionNote }} · 证据文件 {{ improvement.evidenceFileId }}</p><p v-if="improvement.reviewComment">验收意见：{{ improvement.reviewComment }}</p><el-button v-if="improvement.status==='OPEN'||improvement.status==='REWORK'" type="primary" @click="submitImprovement">提交改进结果</el-button><template v-if="improvement.status==='SUBMITTED' && improvement.createdBy!==session.actorId"><el-button type="success" @click="reviewImprovement('ACCEPT')">验收通过</el-button><el-button type="danger" @click="reviewImprovement('REWORK')">退回整改</el-button></template><el-timeline><el-timeline-item v-for="event in improvement.events" :key="event.id" :timestamp="event.createdAt">{{event.action}} · {{event.fromStatus||'新建'}} → {{event.toStatus}}<p>{{event.comment}}</p></el-timeline-item></el-timeline></template></template></template></el-drawer>
  </div>
</template>

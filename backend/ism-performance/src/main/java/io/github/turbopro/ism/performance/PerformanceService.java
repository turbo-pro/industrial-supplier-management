package io.github.turbopro.ism.performance;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import io.github.turbopro.ism.supplier.SupplierReferenceService;
import io.github.turbopro.ism.resource.file.FileReferenceService;
import io.github.turbopro.ism.quality.QualityPerformanceFacts;
import io.github.turbopro.ism.safety.SafetyPerformanceFacts;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PerformanceService {
    private final PerformanceMapper mapper;
    private final OperationIdGenerator ids;
    private final AuditService audit;
    private final SupplierReferenceService suppliers;
    private final FileReferenceService files;
    private final QualityPerformanceFacts qualityFacts;
    private final SafetyPerformanceFacts safetyFacts;
    public PerformanceService(PerformanceMapper mapper, OperationIdGenerator ids, AuditService audit,
                              SupplierReferenceService suppliers, FileReferenceService files,
                              QualityPerformanceFacts qualityFacts, SafetyPerformanceFacts safetyFacts) {
        this.mapper = mapper; this.ids = ids; this.audit = audit;
        this.suppliers = suppliers; this.files = files;
        this.qualityFacts = qualityFacts; this.safetyFacts = safetyFacts;
    }

    public PerformanceModels.Rule rule() {
        var row = mapper.rule(TenantContext.require().tenantId());
        return row == null ? new PerformanceModels.Rule(35, 25, 25, 15, 0, false)
                : new PerformanceModels.Rule(row.qualityWeight(), row.deliveryWeight(), row.safetyWeight(),
                row.serviceWeight(), row.version(), true);
    }

    @Transactional
    public PerformanceModels.Rule saveRule(PerformanceModels.SaveRule command) {
        if (command.qualityWeight() + command.deliveryWeight() + command.safetyWeight() + command.serviceWeight() != 100)
            throw invalid("四项权重之和必须为 100");
        var identity = TenantContext.require();
        var previous = mapper.rule(identity.tenantId());
        if (previous == null) {
            if (command.version() != 0) throw new ApiException(CommonErrorCode.CONFLICT);
            try {
                mapper.insertRule(identity.tenantId(), command.qualityWeight(), command.deliveryWeight(),
                        command.safetyWeight(), command.serviceWeight(), identity.actorId());
            } catch (DuplicateKeyException exception) { throw new ApiException(CommonErrorCode.CONFLICT); }
        } else if (mapper.updateRule(identity.tenantId(), command.qualityWeight(), command.deliveryWeight(),
                command.safetyWeight(), command.serviceWeight(), identity.actorId(), command.version()) != 1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        audit("PERFORMANCE_RULE_UPDATE", identity.tenantId(), "CONFIGURED");
        return rule();
    }

    public PerformanceModels.Page list(String supplierId, String status, int page, int size) {
        var identity = TenantContext.require();
        var scope = scope();
        Long supplier = supplierId == null || supplierId.isBlank() ? null : id(supplierId, "供应商 ID 无效");
        String state = null;
        if (status != null && !status.isBlank()) {
            try { state = PerformanceModels.Status.valueOf(status).name(); }
            catch (IllegalArgumentException exception) { throw invalid("评价状态无效"); }
        }
        long total = mapper.count(identity.tenantId(), supplier, state, scope.type().name(),
                scope.organizationIds(), identity.actorId());
        var rows = mapper.list(identity.tenantId(), supplier, state, scope.type().name(),
                scope.organizationIds(), identity.actorId(), Math.multiplyExact(page, size), size);
        return new PerformanceModels.Page(total, page, size, rows.stream().map(this::view).toList());
    }

    public PerformanceModels.View get(long id) { return view(require(id)); }

    public List<PerformanceModels.Event> events(long id) {
        require(id);
        var identity = TenantContext.require();
        return mapper.events(identity.tenantId(), id).stream().map(row -> new PerformanceModels.Event(
                Long.toString(row.id()), row.action(), row.fromStatus(), row.toStatus(), row.comment(),
                Long.toString(row.actorId()), row.createdAt())).toList();
    }

    @Transactional
    public PerformanceModels.View create(PerformanceModels.SaveEvaluation command) {
        if (command.version() != 0) throw invalid("新建评价的版本必须为 0");
        dates(command);
        var identity = TenantContext.require();
        long supplierId = id(command.supplierId(), "供应商 ID 无效");
        var supplier = suppliers.active(supplierId);
        if (supplier == null) throw invalid("供应商不存在或不可用");
        if (!scope().allows(new DataTarget(supplier.organizationId(), null, null,
                identity.actorId()), identity.actorId())) throw new ApiException(CommonErrorCode.FORBIDDEN);
        var weights = weights(rule());
        var items = items(command.items(), weights);
        var quality = qualityFacts.forSupplier(supplierId, command.periodStart(), command.periodEnd());
        var safety = safetyFacts.forSupplier(supplierId, command.periodStart(), command.periodEnd());
        BigDecimal total = total(items);
        long evaluationId = ids.nextId();
        try {
            mapper.insert(evaluationId, identity.tenantId(), supplier.organizationId(), supplierId,
                    supplier.code(), supplier.name(), command.periodStart(), command.periodEnd(), total, grade(total),
                    quality.total(), quality.open(), safety.total(), safety.open(), identity.actorId());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(CommonErrorCode.CONFLICT, "供应商在该评价周期已有记录");
        }
        writeItems(evaluationId, items);
        event(evaluationId, "CREATE", null, "DRAFT", null);
        audit("PERFORMANCE_EVALUATION_CREATE", evaluationId, "DRAFT");
        return view(mapper.get(identity.tenantId(), evaluationId));
    }

    @Transactional
    public PerformanceModels.View update(long evaluationId, PerformanceModels.SaveEvaluation command) {
        dates(command);
        var before = require(evaluationId);
        if (!before.status().equals("DRAFT") && !before.status().equals("REJECTED"))
            throw invalid("当前状态不能修改评价");
        if (before.supplierId() != id(command.supplierId(), "供应商 ID 无效")
                || !before.periodStart().equals(command.periodStart()) || !before.periodEnd().equals(command.periodEnd()))
            throw invalid("供应商和评价周期不可修改");
        var identity = TenantContext.require();
        var weights = mapper.items(identity.tenantId(), evaluationId).stream().collect(Collectors.toMap(
                row -> PerformanceModels.Dimension.valueOf(row.dimensionCode()), PerformanceModels.ItemRow::weight));
        var items = items(command.items(), weights);
        var quality = qualityFacts.forSupplier(before.supplierId(), before.periodStart(), before.periodEnd());
        var safety = safetyFacts.forSupplier(before.supplierId(), before.periodStart(), before.periodEnd());
        BigDecimal total = total(items);
        if (mapper.update(identity.tenantId(), evaluationId, total, grade(total), quality.total(),
                quality.open(), safety.total(), safety.open(), identity.actorId(), command.version()) != 1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        mapper.deleteItems(identity.tenantId(), evaluationId);
        writeItems(evaluationId, items);
        event(evaluationId, "UPDATE", before.status(), "DRAFT", null);
        audit("PERFORMANCE_EVALUATION_UPDATE", evaluationId, "DRAFT");
        return view(mapper.get(identity.tenantId(), evaluationId));
    }

    @Transactional
    public PerformanceModels.View submit(long evaluationId, int version) {
        var before = require(evaluationId);
        if (!before.status().equals("DRAFT")) throw invalid("只有草稿可以提交审核");
        var identity = TenantContext.require();
        if (mapper.submit(identity.tenantId(), evaluationId, identity.actorId(), version) != 1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        event(evaluationId, "SUBMIT", "DRAFT", "SUBMITTED", null);
        audit("PERFORMANCE_EVALUATION_SUBMIT", evaluationId, "SUBMITTED");
        return view(mapper.get(identity.tenantId(), evaluationId));
    }

    @Transactional
    public PerformanceModels.View review(long evaluationId, PerformanceModels.Review command) {
        var before = require(evaluationId);
        if (!before.status().equals("SUBMITTED")) throw invalid("只有已提交评价可以审核");
        var identity = TenantContext.require();
        if (before.createdBy() == identity.actorId()) throw invalid("评价人不能审核自己的评价");
        if (command.decision() == PerformanceModels.Decision.REJECT
                && (command.comment() == null || command.comment().isBlank())) throw invalid("驳回必须填写原因");
        String next = command.decision() == PerformanceModels.Decision.APPROVE ? "APPROVED" : "REJECTED";
        String comment = command.comment() == null ? null : command.comment().trim();
        if (mapper.review(identity.tenantId(), evaluationId, command.decision().name(), comment,
                identity.actorId(), command.version()) != 1) throw new ApiException(CommonErrorCode.CONFLICT);
        event(evaluationId, "REVIEW_" + command.decision().name(), "SUBMITTED", next, comment);
        audit("PERFORMANCE_EVALUATION_REVIEW", evaluationId, next);
        return view(mapper.get(identity.tenantId(), evaluationId));
    }

    private PerformanceModels.Row require(long id) {
        var identity = TenantContext.require();
        var row = mapper.get(identity.tenantId(), id);
        if (row == null || !scope().allows(new DataTarget(row.organizationId(), null, null,
                row.createdBy()), identity.actorId())) throw new ApiException(CommonErrorCode.NOT_FOUND);
        return row;
    }
    private DataScope scope() { return AuthorizationContext.require().dataScope("performance:evaluation"); }
    private void dates(PerformanceModels.SaveEvaluation command) {
        if (command.periodStart().isAfter(command.periodEnd())) throw invalid("周期开始日期不能晚于结束日期");
        if (command.periodEnd().isAfter(LocalDate.now())) throw invalid("不能评价未结束的周期");
    }
    private Map<PerformanceModels.Dimension, Integer> weights(PerformanceModels.Rule rule) {
        return Map.of(PerformanceModels.Dimension.QUALITY, rule.qualityWeight(),
                PerformanceModels.Dimension.DELIVERY, rule.deliveryWeight(),
                PerformanceModels.Dimension.SAFETY, rule.safetyWeight(),
                PerformanceModels.Dimension.SERVICE, rule.serviceWeight());
    }
    private List<WeightedItem> items(List<PerformanceModels.ScoreItem> source,
                                     Map<PerformanceModels.Dimension, Integer> weights) {
        if (source == null || source.size() != 4 || weights.size() != 4) throw invalid("必须填写四个完整评价维度");
        var dimensions = EnumSet.noneOf(PerformanceModels.Dimension.class);
        var result = new ArrayList<WeightedItem>();
        for (var item : source) {
            if (item == null || item.dimension() == null || !dimensions.add(item.dimension()))
                throw invalid("评价维度重复或缺失");
            if (item.score() == null || item.score().compareTo(BigDecimal.ZERO) < 0
                    || item.score().compareTo(new BigDecimal("100")) > 0 || item.score().scale() > 2)
                throw invalid("维度评分必须介于 0 到 100，最多两位小数");
            if (item.comment() == null || item.comment().isBlank() || item.comment().length() > 1000)
                throw invalid("每项评分都需要说明");
            long fileId = id(item.evidenceFileId(), "证据文件 ID 无效");
            if (!files.active(fileId))
                throw invalid("证据文件不存在或不可用");
            result.add(new WeightedItem(item.dimension(), weights.get(item.dimension()), item.score(),
                    item.comment().trim(), fileId));
        }
        if (dimensions.size() != 4) throw invalid("必须填写四个完整评价维度");
        return result;
    }
    private BigDecimal total(List<WeightedItem> items) {
        return items.stream().map(item -> item.score().multiply(BigDecimal.valueOf(item.weight())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
    private String grade(BigDecimal total) {
        if (total.compareTo(new BigDecimal("90")) >= 0) return "A";
        if (total.compareTo(new BigDecimal("80")) >= 0) return "B";
        if (total.compareTo(new BigDecimal("70")) >= 0) return "C";
        return "D";
    }
    private void writeItems(long id, List<WeightedItem> items) {
        long tenantId = TenantContext.require().tenantId();
        for (var item : items)
            mapper.insertItem(id, tenantId, item.dimension().name(), item.weight(), item.score(), item.comment(), item.fileId());
    }
    private void event(long id, String action, String from, String to, String comment) {
        var identity = TenantContext.require();
        mapper.insertEvent(ids.nextId(), identity.tenantId(), id, action, from, to, comment, identity.actorId());
    }
    private void audit(String action, long id, String status) {
        audit.append(new AuditService.AuditCommand(action, "PERFORMANCE_EVALUATION", id, null,
                Map.of(), Map.of("status", status), null, null));
    }
    private PerformanceModels.View view(PerformanceModels.Row row) {
        long tenantId = TenantContext.require().tenantId();
        var itemViews = mapper.items(tenantId, row.id()).stream().map(item -> new PerformanceModels.ItemView(
                PerformanceModels.Dimension.valueOf(item.dimensionCode()), item.weight(), item.score(),
                item.comment(), Long.toString(item.evidenceFileId()))).toList();
        return new PerformanceModels.View(Long.toString(row.id()), Long.toString(row.organizationId()),
                Long.toString(row.supplierId()), row.supplierCode(), row.supplierName(), row.periodStart(),
                row.periodEnd(), row.totalScore(), PerformanceModels.Grade.valueOf(row.grade()),
                PerformanceModels.Status.valueOf(row.status()), row.qualityNcrTotal(), row.qualityNcrOpen(),
                row.safetyIssueTotal(), row.safetyIssueOpen(), row.submittedAt(),
                row.reviewedBy() == null ? null : Long.toString(row.reviewedBy()), row.reviewedAt(),
                row.reviewComment(), Long.toString(row.createdBy()), row.version(), row.createdAt(), row.updatedAt(), itemViews);
    }
    private long id(String value, String message) {
        try { long id = Long.parseLong(value); if (id > 0) return id; }
        catch (Exception ignored) { }
        throw invalid(message);
    }
    private ApiException invalid(String message) { return new ApiException(CommonErrorCode.VALIDATION_FAILED, message); }
    private record WeightedItem(PerformanceModels.Dimension dimension, int weight, BigDecimal score,
                                String comment, long fileId) {}
}

package io.github.turbopro.ism.quality;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class QualityNcrService {
    private final QualityNcrMapper mapper;
    private final OperationIdGenerator ids;
    private final AuditService audit;
    public QualityNcrService(QualityNcrMapper mapper, OperationIdGenerator ids, AuditService audit) {
        this.mapper = mapper; this.ids = ids; this.audit = audit;
    }

    public QualityNcrModels.Page list(String keyword, String status, int page, int size) {
        var identity = TenantContext.require();
        var scope = scope();
        String search = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().replace("=", "==").replace("%", "=%").replace("_", "=_") + "%";
        String state = null;
        if (status != null && !status.isBlank()) {
            try { state = QualityNcrModels.Status.valueOf(status).name(); }
            catch (IllegalArgumentException exception) { throw invalid("状态无效"); }
        }
        long total = mapper.count(identity.tenantId(), search, state, scope.type().name(),
                scope.organizationIds(), scope.projectIds(), identity.actorId());
        var items = mapper.list(identity.tenantId(), search, state, scope.type().name(),
                scope.organizationIds(), scope.projectIds(), identity.actorId(), Math.multiplyExact(page, size), size);
        return new QualityNcrModels.Page(total, page, size, items.stream().map(this::view).toList());
    }

    public QualityNcrModels.View get(long ncrId) { return view(require(ncrId)); }

    public List<QualityNcrModels.Event> events(long ncrId) {
        require(ncrId);
        var identity = TenantContext.require();
        return mapper.events(identity.tenantId(), ncrId).stream().map(e -> new QualityNcrModels.Event(
                Long.toString(e.id()), e.action(), e.fromStatus(), e.toStatus(), e.note(),
                e.fileId() == null ? null : Long.toString(e.fileId()), Long.toString(e.actorId()), e.createdAt())).toList();
    }

    @Transactional
    public QualityNcrModels.View create(QualityNcrModels.Create command) {
        var identity = TenantContext.require();
        long projectId = id(command.projectId(), "项目 ID 无效");
        var project = mapper.activeProject(identity.tenantId(), projectId);
        if (project == null) throw invalid("项目不存在、未进行中或供应商不可用");
        long responsible = id(command.responsibleUserId(), "责任人 ID 无效");
        if (!scope().allows(new DataTarget(project.organizationId(), projectId,
                responsible, identity.actorId()), identity.actorId()))
            throw new ApiException(CommonErrorCode.FORBIDDEN);
        if (command.inspectionDate().isAfter(LocalDate.now())) throw invalid("验收日期不能晚于当前日期");
        if (command.deadline().isBefore(command.inspectionDate())) throw invalid("整改期限不能早于验收日期");
        if (command.defectiveQuantity().compareTo(command.inspectedQuantity()) > 0)
            throw invalid("缺陷数量不能大于验收数量");
        long evidence = activeFile(command.evidenceFileId());
        if (mapper.activeUser(identity.tenantId(), responsible) == 0)
            throw invalid("责任人不是当前租户的有效用户");
        long ncrId = ids.nextId();
        try {
            mapper.insert(ncrId, identity.tenantId(), project.organizationId(), projectId, project.supplierId(),
                    command.ncrNo(), command.title().trim(), command.category().name(), command.severity().name(),
                    command.description().trim(), command.inspectionDate(), command.inspectedQuantity(),
                    command.defectiveQuantity(), command.unit().trim(), evidence, command.deadline(), responsible,
                    identity.actorId());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(CommonErrorCode.CONFLICT, "不符合项编号已存在");
        }
        event(ncrId, "CREATE", null, "OPEN", command.description().trim(), evidence);
        audit("QUALITY_NCR_CREATE", ncrId, "OPEN");
        return view(mapper.get(identity.tenantId(), ncrId));
    }

    @Transactional
    public QualityNcrModels.View rectify(long ncrId, QualityNcrModels.Rectify command) {
        var before = require(ncrId);
        if (!before.status().equals("OPEN")) throw invalid("当前状态不能提交纠正措施");
        long fileId = activeFile(command.fileId());
        var identity = TenantContext.require();
        if (mapper.rectify(identity.tenantId(), ncrId, command.rootCause().trim(), command.correction().trim(),
                command.preventiveAction().trim(), fileId, identity.actorId(), command.version()) != 1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        event(ncrId, "RECTIFY", "OPEN", "PENDING_REVIEW",
                "原因：" + command.rootCause().trim() + "\n纠正：" + command.correction().trim()
                        + "\n预防：" + command.preventiveAction().trim(), fileId);
        audit("QUALITY_NCR_RECTIFY", ncrId, "PENDING_REVIEW");
        return view(mapper.get(identity.tenantId(), ncrId));
    }

    @Transactional
    public QualityNcrModels.View verify(long ncrId, QualityNcrModels.Verify command) {
        var before = require(ncrId);
        if (!before.status().equals("PENDING_REVIEW")) throw invalid("当前状态不能复验");
        var identity = TenantContext.require();
        if (mapper.verify(identity.tenantId(), ncrId, command.decision().name(), command.comment().trim(),
                identity.actorId(), command.version()) != 1) throw new ApiException(CommonErrorCode.CONFLICT);
        String next = command.decision() == QualityNcrModels.Decision.PASS ? "CLOSED" : "OPEN";
        event(ncrId, "VERIFY_" + command.decision().name(), "PENDING_REVIEW", next, command.comment().trim(), null);
        audit("QUALITY_NCR_VERIFY", ncrId, next);
        return view(mapper.get(identity.tenantId(), ncrId));
    }

    private QualityNcrModels.Row require(long ncrId) {
        var identity = TenantContext.require();
        var row = mapper.get(identity.tenantId(), ncrId);
        if (row == null || !scope().allows(new DataTarget(row.organizationId(), row.projectId(),
                row.responsibleUserId(), row.createdBy()), identity.actorId()))
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        return row;
    }
    private DataScope scope() { return AuthorizationContext.require().dataScope("quality:ncr"); }
    private long activeFile(String value) {
        long fileId = id(value, "文件 ID 无效");
        if (mapper.activeFile(TenantContext.require().tenantId(), fileId) == 0) throw invalid("证据文件不存在或不可用");
        return fileId;
    }
    private long id(String value, String message) {
        try { long result = Long.parseLong(value); if (result > 0) return result; }
        catch (Exception ignored) { }
        throw invalid(message);
    }
    private ApiException invalid(String message) { return new ApiException(CommonErrorCode.VALIDATION_FAILED, message); }
    private void event(long ncrId, String action, String from, String to, String note, Long fileId) {
        var identity = TenantContext.require();
        mapper.insertEvent(ids.nextId(), identity.tenantId(), ncrId, action, from, to, note, fileId, identity.actorId());
    }
    private void audit(String action, long ncrId, String state) {
        audit.append(new AuditService.AuditCommand(action, "QUALITY_NCR", ncrId, null,
                Map.of(), Map.of("status", state), null, null));
    }
    private QualityNcrModels.View view(QualityNcrModels.Row row) {
        return new QualityNcrModels.View(Long.toString(row.id()), Long.toString(row.organizationId()),
                Long.toString(row.projectId()), Long.toString(row.supplierId()), row.projectCode(), row.projectName(),
                row.supplierCode(), row.supplierName(), row.ncrNo(), row.title(),
                QualityNcrModels.Category.valueOf(row.category()), QualityNcrModels.Severity.valueOf(row.severity()),
                row.description(), row.inspectionDate(), row.inspectedQuantity(), row.defectiveQuantity(), row.unit(),
                Long.toString(row.evidenceFileId()), row.deadline(), Long.toString(row.responsibleUserId()),
                QualityNcrModels.Status.valueOf(row.status()), row.rootCause(), row.correction(),
                row.preventiveAction(), row.actionFileId() == null ? null : Long.toString(row.actionFileId()),
                row.submittedAt(), row.verificationResult(), row.verificationComment(),
                row.verifiedBy() == null ? null : Long.toString(row.verifiedBy()), row.verifiedAt(),
                !row.status().equals("CLOSED") && row.deadline().isBefore(LocalDate.now()),
                row.version(), row.createdAt(), row.updatedAt());
    }
}

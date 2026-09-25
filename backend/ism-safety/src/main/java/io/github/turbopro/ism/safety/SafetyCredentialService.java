package io.github.turbopro.ism.safety;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
public class SafetyCredentialService {
    private final SafetyCredentialMapper mapper;
    private final OperationIdGenerator ids;
    private final AuditService audit;

    public SafetyCredentialService(SafetyCredentialMapper mapper, OperationIdGenerator ids, AuditService audit) {
        this.mapper = mapper;
        this.ids = ids;
        this.audit = audit;
    }

    public SafetyCredentialModels.Page list(String personId, String kind, String status, int page, int size) {
        var identity = TenantContext.require();
        var scope = AuthorizationContext.require().dataScope("safety:credential");
        Long pid = personId == null || personId.isBlank() ? null : id(personId, "人员 ID 无效");
        String type = parseKind(kind), state = parseStatus(status);
        long total = mapper.count(identity.tenantId(), pid, type, state, scope.type().name(),
                scope.organizationIds(), scope.projectIds(), identity.actorId());
        var rows = mapper.list(identity.tenantId(), pid, type, state, scope.type().name(),
                scope.organizationIds(), scope.projectIds(), identity.actorId(), Math.multiplyExact(page, size), size);
        return new SafetyCredentialModels.Page(total, page, size, rows.stream().map(this::view).toList());
    }

    @Transactional
    public SafetyCredentialModels.View create(SafetyCredentialModels.Create command) {
        var identity = TenantContext.require();
        long personId = id(command.personId(), "人员 ID 无效");
        var person = accessiblePerson(personId);
        if (person.status().equals("EXITED")) throw invalid("离场人员不能登记凭证");
        if (command.effectiveDate().isAfter(command.expiryDate())) throw invalid("有效期开始日期不能晚于结束日期");
        if (command.kind() == SafetyCredentialModels.Kind.TRAINING) {
            if (command.workType() != null || command.passed() == null) throw invalid("培训记录需填写考试结果且不能填写特种作业类型");
        } else if (command.workType() == null || !command.workType().name().equals(person.specialWorkType())) {
            throw invalid("特种作业类型必须与人员登记的类型一致");
        }
        long fileId = id(command.fileId(), "凭证文件 ID 无效");
        if (mapper.activeFile(identity.tenantId(), fileId) == 0) throw invalid("凭证文件不存在或不可用");
        long credentialId = ids.nextId();
        try {
            mapper.insert(credentialId, identity.tenantId(), person.organizationId(), person.supplierId(),
                    personId, person.projectId(), command.credentialNo(), command.kind().name(),
                    command.workType() == null ? null : command.workType().name(), command.title().trim(),
                    command.examScore(), command.passed(), command.effectiveDate(), command.expiryDate(),
                    fileId, identity.actorId());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(CommonErrorCode.CONFLICT, "凭证编号已存在");
        }
        audit("SAFETY_CREDENTIAL_CREATE", credentialId, "PENDING");
        return view(mapper.get(identity.tenantId(), credentialId));
    }

    @Transactional
    public SafetyCredentialModels.View review(long id, SafetyCredentialModels.Review command) {
        var before = accessibleCredential(id);
        String expected = command.decision() == SafetyCredentialModels.Decision.REVOKE ? "VERIFIED" : "PENDING";
        if (!before.status().equals(expected)) throw invalid("当前状态不允许此审核操作");
        if (command.decision() != SafetyCredentialModels.Decision.APPROVE
                && (command.comment() == null || command.comment().isBlank())) throw invalid("驳回或撤销必须填写原因");
        if (command.decision() == SafetyCredentialModels.Decision.APPROVE) {
            if (before.expiryDate().isBefore(LocalDate.now())) throw invalid("过期凭证不能审核通过");
            if (before.credentialKind().equals("TRAINING") && !Boolean.TRUE.equals(before.passed()))
                throw invalid("考试未通过的培训记录不能审核通过");
        }
        String next = switch (command.decision()) {
            case APPROVE -> "VERIFIED";
            case REJECT -> "REJECTED";
            case REVOKE -> "REVOKED";
        };
        var identity = TenantContext.require();
        if (mapper.review(identity.tenantId(), id, expected, next,
                command.comment() == null ? null : command.comment().trim(), identity.actorId(), command.version()) != 1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        audit("SAFETY_CREDENTIAL_REVIEW", id, next);
        return view(mapper.get(identity.tenantId(), id));
    }

    public SafetyCredentialModels.Eligibility eligibility(long personId) {
        var person = accessiblePerson(personId);
        long tenantId = TenantContext.require().tenantId();
        boolean training = mapper.validTraining(tenantId, personId) > 0;
        boolean special = person.specialWorkType() == null
                || mapper.validSpecialWork(tenantId, personId, person.specialWorkType()) > 0;
        return new SafetyCredentialModels.Eligibility(Long.toString(personId), person.personName(), person.status(),
                person.specialWorkType() == null ? null : SafetyCredentialModels.WorkType.valueOf(person.specialWorkType()),
                training, special, !person.status().equals("EXITED") && training && special);
    }

    private SafetyCredentialModels.PersonRef accessiblePerson(long id) {
        var identity = TenantContext.require();
        var person = mapper.person(identity.tenantId(), id);
        if (person == null || !scope().allows(new DataTarget(person.organizationId(), person.projectId(),
                null, person.createdBy()), identity.actorId())) throw new ApiException(CommonErrorCode.NOT_FOUND);
        return person;
    }

    private SafetyCredentialModels.Row accessibleCredential(long id) {
        var identity = TenantContext.require();
        var row = mapper.get(identity.tenantId(), id);
        if (row == null || !scope().allows(new DataTarget(row.organizationId(), row.projectId(),
                null, row.createdBy()), identity.actorId())) throw new ApiException(CommonErrorCode.NOT_FOUND);
        return row;
    }

    private DataScope scope() { return AuthorizationContext.require().dataScope("safety:credential"); }
    private String parseKind(String value) {
        if (value == null || value.isBlank()) return null;
        try { return SafetyCredentialModels.Kind.valueOf(value).name(); }
        catch (IllegalArgumentException exception) { throw invalid("凭证类型无效"); }
    }
    private String parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return SafetyCredentialModels.Status.valueOf(value).name(); }
        catch (IllegalArgumentException exception) { throw invalid("凭证状态无效"); }
    }
    private long id(String value, String message) {
        try { long id = Long.parseLong(value); if (id > 0) return id; }
        catch (Exception ignored) { }
        throw invalid(message);
    }
    private ApiException invalid(String message) { return new ApiException(CommonErrorCode.VALIDATION_FAILED, message); }
    private void audit(String action, long id, String state) {
        audit.append(new AuditService.AuditCommand(action, "SAFETY_CREDENTIAL", id, null,
                Map.of(), Map.of("status", state), null, null));
    }
    private SafetyCredentialModels.View view(SafetyCredentialModels.Row row) {
        boolean valid = row.status().equals("VERIFIED")
                && !row.effectiveDate().isAfter(LocalDate.now())
                && !row.expiryDate().isBefore(LocalDate.now())
                && (!row.credentialKind().equals("TRAINING") || Boolean.TRUE.equals(row.passed()));
        return new SafetyCredentialModels.View(Long.toString(row.id()), Long.toString(row.personId()),
                row.personCode(), row.personName(), row.credentialNo(),
                SafetyCredentialModels.Kind.valueOf(row.credentialKind()),
                row.workType() == null ? null : SafetyCredentialModels.WorkType.valueOf(row.workType()),
                row.title(), row.examScore(), row.passed(), row.effectiveDate(), row.expiryDate(),
                Long.toString(row.fileId()), SafetyCredentialModels.Status.valueOf(row.status()),
                row.reviewComment(), row.reviewedBy() == null ? null : Long.toString(row.reviewedBy()),
                row.reviewedAt(), valid, row.version(), row.updatedAt());
    }
}

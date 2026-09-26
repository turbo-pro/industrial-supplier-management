package io.github.turbopro.ism.safety;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class SafetyAttendanceService {
    private final SafetyAttendanceMapper mapper;
    private final SafetyCredentialMapper credentialMapper;
    private final OperationIdGenerator ids;
    private final AuditService audit;
    private final io.github.turbopro.ism.supplier.SupplierReferenceService suppliers;

    public SafetyAttendanceService(SafetyAttendanceMapper mapper, SafetyCredentialMapper credentialMapper,
                                   OperationIdGenerator ids, AuditService audit,io.github.turbopro.ism.supplier.SupplierReferenceService suppliers) {
        this.mapper = mapper; this.credentialMapper = credentialMapper;
        this.ids = ids; this.audit = audit;
        this.suppliers=suppliers;
    }

    public SafetyAttendanceModels.Page list(String personId, boolean openOnly, int page, int size) {
        var identity = TenantContext.require();
        var scope = AuthorizationContext.require().dataScope("safety:attendance");
        Long pid = personId == null || personId.isBlank() ? null : id(personId);
        long total = mapper.count(identity.tenantId(), pid, openOnly, scope.type().name(), scope.organizationIds(), scope.projectIds(), identity.actorId());
        var rows = mapper.list(identity.tenantId(), pid, openOnly, scope.type().name(), scope.organizationIds(), scope.projectIds(), identity.actorId(), Math.multiplyExact(page, size), size);
        return new SafetyAttendanceModels.Page(total, page, size, rows.stream().map(this::view).toList());
    }

    @Transactional
    public SafetyAttendanceModels.View checkIn(SafetyAttendanceModels.CheckIn command) {
        var identity = TenantContext.require();
        long personId = id(command.personId());
        var person = credentialMapper.person(identity.tenantId(), personId);
        if (person == null || person.projectId() == null || !scope().allows(
                new DataTarget(person.organizationId(), person.projectId(), null, person.createdBy()), identity.actorId()))
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(suppliers.activeForNewBusiness(person.supplierId())==null)
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"供应商无效或存在生效限制，不能入场");
        person=credentialMapper.personForEntry(identity.tenantId(),personId);
        if(person==null || person.projectId()==null || !scope().allows(
                new DataTarget(person.organizationId(),person.projectId(),null,person.createdBy()),identity.actorId()))
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        if (mapper.activeProject(identity.tenantId(), person.projectId(), person.supplierId()) == 0)
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "所属项目不是进行中状态");
        boolean trained = credentialMapper.validTraining(identity.tenantId(), personId) > 0;
        boolean specialReady = person.specialWorkType() == null || credentialMapper.validSpecialWork(
                identity.tenantId(), personId, person.specialWorkType()) > 0;
        if (!"ACTIVE".equals(person.status()) || !trained || !specialReady)
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "人员状态或安全凭证不满足入场要求");
        long attendanceId = ids.nextId();
        try {
            mapper.checkIn(attendanceId, identity.tenantId(), person.organizationId(), person.projectId(),
                    person.supplierId(), personId, command.siteName().trim(), identity.actorId());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(CommonErrorCode.CONFLICT, "人员已有未签退的现场记录");
        }
        audit("SAFETY_ATTENDANCE_CHECK_IN", attendanceId, "OPEN");
        return view(mapper.get(identity.tenantId(), attendanceId));
    }

    @Transactional
    public SafetyAttendanceModels.View checkOut(long attendanceId, SafetyAttendanceModels.CheckOut command) {
        var identity = TenantContext.require();
        var before = mapper.get(identity.tenantId(), attendanceId);
        if (before == null || !scope().allows(new DataTarget(before.organizationId(), before.projectId(),
                null, before.createdBy()), identity.actorId())) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if (before.checkOutAt() != null) throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "该记录已经签退");
        if (mapper.checkOut(identity.tenantId(), attendanceId, identity.actorId(),
                command.note() == null ? null : command.note().trim(), command.version()) != 1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        audit("SAFETY_ATTENDANCE_CHECK_OUT", attendanceId, "CLOSED");
        return view(mapper.get(identity.tenantId(), attendanceId));
    }

    private DataScope scope() { return AuthorizationContext.require().dataScope("safety:attendance"); }
    private long id(String value) {
        try { long result = Long.parseLong(value); if (result > 0) return result; }
        catch (Exception ignored) { }
        throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "人员 ID 无效");
    }
    private SafetyAttendanceModels.View view(SafetyAttendanceModels.Row row) {
        return new SafetyAttendanceModels.View(Long.toString(row.id()), Long.toString(row.organizationId()),
                Long.toString(row.projectId()), Long.toString(row.supplierId()), Long.toString(row.personId()),
                row.personCode(), row.personName(), row.supplierName(), row.projectName(), row.siteName(),
                row.checkInAt(), row.checkOutAt(), Long.toString(row.checkInBy()),
                row.checkOutBy() == null ? null : Long.toString(row.checkOutBy()), row.checkOutNote(), row.version());
    }
    private void audit(String action, long id, String state) {
        audit.append(new AuditService.AuditCommand(action, "SAFETY_ATTENDANCE", id, null,
                Map.of(), Map.of("status", state), null, null));
    }
}

package io.github.turbopro.ism.platform.support;

import io.github.turbopro.ism.common.api.support.SupportSupplierProjection;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportSupplierReadService {
    private final SupportSessionService sessions;
    private final SupportSupplierProjection suppliers;
    private final AuditService audit;

    public SupportSupplierReadService(SupportSessionService sessions, SupportSupplierProjection suppliers,
                                      AuditService audit) {
        this.sessions = sessions;
        this.suppliers = suppliers;
        this.audit = audit;
    }

    /** Empty means the supplier is absent or outside scope. Audit commits in either case. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, String> read(long sessionId, long tenantId, String access, long supplierId) {
        var scope = sessions.requireAccess(sessionId, tenantId, access);
        return project(sessionId, tenantId, supplierId, scope, "CONSOLE");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, String> readWorkspace(long sessionId, String access, long supplierId) {
        var identity = sessions.workspaceIdentity(sessionId, access);
        try (var operator = TenantContext.open(0, Long.parseLong(identity.operatorId()))) {
            return project(sessionId, Long.parseLong(identity.tenantId()), supplierId, identity.scope(), identity.realm());
        }
    }

    private Map<String, String> project(long sessionId, long tenantId, long supplierId,
                                        TenantSupportModels.Scope scope, String surface) {
        long operator = TenantContext.require().actorId();
        SupportSupplierProjection.Row row;
        try (var target = TenantContext.open(tenantId, operator)) {
            row = suppliers.find(supplierId, scope.tenantAll(), scope.organizations());
        }
        var result = new LinkedHashMap<String, String>();
        if (row != null) {
            var basic = Map.of("id", Long.toString(row.id()), "code", row.code(), "name", row.name(),
                    "type", row.type(), "status", row.status());
            basic.forEach((key, value) -> {
                if (scope.fields().contains(key)) {
                    result.put(key, value);
                }
            });
        }
        audit.append(new AuditService.AuditCommand("SUPPORT_SUPPLIER_READ", "SUPPLIER", supplierId,
                null, Map.of(), Map.of("sessionId", Long.toString(sessionId),
                "targetTenantId", Long.toString(tenantId), "surface", surface,
                "result", row == null ? "NOT_VISIBLE" : "READ"), null, null));
        return Map.copyOf(result);
    }
}

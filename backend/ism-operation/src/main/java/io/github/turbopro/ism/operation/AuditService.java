package io.github.turbopro.ism.operation;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.web.TraceIdContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AuditService {
    private final TenantOperationMapper mapper;
    private final OperationIdGenerator ids;
    private final SensitivePayloadSanitizer sanitizer;

    public AuditService(TenantOperationMapper mapper, OperationIdGenerator ids,
                        SensitivePayloadSanitizer sanitizer) {
        this.mapper = mapper;
        this.ids = ids;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public long append(AuditCommand command) {
        TenantContext.Identity identity = TenantContext.require();
        long id = ids.nextId();
        mapper.insertAudit(id, identity.tenantId(), identity.actorId(), command.actingUserId(),
                command.action(), command.objectType(), command.objectId(),
                sanitizer.sanitize(command.beforeSummary()), sanitizer.sanitize(command.afterSummary()),
                command.ip(), command.userAgent(), TraceIdContext.currentTraceId(), utcNow());
        return id;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public record AuditCommand(String action, String objectType, long objectId, Long actingUserId,
                               Object beforeSummary, Object afterSummary, String ip, String userAgent) {
    }
}

package io.github.turbopro.ism.operation;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.web.TraceIdContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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

    public record SupportAccessRow(String id, String objectId, String result, String surface,
                                   String traceId, LocalDateTime occurredAt) {}
    public record SupportAccessPage(List<SupportAccessRow> items, boolean hasMore) {}

    public SupportAccessPage ownSupportAccess(long sessionId, long targetTenantId, int page) {
        var actor = TenantContext.require();
        if (actor.tenantId() != 0) throw new ApiException(CommonErrorCode.FORBIDDEN);
        if (page < 0 || page > 100) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        var rows = mapper.supportAccess(0, actor.actorId(), Long.toString(sessionId),
                Long.toString(targetTenantId), page * 20);
        return new SupportAccessPage(rows.stream().limit(20).toList(), rows.size() > 20 && page < 100);
    }

    public SupportAccessPage auditedSupportAccess(long sessionId, long targetTenantId, long operatorId, int page) {
        if (TenantContext.require().tenantId() != 0 || !io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext
                .require().hasAction("platform:support:audit")) throw new ApiException(CommonErrorCode.FORBIDDEN);
        if (page < 0 || page > 100) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        var rows = mapper.supportAccess(0, operatorId, Long.toString(sessionId), Long.toString(targetTenantId), page * 20);
        return new SupportAccessPage(rows.stream().limit(20).toList(), rows.size() > 20 && page < 100);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public record AuditCommand(String action, String objectType, long objectId, Long actingUserId,
                               Object beforeSummary, Object afterSummary, String ip, String userAgent) {
    }
}

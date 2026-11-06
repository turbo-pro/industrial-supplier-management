package io.github.turbopro.ism.platform.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportSessionService {
    private final TenantSupportService authorization;
    private final SupportRequestMapper requests;
    private final TenantSupportMapper consent;
    private final SupportSessionMapper sessions;
    private final OperationIdGenerator ids;
    private final ObjectMapper json;
    private final AuditService audit;

    public SupportSessionService(TenantSupportService authorization, SupportRequestMapper requests,
                                 TenantSupportMapper consent, SupportSessionMapper sessions,
                                 OperationIdGenerator ids, ObjectMapper json, AuditService audit) {
        this.authorization = authorization;
        this.requests = requests;
        this.consent = consent;
        this.sessions = sessions;
        this.ids = ids;
        this.json = json;
        this.audit = audit;
    }

    /** Reserves a session record. Activation and credentials require a separate exchange. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long createPending(long requestId) {
        var actor = TenantContext.require();
        if (actor.tenantId() != 0 || !AuthorizationContext.require().hasAction("platform:support:request")) {
            throw new ApiException(CommonErrorCode.FORBIDDEN);
        }
        var grant = authorization.authorization(requestId);
        var request = requests.get(requestId);
        if (request.platformOperatorId() != actor.actorId()) {
            throw new ApiException(CommonErrorCode.FORBIDDEN);
        }
        var now = requests.now();
        if (!grant.authorizationReady() || !grant.expiresAt().isAfter(now)) {
            throw new ApiException(CommonErrorCode.RULE_BLOCKED, "支持授权当前不可用");
        }
        // authorization() holds the tenant and request locks until this transaction commits.
        long tenantId = request.targetTenantId();
        var policy = consent.policy(tenantId);
        var decision = consent.consent(tenantId, requestId, policy.version());
        long id = ids.nextId();
        sessions.insert(id, requestId, decision.id(), actor.actorId(), tenantId,
                decision.roleId(), serialize(grant.scope()), now, grant.expiresAt());
        audit.append(new AuditService.AuditCommand("SUPPORT_SESSION_RESERVE", "SUPPORT_SESSION", id,
                null, Map.of(), Map.of("requestId", Long.toString(requestId),
                "targetTenantId", Long.toString(tenantId)), null, null));
        return id;
    }

    private String serialize(TenantSupportModels.Scope scope) {
        try {
            return json.writeValueAsString(scope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize support scope", e);
        }
    }
}

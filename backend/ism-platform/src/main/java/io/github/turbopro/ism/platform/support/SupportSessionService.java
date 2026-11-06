package io.github.turbopro.ism.platform.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();
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
        var actor = requireOperator();
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String issueEntry(long sessionId, String browserSecret) {
        String browser = digest(browserSecret);
        var grant = revalidate(sessionId);
        var deadline = requests.now().plusSeconds(60);
        if (grant.expiresAt().isBefore(deadline)) {
            deadline = grant.expiresAt();
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (sessions.issue(sessionId, digest(token), browser, deadline) != 1) {
            throw unavailable();
        }
        return token;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void exchangeEntry(long sessionId, String token, String browserSecret) {
        String tokenDigest = digest(token);
        String browser = digest(browserSecret);
        revalidate(sessionId);
        if (sessions.consume(sessionId, tokenDigest, browser) != 1) {
            throw unavailable();
        }
        audit.append(new AuditService.AuditCommand("SUPPORT_SESSION_ACTIVATE", "SUPPORT_SESSION",
                sessionId, null, Map.of(), Map.of(), null, null));
    }

    private TenantSupportModels.Authorization revalidate(long sessionId) {
        var actor = requireOperator();
        var entry = sessions.entry(sessionId);
        if (entry == null || entry.operatorId() != actor.actorId()) {
            throw unavailable();
        }
        // Keep the same lock order as reservation and tenant consent changes.
        var grant = authorization.authorization(entry.requestId());
        if (!grant.authorizationReady()) {
            throw unavailable();
        }
        long tenantId = requests.get(entry.requestId()).targetTenantId();
        var policy = consent.policy(tenantId);
        var decision = consent.consent(tenantId, entry.requestId(), policy.version());
        if (decision.id() != entry.decisionId()) {
            throw unavailable();
        }
        var frozen = readScope(entry.scopeJson());
        var current = grant.scope();
        if (frozen.fields().stream().noneMatch(current.fields()::contains)
                || (!frozen.tenantAll() && !current.tenantAll()
                    && frozen.organizations().stream().noneMatch(current.organizations()::contains))) {
            throw unavailable();
        }
        return grant;
    }

    private TenantContext.Identity requireOperator() {
        var actor = TenantContext.require();
        if (actor.tenantId() != 0 || !AuthorizationContext.require().hasAction("platform:support:request")) {
            throw unavailable();
        }
        return actor;
    }

    private static ApiException unavailable() {
        return new ApiException(CommonErrorCode.FORBIDDEN, "支持会话或进入凭证不可用");
    }

    private static String digest(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
            throw unavailable();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private TenantSupportModels.Scope readScope(String scope) {
        try {
            return json.readValue(scope, TenantSupportModels.Scope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read frozen support scope", e);
        }
    }

    private String serialize(TenantSupportModels.Scope scope) {
        try {
            return json.writeValueAsString(scope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize support scope", e);
        }
    }
}

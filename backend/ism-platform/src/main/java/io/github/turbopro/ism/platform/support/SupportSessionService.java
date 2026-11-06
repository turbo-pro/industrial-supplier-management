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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String WORKSPACE_ENTRY = "workspace-entry-v1:";
    private static final String WORKSPACE_ACCESS = "workspace-access-v1:";
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
        return issueEntry(sessionId, browserSecret, "");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String issueWorkspaceEntry(long sessionId, String browserSecret) {
        return issueEntry(sessionId, browserSecret, WORKSPACE_ENTRY);
    }

    private String issueEntry(long sessionId, String browserSecret, String domain) {
        String browser = digest(browserSecret);
        var grant = revalidate(sessionId);
        var deadline = requests.now().plusSeconds(60);
        if (grant.expiresAt().isBefore(deadline)) {
            deadline = grant.expiresAt();
        }
        String token = randomSecret();
        if (sessions.issue(sessionId, digest(token, domain), browser, deadline) != 1) {
            throw unavailable();
        }
        return token;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String exchangeEntry(long sessionId, String token, String browserSecret) {
        String tokenDigest = digest(token);
        String browser = digest(browserSecret);
        revalidate(sessionId);
        String access = randomSecret();
        if (sessions.consume(sessionId, tokenDigest, browser, digest(access)) != 1) {
            throw unavailable();
        }
        audit.append(new AuditService.AuditCommand("SUPPORT_SESSION_ACTIVATE", "SUPPORT_SESSION",
                sessionId, null, Map.of(), Map.of(), null, null));
        return access;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String exchangeWorkspaceEntry(long sessionId, String token, String browserSecret) {
        String tokenDigest = digest(token, WORKSPACE_ENTRY);
        String browser = digest(browserSecret);
        var candidate = sessions.entryCandidate(sessionId, tokenDigest, browser);
        if (candidate == null) throw unavailable();
        workspaceGrant(candidate);
        String access = randomSecret();
        if (sessions.consume(sessionId, tokenDigest, browser, digest(access, WORKSPACE_ACCESS)) != 1) {
            throw unavailable();
        }
        try (var identity = TenantContext.open(0, candidate.operatorId())) {
            audit.append(new AuditService.AuditCommand("SUPPORT_SESSION_ACTIVATE", "SUPPORT_SESSION",
                    sessionId, null, Map.of(), Map.of("surface", "SUPPORT_WORKSPACE"), null, null));
        }
        return access;
    }

    public record WorkspaceIdentity(String realm, String sessionId, String operatorId, String operatorName,
                                    String tenantId, String tenantName, java.time.LocalDateTime expiresAt,
                                    TenantSupportModels.Scope scope) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkspaceIdentity workspaceIdentity(long sessionId, String access) {
        String hash = digest(access, WORKSPACE_ACCESS);
        var entry = sessions.workspaceCandidate(sessionId, hash);
        if (entry == null) throw unavailable();
        var grant = workspaceGrant(entry);
        var request = requests.get(entry.requestId());
        if (sessions.lockActive(sessionId, request.targetTenantId(), hash) == null) throw unavailable();
        return new WorkspaceIdentity("SUPPORT_WORKSPACE", Long.toString(sessionId), Long.toString(entry.operatorId()),
                request.operatorName(), Long.toString(request.targetTenantId()), request.tenantName(),
                grant.expiresAt(), grant.scope());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void exitWorkspace(long sessionId, String access) {
        String hash = digest(access, WORKSPACE_ACCESS);
        var entry = sessions.workspaceCandidate(sessionId, hash);
        if (entry == null) throw unavailable();
        var request = requests.get(entry.requestId());
        if (sessions.lockActive(sessionId, request.targetTenantId(), hash) == null
                || sessions.revoke(sessionId, entry.operatorId()) != 1) throw unavailable();
        try (var identity = TenantContext.open(0, entry.operatorId())) {
            audit.append(new AuditService.AuditCommand("SUPPORT_SESSION_REVOKE", "SUPPORT_SESSION",
                    sessionId, null, Map.of(), Map.of("surface", "SUPPORT_WORKSPACE"), null, null));
        }
    }

    private TenantSupportModels.Authorization workspaceGrant(SupportSessionMapper.Entry entry) {
        // IAM needs a narrowly scoped caller for role lookup; never install a login or permission snapshot.
        try (var lookup = TenantContext.open(0, entry.operatorId())) {
            return boundedGrant(entry, authorization.workspaceAuthorization(entry.requestId(), entry.operatorId()));
        }
    }

    /** Business callers must hold the transaction through the protected read and its audit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public TenantSupportModels.Scope requireAccess(long sessionId, long targetTenantId, String access) {
        String digest = digest(access);
        var grant = revalidate(sessionId);
        if (sessions.lockActive(sessionId, targetTenantId, digest) == null) {
            throw unavailable();
        }
        return grant.scope();
    }

    @Transactional
    public void revoke(long sessionId) {
        var actor = requireOperator();
        if (sessions.revoke(sessionId, actor.actorId()) != 1) {
            if (sessions.lockRevoked(sessionId, actor.actorId()) != null) return;
            throw unavailable();
        }
        audit.append(new AuditService.AuditCommand("SUPPORT_SESSION_REVOKE", "SUPPORT_SESSION",
                sessionId, null, Map.of(), Map.of(), null, null));
    }

    private TenantSupportModels.Authorization revalidate(long sessionId) {
        var actor = requireOperator();
        var entry = sessions.entry(sessionId);
        if (entry == null || entry.operatorId() != actor.actorId()) {
            throw unavailable();
        }
        // Keep the same lock order as reservation and tenant consent changes.
        return boundedGrant(entry, authorization.authorization(entry.requestId()));
    }

    private TenantSupportModels.Authorization boundedGrant(SupportSessionMapper.Entry entry,
                                                           TenantSupportModels.Authorization grant) {
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
        var fields = new HashSet<>(frozen.fields());
        fields.retainAll(current.fields());
        boolean all = frozen.tenantAll() && current.tenantAll();
        var organizations = new HashSet<>(frozen.tenantAll() ? current.organizations() : frozen.organizations());
        if (!frozen.tenantAll() && !current.tenantAll()) {
            organizations.retainAll(current.organizations());
        }
        if (fields.isEmpty() || (!all && organizations.isEmpty())) {
            throw unavailable();
        }
        var effective = new TenantSupportModels.Scope(all, Set.copyOf(organizations), Set.copyOf(fields));
        return new TenantSupportModels.Authorization(grant.requestId(), true, false,
                grant.reason(), effective, grant.expiresAt());
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
        return digest(value, "");
    }

    private static String digest(String value, String domain) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
            throw unavailable();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((domain + value).getBytes(StandardCharsets.US_ASCII)));
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

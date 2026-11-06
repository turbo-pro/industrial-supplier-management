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
import java.util.List;
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
    private final SupportRequestService requestVisibility;
    private final TenantSupportMapper consent;
    private final SupportSessionMapper sessions;
    private final OperationIdGenerator ids;
    private final ObjectMapper json;
    private final AuditService audit;

    public SupportSessionService(TenantSupportService authorization, SupportRequestMapper requests,
                                 SupportRequestService requestVisibility,
                                 TenantSupportMapper consent, SupportSessionMapper sessions,
                                 OperationIdGenerator ids, ObjectMapper json, AuditService audit) {
        this.authorization = authorization;
        this.requests = requests;
        this.requestVisibility = requestVisibility;
        this.consent = consent;
        this.sessions = sessions;
        this.ids = ids;
        this.json = json;
        this.audit = audit;
    }

    public record SessionSummary(String id, String state, java.time.LocalDateTime createdAt,
                                 java.time.LocalDateTime expiresAt, java.time.LocalDateTime revokedAt) {}
    public record SessionPage(List<SessionSummary> items, String nextBefore) {}

    @Transactional(readOnly = true)
    public SessionPage listOwn(long requestId, Long before) {
        var actor = requireOperator();
        var request = requestVisibility.get(requestId);
        if (!request.operatorId().equals(Long.toString(actor.actorId()))) {
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        }
        if (before != null && before <= 0) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        var cursorTime = before == null ? null : sessions.cursorTime(requestId, actor.actorId(), before);
        if (before != null && cursorTime == null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        var rows = sessions.listOwn(requestId, actor.actorId(), before, cursorTime);
        var items = rows.stream().limit(20).map(row -> new SessionSummary(Long.toString(row.id()), row.state(),
                row.createdAt(), row.expiresAt(), row.revokedAt())).toList();
        return new SessionPage(items, rows.size() > 20 ? items.get(19).id() : null);
    }

    @Transactional(readOnly = true)
    public AuditService.SupportAccessPage listOwnAccess(long sessionId, int page) {
        var actor = requireOperator();
        var entry = sessions.entry(sessionId);
        if (entry == null || entry.operatorId() != actor.actorId()) throw new ApiException(CommonErrorCode.NOT_FOUND);
        var request = requestVisibility.get(entry.requestId());
        return audit.ownSupportAccess(sessionId, Long.parseLong(request.tenantId()), page);
    }

    public record AuditSessionPage(List<SupportSessionMapper.AuditSummary> items, boolean hasMore) {}

    private void requireAuditor() {
        if (TenantContext.require().tenantId() != 0 || !AuthorizationContext.require().hasAction("platform:support:audit")) {
            throw unavailable();
        }
    }

    @Transactional(readOnly = true)
    public AuditSessionPage auditSessions(int page) {
        requireAuditor();
        if (page < 0 || page > 100) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        var rows = sessions.auditSessions(page * 20);
        return new AuditSessionPage(rows.stream().limit(20).toList(), rows.size() > 20 && page < 100);
    }

    @Transactional(readOnly = true)
    public AuditService.SupportAccessPage auditAccess(long sessionId, int page) {
        requireAuditor();
        var entry = sessions.entry(sessionId);
        if (entry == null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        return audit.auditedSupportAccess(sessionId, requests.get(entry.requestId()).targetTenantId(), entry.operatorId(), page);
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void auditWorkspaceReadDenied(long sessionId, String access, long supplierId) {
        var candidate = sessions.workspaceCandidate(sessionId, digest(access, WORKSPACE_ACCESS));
        if (candidate == null) return; // Unverified credentials must not attribute actions to an operator.
        var request = requests.get(candidate.requestId());
        try (var operator = TenantContext.open(0, candidate.operatorId())) {
            audit.append(new AuditService.AuditCommand("SUPPORT_SUPPLIER_READ_DENIED", "SUPPLIER", supplierId,
                    null, Map.of(), Map.of("sessionId", Long.toString(sessionId),
                    "targetTenantId", Long.toString(request.targetTenantId()), "result", "DENIED",
                    "surface", "SUPPORT_WORKSPACE"), null, null));
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
        long actualTenantId = requests.get(Long.parseLong(grant.requestId())).targetTenantId();
        if (sessions.lockActive(sessionId, actualTenantId, digest) == null) {
            throw unavailable();
        }
        if (targetTenantId != actualTenantId) throw new TenantMismatch(actualTenantId);
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

    @Transactional
    public void terminate(long sessionId, String reason) {
        var actor = TenantContext.require();
        if (actor.tenantId() != 0 || !AuthorizationContext.require().hasAction("platform:support:terminate")) {
            throw unavailable();
        }
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        }
        var state = sessions.lockState(sessionId);
        if (state == null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if ("REVOKED".equals(state)) return;
        var entry = sessions.entry(sessionId);
        if (sessions.revoke(sessionId, entry.operatorId()) != 1) throw unavailable();
        audit.append(new AuditService.AuditCommand("SUPPORT_SESSION_TERMINATE", "SUPPORT_SESSION",
                sessionId, null, Map.of(), Map.of("reason", reason.strip(),
                "originalOperatorId", Long.toString(entry.operatorId()),
                "targetTenantId", Long.toString(requests.get(entry.requestId()).targetTenantId())), null, null));
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

    static final class TenantMismatch extends ApiException {
        private final long actualTenantId;
        TenantMismatch(long actualTenantId) {
            super(CommonErrorCode.FORBIDDEN, "支持会话或进入凭证不可用");
            this.actualTenantId = actualTenantId;
        }
        long actualTenantId() { return actualTenantId; }
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

package io.github.turbopro.ism.platform.support;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console")
@ConditionalOnProperty(name = "ism.support.sessions.enabled", havingValue = "true")
public class SupportSessionController {
    private static final Logger LOG = LoggerFactory.getLogger(SupportSessionController.class);
    private static final String SECRET = "[A-Za-z0-9_-]{43}";
    private final SupportSessionService sessions;
    private final SupportSupplierReadService suppliers;
    private final ApiResponseFactory responses;
    private final AuditService audit;

    public SupportSessionController(SupportSessionService sessions, SupportSupplierReadService suppliers,
                                    ApiResponseFactory responses, AuditService audit) {
        this.sessions = sessions;
        this.suppliers = suppliers;
        this.responses = responses;
        this.audit = audit;
    }

    public record Entry(@NotBlank @Pattern(regexp = SECRET) String browserSecret) {}
    public record Exchange(@NotBlank @Pattern(regexp = SECRET) String browserSecret,
                           @NotBlank @Pattern(regexp = SECRET) String entryToken) {}
    public record Access(@NotBlank @Pattern(regexp = SECRET) String accessSecret) {}

    @RequiresPermission("platform:support:request")
    @GetMapping("/support-sessions/{sessionId}/access-logs")
    ResponseEntity<ApiResponse<AuditService.SupportAccessPage>> accessLogs(@PathVariable long sessionId,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(sessions.listOwnAccess(sessionId, page)));
    }

    @RequiresPermission("platform:support:request")
    @GetMapping("/support-requests/{requestId}/sessions")
    ResponseEntity<ApiResponse<SupportSessionService.SessionPage>> list(@PathVariable long requestId,
            @RequestParam(required = false) Long before) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(sessions.listOwn(requestId, before)));
    }

    @RequiresPermission("platform:support:request")
    @PostMapping("/support-requests/{requestId}/sessions")
    ResponseEntity<ApiResponse<Map<String, String>>> create(@PathVariable long requestId) {
        return reply(Map.of("sessionId", Long.toString(sessions.createPending(requestId))));
    }

    @RequiresPermission("platform:support:request")
    @PostMapping("/support-sessions/{sessionId}/entry-token")
    ResponseEntity<ApiResponse<Map<String, String>>> issue(@PathVariable long sessionId,
                                                          @Valid @RequestBody Entry body) {
        return reply(Map.of("entryToken", sessions.issueEntry(sessionId, body.browserSecret())));
    }

    @RequiresPermission("platform:support:request")
    @PostMapping("/support-sessions/{sessionId}/workspace-entry-token")
    ResponseEntity<ApiResponse<Map<String, String>>> issueWorkspace(@PathVariable long sessionId,
                                                                    @Valid @RequestBody Entry body) {
        return reply(Map.of("entryToken", sessions.issueWorkspaceEntry(sessionId, body.browserSecret())));
    }

    @RequiresPermission("platform:support:request")
    @PostMapping("/support-sessions/{sessionId}/exchange")
    ResponseEntity<ApiResponse<Map<String, String>>> exchange(@PathVariable long sessionId,
                                                             @Valid @RequestBody Exchange body) {
        return reply(Map.of("accessSecret", sessions.exchangeEntry(sessionId, body.entryToken(), body.browserSecret())));
    }

    @RequiresPermission("platform:support:request")
    @PostMapping("/support-sessions/{sessionId}/tenants/{tenantId}/suppliers/{supplierId}/read")
    ResponseEntity<ApiResponse<Map<String, String>>> read(@PathVariable long sessionId,
            @PathVariable long tenantId, @PathVariable long supplierId, @Valid @RequestBody Access body) {
        try {
            return reply(suppliers.read(sessionId, tenantId, body.accessSecret(), supplierId));
        } catch (ApiException failure) {
            if (failure.errorCode() == CommonErrorCode.FORBIDDEN && TenantContext.require().tenantId() == 0) {
                // The read transaction has rolled back. Persist the attempt independently in the Console realm.
                if (failure instanceof SupportSessionService.TenantMismatch mismatch) {
                    audit.append(new AuditService.AuditCommand("SUPPORT_SUPPLIER_READ_DENIED", "SUPPLIER", supplierId,
                            null, Map.of(), Map.of("sessionId", Long.toString(sessionId),
                            "targetTenantId", Long.toString(mismatch.actualTenantId()), "requestedTenantId", Long.toString(tenantId),
                            "reason", "TENANT_MISMATCH", "result", "DENIED", "surface", "CONSOLE"), null, null));
                    LOG.warn("SUPPORT_TENANT_MISMATCH operatorId={} sessionId={} expectedTenantId={} requestedTenantId={}",
                            TenantContext.require().actorId(), sessionId, mismatch.actualTenantId(), tenantId);
                } else {
                    audit.append(new AuditService.AuditCommand("SUPPORT_SUPPLIER_READ_DENIED", "SUPPLIER", supplierId,
                            null, Map.of(), Map.of("requestedSessionId", Long.toString(sessionId),
                            "requestedTenantId", Long.toString(tenantId), "result", "DENIED"), null, null));
                }
            }
            throw failure;
        }
    }

    @RequiresPermission("platform:support:request")
    @PostMapping("/support-sessions/{sessionId}/revoke")
    ResponseEntity<ApiResponse<Map<String, String>>> revoke(@PathVariable long sessionId) {
        sessions.revoke(sessionId);
        return reply(Map.of("status", "REVOKED"));
    }

    @RequiresPermission("platform:support:audit")
    @GetMapping("/support-audit/sessions")
    ResponseEntity<ApiResponse<SupportSessionService.AuditSessionPage>> auditSessions(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(sessions.auditSessions(page)));
    }

    @RequiresPermission("platform:support:audit")
    @GetMapping("/support-audit/sessions/{sessionId}/access-logs")
    ResponseEntity<ApiResponse<AuditService.SupportAccessPage>> auditAccess(@PathVariable long sessionId,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(sessions.auditAccess(sessionId, page)));
    }

    @RequiresPermission("platform:support:audit")
    @GetMapping("/support-audit/sessions/{sessionId}/lifecycle")
    ResponseEntity<ApiResponse<AuditService.SupportLifecyclePage>> lifecycle(@PathVariable long sessionId,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(sessions.auditLifecycle(sessionId, page)));
    }

    public record Termination(@NotBlank @jakarta.validation.constraints.Size(max = 1000) String reason) {}

    @RequiresPermission("platform:support:terminate")
    @PostMapping("/support-sessions/{sessionId}/terminate")
    ResponseEntity<ApiResponse<Map<String, String>>> terminate(@PathVariable long sessionId,
            @Valid @RequestBody Termination body) {
        sessions.terminate(sessionId, body.reason());
        return reply(Map.of("status", "REVOKED"));
    }

    private ResponseEntity<ApiResponse<Map<String, String>>> reply(Map<String, String> data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(data));
    }
}

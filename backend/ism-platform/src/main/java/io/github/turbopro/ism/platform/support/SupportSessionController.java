package io.github.turbopro.ism.platform.support;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console")
@ConditionalOnProperty(name = "ism.support.sessions.enabled", havingValue = "true")
public class SupportSessionController {
    private static final String SECRET = "[A-Za-z0-9_-]{43}";
    private final SupportSessionService sessions;
    private final SupportSupplierReadService suppliers;
    private final ApiResponseFactory responses;

    public SupportSessionController(SupportSessionService sessions, SupportSupplierReadService suppliers,
                                    ApiResponseFactory responses) {
        this.sessions = sessions;
        this.suppliers = suppliers;
        this.responses = responses;
    }

    public record Entry(@NotBlank @Pattern(regexp = SECRET) String browserSecret) {}
    public record Exchange(@NotBlank @Pattern(regexp = SECRET) String browserSecret,
                           @NotBlank @Pattern(regexp = SECRET) String entryToken) {}
    public record Access(@NotBlank @Pattern(regexp = SECRET) String accessSecret) {}

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
    @PostMapping("/support-sessions/{sessionId}/exchange")
    ResponseEntity<ApiResponse<Map<String, String>>> exchange(@PathVariable long sessionId,
                                                             @Valid @RequestBody Exchange body) {
        return reply(Map.of("accessSecret", sessions.exchangeEntry(sessionId, body.entryToken(), body.browserSecret())));
    }

    @RequiresPermission("platform:support:request")
    @PostMapping("/support-sessions/{sessionId}/tenants/{tenantId}/suppliers/{supplierId}/read")
    ResponseEntity<ApiResponse<Map<String, String>>> read(@PathVariable long sessionId,
            @PathVariable long tenantId, @PathVariable long supplierId, @Valid @RequestBody Access body) {
        return reply(suppliers.read(sessionId, tenantId, body.accessSecret(), supplierId));
    }

    @RequiresPermission("platform:support:request")
    @PostMapping("/support-sessions/{sessionId}/revoke")
    ResponseEntity<ApiResponse<Map<String, String>>> revoke(@PathVariable long sessionId) {
        sessions.revoke(sessionId);
        return reply(Map.of("status", "REVOKED"));
    }

    private ResponseEntity<ApiResponse<Map<String, String>>> reply(Map<String, String> data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(data));
    }
}

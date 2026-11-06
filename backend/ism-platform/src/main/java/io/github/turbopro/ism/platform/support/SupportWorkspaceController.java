package io.github.turbopro.ism.platform.support;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support-workspace/{sessionId}")
@ConditionalOnProperty(name = "ism.support.sessions.enabled", havingValue = "true")
public class SupportWorkspaceController {
    private final SupportSessionService sessions;
    private final ApiResponseFactory responses;
    private final SupportSupplierReadService suppliers;

    public SupportWorkspaceController(SupportSessionService sessions, ApiResponseFactory responses,
                                      SupportSupplierReadService suppliers) {
        this.sessions = sessions;
        this.responses = responses;
        this.suppliers = suppliers;
    }

    @PostMapping("/exchange")
    ResponseEntity<ApiResponse<Map<String, String>>> exchange(@PathVariable long sessionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SupportSessionController.Exchange body) {
        requireDedicatedIdentity(authorization);
        return reply(Map.of("accessSecret", sessions.exchangeWorkspaceEntry(sessionId, body.entryToken(), body.browserSecret())));
    }

    @PostMapping("/identity")
    ResponseEntity<ApiResponse<SupportSessionService.WorkspaceIdentity>> identity(@PathVariable long sessionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SupportSessionController.Access body) {
        requireDedicatedIdentity(authorization);
        return reply(sessions.workspaceIdentity(sessionId, body.accessSecret()));
    }

    @PostMapping("/suppliers/{supplierId}/read")
    ResponseEntity<ApiResponse<Map<String, String>>> read(@PathVariable long sessionId, @PathVariable long supplierId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SupportSessionController.Access body) {
        requireDedicatedIdentity(authorization);
        try {
            return reply(suppliers.readWorkspace(sessionId, body.accessSecret(), supplierId));
        } catch (ApiException failure) {
            if (failure.errorCode() == CommonErrorCode.FORBIDDEN) {
                sessions.auditWorkspaceReadDenied(sessionId, body.accessSecret(), supplierId);
            }
            throw failure;
        }
    }

    @PostMapping("/exit")
    ResponseEntity<ApiResponse<Map<String, String>>> exit(@PathVariable long sessionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SupportSessionController.Access body) {
        requireDedicatedIdentity(authorization);
        sessions.exitWorkspace(sessionId, body.accessSecret());
        return reply(Map.of("status", "REVOKED"));
    }

    private static void requireDedicatedIdentity(String authorization) {
        if (authorization != null) throw new ApiException(CommonErrorCode.FORBIDDEN);
    }

    private <T> ResponseEntity<ApiResponse<T>> reply(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(data));
    }
}

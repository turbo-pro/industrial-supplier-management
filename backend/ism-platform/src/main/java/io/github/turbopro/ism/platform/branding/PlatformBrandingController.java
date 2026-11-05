package io.github.turbopro.ism.platform.branding;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.operation.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/console")
public class PlatformBrandingController {
    private final PlatformBrandingService service;
    private final ApiResponseFactory responses;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    public PlatformBrandingController(PlatformBrandingService service, ApiResponseFactory responses,
                                      IdempotencyService idempotency, ObjectMapper json) {
        this.service = service;
        this.responses = responses;
        this.idempotency = idempotency;
        this.json = json;
    }

    @GetMapping("/branding")
    ApiResponse<PlatformBrandingModels.Branding> publicBranding() {
        return responses.success(service.publicBranding());
    }

    @GetMapping("/settings/branding")
    @RequiresPermission("platform:branding:view")
    ApiResponse<PlatformBrandingModels.Settings> get() {
        return responses.success(service.settings());
    }

    @PutMapping("/settings/branding")
    @RequiresPermission("platform:branding:manage")
    ApiResponse<PlatformBrandingModels.Settings> save(@RequestHeader("Idempotency-Key") String key,
                                                     @Valid @RequestBody PlatformBrandingModels.Save command) {
        String result = idempotency.execute("PLATFORM_BRANDING_UPDATE", key, write(command),
                Duration.ofHours(24), () -> write(service.save(command)));
        try {
            return responses.success(json.readValue(result, PlatformBrandingModels.Settings.class));
        } catch (Exception invalidReceipt) {
            throw new IllegalStateException(invalidReceipt);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception invalidValue) {
            throw new IllegalStateException(invalidValue);
        }
    }
}

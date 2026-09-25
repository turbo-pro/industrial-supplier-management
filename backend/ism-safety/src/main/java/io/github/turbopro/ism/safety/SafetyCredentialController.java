package io.github.turbopro.ism.safety;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/safety")
public class SafetyCredentialController {
    private final SafetyCredentialService service;
    private final ApiResponseFactory responses;

    public SafetyCredentialController(SafetyCredentialService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping("/credentials")
    @RequiresPermission("safety:credential:view")
    public ApiResponse<SafetyCredentialModels.Page> list(
            @RequestParam(defaultValue = "") String personId,
            @RequestParam(defaultValue = "") String kind,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return responses.success(service.list(personId, kind, status, page, size));
    }

    @PostMapping("/credentials")
    @RequiresPermission("safety:credential:create")
    public ApiResponse<SafetyCredentialModels.View> create(@Valid @RequestBody SafetyCredentialModels.Create command) {
        return responses.success(service.create(command));
    }

    @PostMapping("/credentials/{id}/review")
    @RequiresPermission("safety:credential:review")
    public ApiResponse<SafetyCredentialModels.View> review(@PathVariable long id,
                                                            @Valid @RequestBody SafetyCredentialModels.Review command) {
        return responses.success(service.review(id, command));
    }

    @GetMapping("/persons/{personId}/eligibility")
    @RequiresPermission("safety:credential:view")
    public ApiResponse<SafetyCredentialModels.Eligibility> eligibility(@PathVariable long personId) {
        return responses.success(service.eligibility(personId));
    }
}

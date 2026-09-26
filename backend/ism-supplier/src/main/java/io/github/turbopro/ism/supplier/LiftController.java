package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/supplier-blacklist/{caseId}/lift-applications")
public class LiftController {
    private final LiftService service;private final ApiResponseFactory responses;
    public LiftController(LiftService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("supplier:lift:view")
    public ApiResponse<LiftModels.Page> list(@PathVariable long caseId,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.list(caseId,page,size));}
    @GetMapping("/readiness") @RequiresPermission("supplier:lift:view")
    public ApiResponse<LiftModels.Readiness> readiness(@PathVariable long caseId){return responses.success(service.readiness(caseId));}
    @PostMapping @RequiresPermission("supplier:lift:create")
    public ApiResponse<LiftModels.View> create(@PathVariable long caseId,@Valid @RequestBody LiftModels.Create command){return responses.success(service.create(caseId,command));}
    @PostMapping("/{id}/review") @RequiresPermission("supplier:lift:review")
    public ApiResponse<LiftModels.View> review(@PathVariable long caseId,@PathVariable long id,@Valid @RequestBody LiftModels.Review command){return responses.success(service.review(caseId,id,command));}
}

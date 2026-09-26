package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/supplier-blacklist/{caseId}/appeals")
public class AppealController {
    private final AppealService service;private final ApiResponseFactory responses;
    public AppealController(AppealService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("supplier:appeal:view")
    public ApiResponse<AppealModels.Page> list(@PathVariable long caseId,@RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.list(caseId,page,size));}
    @PostMapping @RequiresPermission("supplier:appeal:create")
    public ApiResponse<AppealModels.View> create(@PathVariable long caseId,@Valid @RequestBody AppealModels.Create command){return responses.success(service.create(caseId,command));}
    @PostMapping("/{id}/review") @RequiresPermission("supplier:appeal:review")
    public ApiResponse<AppealModels.View> review(@PathVariable long caseId,@PathVariable long id,@Valid @RequestBody AppealModels.Review command){return responses.success(service.review(caseId,id,command));}
}

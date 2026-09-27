package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers/{supplierId}/restriction-explanation")
public class RestrictionExplanationController {
    private final RestrictionExplanationService service;
    private final ApiResponseFactory responses;
    public RestrictionExplanationController(RestrictionExplanationService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @PostMapping @RequiresPermission("supplier:restriction:explain")
    public ApiResponse<RestrictionExplanationService.View> explain(@PathVariable long supplierId,
        @RequestParam RestrictionExplanationService.Action action){return responses.success(service.explain(supplierId,action));}
}

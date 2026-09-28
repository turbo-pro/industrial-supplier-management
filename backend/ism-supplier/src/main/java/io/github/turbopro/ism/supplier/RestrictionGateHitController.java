package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/suppliers/{supplierId}/restriction-gate-hits")
public class RestrictionGateHitController {
    private final RestrictionGateHitService service;private final ApiResponseFactory responses;
    public RestrictionGateHitController(RestrictionGateHitService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("supplier:restriction:explain")
    public ApiResponse<RestrictionGateHitService.Page> list(@PathVariable long supplierId,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.list(supplierId,page,size));}
}

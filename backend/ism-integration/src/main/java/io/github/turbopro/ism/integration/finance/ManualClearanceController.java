package io.github.turbopro.ism.integration.finance;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers/{id}/financial-clearance")
public class ManualClearanceController {
    private final ManualClearanceService service;
    private final ApiResponseFactory responses;
    public ManualClearanceController(ManualClearanceService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("supplier:finance:view")
    public ApiResponse<ManualClearanceModels.View> get(@PathVariable long id){return responses.success(service.get(id));}
    @PostMapping @RequiresPermission("supplier:finance:submit")
    public ApiResponse<ManualClearanceModels.View> submit(@PathVariable long id,@Valid @RequestBody ManualClearanceModels.Submit command){return responses.success(service.submit(id,command));}
    @PostMapping("/review") @RequiresPermission("supplier:finance:review")
    public ApiResponse<ManualClearanceModels.View> review(@PathVariable long id,@Valid @RequestBody ManualClearanceModels.Review command){return responses.success(service.review(id,command));}
}

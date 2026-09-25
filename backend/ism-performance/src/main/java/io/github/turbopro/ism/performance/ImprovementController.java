package io.github.turbopro.ism.performance;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/performance/evaluations/{evaluationId}/improvement")
public class ImprovementController {
    private final ImprovementService service;
    private final ApiResponseFactory responses;
    public ImprovementController(ImprovementService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("performance:evaluation:view")
    public ApiResponse<ImprovementModels.View> get(@PathVariable long evaluationId){return responses.success(service.get(evaluationId));}
    @PostMapping @RequiresPermission("performance:evaluation:manage")
    public ApiResponse<ImprovementModels.View> create(@PathVariable long evaluationId,@Valid @RequestBody ImprovementModels.Create command){return responses.success(service.create(evaluationId,command));}
    @PostMapping("/submit") @RequiresPermission("performance:evaluation:manage")
    public ApiResponse<ImprovementModels.View> submit(@PathVariable long evaluationId,@Valid @RequestBody ImprovementModels.Submit command){return responses.success(service.submit(evaluationId,command));}
    @PostMapping("/review") @RequiresPermission("performance:evaluation:review")
    public ApiResponse<ImprovementModels.View> review(@PathVariable long evaluationId,@Valid @RequestBody ImprovementModels.Review command){return responses.success(service.review(evaluationId,command));}
}

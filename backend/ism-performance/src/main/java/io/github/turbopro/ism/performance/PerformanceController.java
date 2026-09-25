package io.github.turbopro.ism.performance;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/performance")
public class PerformanceController {
    private final PerformanceService service;
    private final ApiResponseFactory responses;
    public PerformanceController(PerformanceService service, ApiResponseFactory responses) {
        this.service = service; this.responses = responses;
    }
    @GetMapping("/rule") @RequiresPermission("performance:evaluation:view")
    public ApiResponse<PerformanceModels.Rule> rule() { return responses.success(service.rule()); }
    @PutMapping("/rule") @RequiresPermission("performance:rule:manage")
    public ApiResponse<PerformanceModels.Rule> saveRule(@Valid @RequestBody PerformanceModels.SaveRule command) {
        return responses.success(service.saveRule(command));
    }
    @GetMapping("/evaluations") @RequiresPermission("performance:evaluation:view")
    public ApiResponse<PerformanceModels.Page> list(@RequestParam(defaultValue = "") String supplierId,
                                                     @RequestParam(defaultValue = "") String status,
                                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return responses.success(service.list(supplierId, status, page, size));
    }
    @GetMapping("/evaluations/{id}") @RequiresPermission("performance:evaluation:view")
    public ApiResponse<PerformanceModels.View> get(@PathVariable long id) { return responses.success(service.get(id)); }
    @GetMapping("/evaluations/{id}/events") @RequiresPermission("performance:evaluation:view")
    public ApiResponse<List<PerformanceModels.Event>> events(@PathVariable long id) {
        return responses.success(service.events(id));
    }
    @PostMapping("/evaluations") @RequiresPermission("performance:evaluation:manage")
    public ApiResponse<PerformanceModels.View> create(@Valid @RequestBody PerformanceModels.SaveEvaluation command) {
        return responses.success(service.create(command));
    }
    @PutMapping("/evaluations/{id}") @RequiresPermission("performance:evaluation:manage")
    public ApiResponse<PerformanceModels.View> update(@PathVariable long id,
                                                       @Valid @RequestBody PerformanceModels.SaveEvaluation command) {
        return responses.success(service.update(id, command));
    }
    @PostMapping("/evaluations/{id}/submit") @RequiresPermission("performance:evaluation:manage")
    public ApiResponse<PerformanceModels.View> submit(@PathVariable long id,
                                                       @RequestParam @Min(0) int version) {
        return responses.success(service.submit(id, version));
    }
    @PostMapping("/evaluations/{id}/review") @RequiresPermission("performance:evaluation:review")
    public ApiResponse<PerformanceModels.View> review(@PathVariable long id,
                                                       @Valid @RequestBody PerformanceModels.Review command) {
        return responses.success(service.review(id, command));
    }
}

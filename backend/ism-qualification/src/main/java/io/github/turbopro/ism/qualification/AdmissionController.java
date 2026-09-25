package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/admissions")
public class AdmissionController {
    private final AdmissionService service;private final ApiResponseFactory responses;
    public AdmissionController(AdmissionService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("supplier:admission:view") ApiResponse<AdmissionModels.Page> list(@RequestParam(defaultValue="") @Size(max=100) String keyword,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.list(keyword,status,page,size));}
    @GetMapping("/{id}") @RequiresPermission("supplier:admission:view") ApiResponse<AdmissionModels.View> get(@PathVariable long id){return responses.success(service.get(id));}
    @PostMapping @RequiresPermission("supplier:admission:apply") ApiResponse<AdmissionModels.View> create(@Valid @RequestBody AdmissionModels.SaveApplication command){return responses.success(service.create(command));}
    @PutMapping("/{id}") @RequiresPermission("supplier:admission:apply") ApiResponse<AdmissionModels.View> update(@PathVariable long id,@Valid @RequestBody AdmissionModels.SaveApplication command){return responses.success(service.update(id,command));}
    @PostMapping("/{id}/submit") @RequiresPermission("supplier:admission:apply") ApiResponse<AdmissionModels.View> submit(@PathVariable long id,@RequestParam @PositiveOrZero int version){return responses.success(service.submit(id,version));}
    @PostMapping("/{id}/review") @RequiresPermission("supplier:admission:review") ApiResponse<AdmissionModels.View> review(@PathVariable long id,@Valid @RequestBody AdmissionModels.ReviewCommand command){return responses.success(service.review(id,command));}
}

package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/supplier-blacklist")
public class BlacklistController {
    private final BlacklistService service;private final ApiResponseFactory responses;
    public BlacklistController(BlacklistService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("supplier:blacklist:view")
    public ApiResponse<BlacklistModels.Page> list(@RequestParam(defaultValue="") String supplierId,@RequestParam(defaultValue="") String status,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.list(supplierId,status,page,size));}
    @GetMapping("/{id}") @RequiresPermission("supplier:blacklist:view")
    public ApiResponse<BlacklistModels.View> get(@PathVariable long id){return responses.success(service.get(id));}
    @PostMapping @RequiresPermission("supplier:blacklist:manage")
    public ApiResponse<BlacklistModels.View> create(@Valid @RequestBody BlacklistModels.Create command){return responses.success(service.create(command));}
    @PostMapping("/{id}/submit") @RequiresPermission("supplier:blacklist:manage")
    public ApiResponse<BlacklistModels.View> submit(@PathVariable long id,@Valid @RequestBody BlacklistModels.Version command){return responses.success(service.submit(id,command));}
    @PostMapping("/{id}/review") @RequiresPermission("supplier:blacklist:review")
    public ApiResponse<BlacklistModels.View> review(@PathVariable long id,@Valid @RequestBody BlacklistModels.Review command){return responses.success(service.review(id,command));}
    @PostMapping("/{id}/revoke") @RequiresPermission("supplier:blacklist:review")
    public ApiResponse<BlacklistModels.View> revoke(@PathVariable long id,@Valid @RequestBody BlacklistModels.Revoke command){return responses.success(service.revoke(id,command));}
}

package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/suppliers/{supplierId}/exit-applications")
public class ExitController {
    private final ExitApplicationService service;private final ApiResponseFactory responses;
    public ExitController(ExitApplicationService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("supplier:exit:view")
    public ApiResponse<ExitModels.Page> list(@PathVariable long supplierId,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.list(supplierId,page,size));}
    @GetMapping("/{id}") @RequiresPermission("supplier:exit:view")
    public ApiResponse<ExitModels.View> get(@PathVariable long supplierId,@PathVariable long id){return responses.success(service.get(supplierId,id));}
    @GetMapping("/{id}/entities") @RequiresPermission("supplier:exit:view")
    public ApiResponse<ExitModels.EntityPage> entities(@PathVariable long supplierId,@PathVariable long id,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.entities(supplierId,id,page,size));}
    @PostMapping @RequiresPermission("supplier:exit:create")
    public ApiResponse<ExitModels.View> create(@PathVariable long supplierId,@Valid @RequestBody ExitModels.Create command){return responses.success(service.create(supplierId,command));}
    @PostMapping("/{id}/recheck") @RequiresPermission("supplier:exit:recheck")
    public ApiResponse<ExitModels.View> recheck(@PathVariable long supplierId,@PathVariable long id,@Valid @RequestBody ExitModels.Version command){return responses.success(service.recheck(supplierId,id,command));}
    @PostMapping("/{id}/entities/{entityId}/assign") @RequiresPermission("supplier:exit:assign")
    public ApiResponse<ExitModels.View> assign(@PathVariable long supplierId,@PathVariable long id,@PathVariable long entityId,@Valid @RequestBody ExitModels.Assign command){return responses.success(service.assign(supplierId,id,entityId,command));}
    @PostMapping("/{id}/cancel") @RequiresPermission("supplier:exit:cancel")
    public ApiResponse<ExitModels.View> cancel(@PathVariable long supplierId,@PathVariable long id,@Valid @RequestBody ExitModels.Version command){return responses.success(service.cancel(supplierId,id,command));}
    @PostMapping("/{id}/review") @RequiresPermission("supplier:exit:review")
    public ApiResponse<ExitModels.View> review(@PathVariable long supplierId,@PathVariable long id,@Valid @RequestBody ExitModels.Review command){return responses.success(service.review(supplierId,id,command));}
}

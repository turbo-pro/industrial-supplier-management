package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierService service;private final ApiResponseFactory responses;
    public SupplierController(SupplierService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("supplier:master:view") ApiResponse<SupplierModels.SupplierPage> list(@RequestParam(defaultValue="") @Size(max=100) String keyword,@RequestParam(defaultValue="") String status,@RequestParam(required=false) Long organizationId,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.list(keyword,status,organizationId,page,size));}
    @GetMapping("/{id}") @RequiresPermission("supplier:master:view") ApiResponse<SupplierModels.SupplierView> get(@PathVariable long id){return responses.success(service.get(id));}
    @PostMapping @RequiresPermission("supplier:master:create") ApiResponse<SupplierModels.SupplierView> create(@Valid @RequestBody SupplierModels.SaveSupplier command){return responses.success(service.create(command));}
    @PutMapping("/{id}") @RequiresPermission("supplier:master:update") ApiResponse<SupplierModels.SupplierView> update(@PathVariable long id,@Valid @RequestBody SupplierModels.SaveSupplier command){return responses.success(service.update(id,command));}
    @PostMapping("/{id}/status") @RequiresPermission("supplier:master:status") ApiResponse<SupplierModels.SupplierView> status(@PathVariable long id,@Valid @RequestBody SupplierModels.ChangeStatus command){return responses.success(service.changeStatus(id,command));}
}

package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class PurchaseCategoryController {
    private final PurchaseCategoryService service;private final ApiResponseFactory responses;
    public PurchaseCategoryController(PurchaseCategoryService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping("/api/supplier-purchase-categories") @RequiresPermission("supplier:master:view")
    ApiResponse<List<PurchaseCategoryModels.Category>> categories(){return responses.success(service.categories());}
    @PostMapping("/api/supplier-purchase-categories") @RequiresPermission("supplier:category:manage")
    ApiResponse<PurchaseCategoryModels.Category> create(@Valid @RequestBody PurchaseCategoryModels.Save command){return responses.success(service.create(command));}
    @PutMapping("/api/supplier-purchase-categories/{id}") @RequiresPermission("supplier:category:manage")
    ApiResponse<PurchaseCategoryModels.Category> update(@PathVariable long id,@Valid @RequestBody PurchaseCategoryModels.Save command){return responses.success(service.update(id,command));}
    @GetMapping("/api/supplier-purchase-categories/{id}/required-materials") @RequiresPermission("supplier:master:view")
    ApiResponse<PurchaseCategoryModels.MaterialPolicy> materialPolicy(@PathVariable long id){return responses.success(service.materialPolicy(id));}
    @PutMapping("/api/supplier-purchase-categories/{id}/required-materials") @RequiresPermission("supplier:category:manage")
    ApiResponse<PurchaseCategoryModels.MaterialPolicy> saveMaterialPolicy(@PathVariable long id,@Valid @RequestBody PurchaseCategoryModels.SaveRequiredMaterials command){return responses.success(service.saveMaterialPolicy(id,command));}
    @GetMapping("/api/suppliers/{id}/purchase-categories") @RequiresPermission("supplier:master:view")
    ApiResponse<PurchaseCategoryModels.Assignment> assignment(@PathVariable long id){return responses.success(service.assignment(id));}
    @PutMapping("/api/suppliers/{id}/purchase-categories") @RequiresPermission("supplier:master:update")
    ApiResponse<PurchaseCategoryModels.Assignment> assign(@PathVariable long id,@Valid @RequestBody PurchaseCategoryModels.Assign command){return responses.success(service.assign(id,command));}
}

package io.github.turbopro.ism.integration.table;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/table-views/{tableKey}")
public class TableViewController {
    private final TableViewService service;private final ApiResponseFactory responses;
    public TableViewController(TableViewService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("table:view:manage") public ApiResponse<TableViewModels.Page> list(@PathVariable String tableKey){return responses.success(service.list(tableKey));}
    @PostMapping("/shared") @RequiresPermission("table:view:publish") public ApiResponse<TableViewModels.View> createShared(@PathVariable String tableKey,@Valid @RequestBody TableViewModels.Save command){return responses.success(service.createShared(tableKey,command));}
    @PutMapping("/shared/{id}") @RequiresPermission("table:view:publish") public ApiResponse<TableViewModels.View> updateShared(@PathVariable String tableKey,@PathVariable long id,@Valid @RequestBody TableViewModels.Save command){return responses.success(service.updateShared(tableKey,id,command));}
    @DeleteMapping("/shared/{id}") @RequiresPermission("table:view:publish") public ApiResponse<Void> deleteShared(@PathVariable String tableKey,@PathVariable long id,@RequestParam @Min(0) int version){service.deleteShared(tableKey,id,version);return responses.success(null);}
    @PostMapping @RequiresPermission("table:view:manage") public ApiResponse<TableViewModels.View> create(@PathVariable String tableKey,@Valid @RequestBody TableViewModels.Save command){return responses.success(service.create(tableKey,command));}
    @PutMapping("/{id}") @RequiresPermission("table:view:manage") public ApiResponse<TableViewModels.View> update(@PathVariable String tableKey,@PathVariable long id,@Valid @RequestBody TableViewModels.Save command){return responses.success(service.update(tableKey,id,command));}
    @DeleteMapping("/{id}") @RequiresPermission("table:view:manage") public ApiResponse<Void> delete(@PathVariable String tableKey,@PathVariable long id,@RequestParam @Min(0) int version){service.delete(tableKey,id,version);return responses.success(null);}
}

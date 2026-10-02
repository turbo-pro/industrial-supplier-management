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
    private final ExitApplicationService service;private final ExitReminderFailureService failures;private final ExitArchiveService archives;private final ExitAccessRecoveryService recovery;private final ApiResponseFactory responses;
    public ExitController(ExitApplicationService service,ExitReminderFailureService failures,ExitArchiveService archives,ExitAccessRecoveryService recovery,ApiResponseFactory responses){this.service=service;this.failures=failures;this.archives=archives;this.recovery=recovery;this.responses=responses;}
    @GetMapping("/reminder-failures") @RequiresPermission("supplier:exit:view")
    public ApiResponse<ExitReminderFailureModels.Page> reminderFailures(@PathVariable long supplierId,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(failures.list(supplierId,page,size));}
    @GetMapping @RequiresPermission("supplier:exit:view")
    public ApiResponse<ExitModels.Page> list(@PathVariable long supplierId,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.list(supplierId,page,size));}
    @GetMapping("/{id}") @RequiresPermission("supplier:exit:view")
    public ApiResponse<ExitModels.View> get(@PathVariable long supplierId,@PathVariable long id){return responses.success(service.get(supplierId,id));}
    @GetMapping("/{id}/archive") @RequiresPermission("supplier:exit:view")
    public ApiResponse<ExitArchiveModels.View> archive(@PathVariable long supplierId,@PathVariable long id){return responses.success(archives.get(supplierId,id));}
    @GetMapping("/{id}/access-recovery") @RequiresPermission("supplier:exit:view")
    public ApiResponse<ExitAccessRecoveryModels.Inventory> accessRecovery(@PathVariable long supplierId,@PathVariable long id){return responses.success(recovery.inventory(supplierId,id));}
    @PostMapping("/{id}/access-recovery/{taskId}/discovery") @RequiresPermission("supplier:exit:review")
    public ApiResponse<ExitAccessRecoveryModels.Inventory> recordDiscovery(@PathVariable long supplierId,@PathVariable long id,@PathVariable long taskId,
        @Valid @RequestBody ExitAccessRecoveryModels.RecordFinding command){return responses.success(recovery.record(supplierId,id,taskId,command));}
    @PostMapping("/{id}/access-recovery/{taskId}/principals") @RequiresPermission("supplier:exit:review")
    public ApiResponse<ExitAccessRecoveryModels.Inventory> registerAccessPrincipal(@PathVariable long supplierId,@PathVariable long id,@PathVariable long taskId,
        @Valid @RequestBody ExitAccessRecoveryModels.RegisterPrincipal command){return responses.success(recovery.registerPrincipal(supplierId,id,taskId,command));}
    @PostMapping("/{id}/access-recovery/{taskId}/principals/{principalId}/void") @RequiresPermission("supplier:exit:review")
    public ApiResponse<ExitAccessRecoveryModels.Inventory> voidAccessPrincipal(@PathVariable long supplierId,@PathVariable long id,@PathVariable long taskId,@PathVariable long principalId,
        @Valid @RequestBody ExitAccessRecoveryModels.VoidPrincipal command){return responses.success(recovery.voidPrincipal(supplierId,id,taskId,principalId,command));}
    @PostMapping("/{id}/access-recovery/{taskId}/assign") @RequiresPermission("supplier:exit:assign")
    public ApiResponse<ExitAccessRecoveryModels.Inventory> assignAccessRecovery(@PathVariable long supplierId,@PathVariable long id,@PathVariable long taskId,
        @Valid @RequestBody ExitAccessRecoveryModels.Assign command){return responses.success(recovery.assign(supplierId,id,taskId,command));}
    @PostMapping("/{id}/access-recovery/{taskId}/remind") @RequiresPermission("supplier:exit:remind")
    public ApiResponse<ExitAccessRecoveryModels.Inventory> remindAccessRecovery(@PathVariable long supplierId,@PathVariable long id,@PathVariable long taskId,
        @Valid @RequestBody ExitAccessRecoveryModels.Remind command){return responses.success(recovery.remind(supplierId,id,taskId,command));}
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
    @PostMapping("/{id}/entities/{entityId}/deadline") @RequiresPermission("supplier:exit:assign")
    public ApiResponse<ExitModels.View> deadline(@PathVariable long supplierId,@PathVariable long id,@PathVariable long entityId,@Valid @RequestBody ExitModels.Deadline command){return responses.success(service.deadline(supplierId,id,entityId,command));}
    @PostMapping("/{id}/entities/{entityId}/remind") @RequiresPermission("supplier:exit:remind")
    public ApiResponse<ExitModels.View> remind(@PathVariable long supplierId,@PathVariable long id,@PathVariable long entityId,@Valid @RequestBody ExitModels.Reminder command){return responses.success(service.remind(supplierId,id,entityId,command));}
    @PostMapping("/{id}/review") @RequiresPermission("supplier:exit:review")
    public ApiResponse<ExitModels.View> review(@PathVariable long supplierId,@PathVariable long id,@Valid @RequestBody ExitModels.Review command){return responses.success(service.review(supplierId,id,command));}
}

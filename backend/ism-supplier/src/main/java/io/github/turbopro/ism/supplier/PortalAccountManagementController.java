package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.*;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers/{supplierId}/portal-accounts")
public class PortalAccountManagementController {
    public record Status(@NotNull @Min(0) Integer version,@NotNull @Pattern(regexp="ACTIVE|DISABLED") String status,@NotBlank @Size(max=500) String reason) {}
    public record Revoke(@NotNull @Min(0) Integer version,@NotBlank @Size(max=500) String reason) {}
    private final PortalAccountManagementService service;private final ApiResponseFactory responses;
    public PortalAccountManagementController(PortalAccountManagementService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("supplier:master:view")
    ResponseEntity<ApiResponse<PortalAccountManagementService.View>> list(@PathVariable long supplierId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(service.list(supplierId,page,size)));
    }
    @PostMapping("/{id}/status") @RequiresPermission("supplier:master:update")
    ResponseEntity<ApiResponse<Account>> status(@PathVariable long supplierId,@PathVariable long id,@Valid @RequestBody Status command){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(service.change(supplierId,id,command.version(),"ACTIVE".equals(command.status())?Action.ENABLE:Action.DISABLE,command.reason())));
    }
    @PostMapping("/{id}/revoke-sessions") @RequiresPermission("supplier:master:update")
    ResponseEntity<ApiResponse<Account>> revoke(@PathVariable long supplierId,@PathVariable long id,@Valid @RequestBody Revoke command){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(service.change(supplierId,id,command.version(),Action.REVOKE_SESSIONS,command.reason())));
    }
}

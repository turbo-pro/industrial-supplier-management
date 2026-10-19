package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers/{supplierId}/portal-invitations")
public class PortalInvitationController {
    private final PortalInvitationService service;private final ApiResponseFactory responses;
    public PortalInvitationController(PortalInvitationService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping @RequiresPermission("supplier:master:view")
    ApiResponse<PortalInvitationModels.History> history(@PathVariable long supplierId){return responses.success(service.history(supplierId));}
    @PostMapping @RequiresPermission("supplier:master:update")
    ResponseEntity<ApiResponse<PortalInvitationModels.Issued>> issue(@PathVariable long supplierId,@Valid @RequestBody PortalInvitationModels.Issue command){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(service.issue(supplierId,command)));
    }
    @PostMapping("/{id}/revoke") @RequiresPermission("supplier:master:update")
    ApiResponse<PortalInvitationModels.Invitation> revoke(@PathVariable long supplierId,@PathVariable long id,@Valid @RequestBody PortalInvitationModels.Revoke command){return responses.success(service.revoke(supplierId,id,command));}
}

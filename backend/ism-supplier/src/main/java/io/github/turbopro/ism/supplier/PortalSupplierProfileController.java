package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PortalSupplierProfileController {
    private final PortalSupplierProfileService service;private final ApiResponseFactory responses;
    public PortalSupplierProfileController(PortalSupplierProfileService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping("/api/portal/supplier-profile")
    ResponseEntity<ApiResponse<PortalSupplierProfileModels.View>> current(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(service.current()));
    }
}

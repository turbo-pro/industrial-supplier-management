package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProfileChangeController {
    private final ProfileChangeService service;private final ApiResponseFactory responses;
    public ProfileChangeController(ProfileChangeService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping("/api/portal/profile-changes") ResponseEntity<ApiResponse<ProfileChangeModels.Page>> portal(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return reply(service.portalList(page,size));}
    @PostMapping("/api/portal/profile-changes") ResponseEntity<ApiResponse<ProfileChangeModels.View>> submit(@Valid @RequestBody ProfileChangeModels.Submit c){return reply(service.submit(c));}
    @PostMapping("/api/portal/profile-changes/{id}/cancel") ResponseEntity<ApiResponse<ProfileChangeModels.View>> cancel(@PathVariable long id,@Valid @RequestBody ProfileChangeModels.Version c){return reply(service.cancel(id,c.version()));}
    @GetMapping("/api/suppliers/{supplierId}/profile-changes") @RequiresPermission("supplier:master:view") ApiResponse<ProfileChangeModels.Page> employee(@PathVariable long supplierId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return responses.success(service.employeeList(supplierId,page,size));}
    @PostMapping("/api/suppliers/{supplierId}/profile-changes/{id}/review") @RequiresPermission("supplier:change:review") ApiResponse<ProfileChangeModels.View> review(@PathVariable long supplierId,@PathVariable long id,@Valid @RequestBody ProfileChangeModels.Review c){return responses.success(service.review(supplierId,id,c));}
    private <T> ResponseEntity<ApiResponse<T>> reply(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(value));}
}

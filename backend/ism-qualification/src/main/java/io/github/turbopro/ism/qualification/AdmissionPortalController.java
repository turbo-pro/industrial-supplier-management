package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController public class AdmissionPortalController {
 private final AdmissionPortalService service;private final ApiResponseFactory responses;
 public AdmissionPortalController(AdmissionPortalService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
 @GetMapping("/api/admissions/{id}/material-requests") @RequiresPermission("supplier:admission:view") ResponseEntity<ApiResponse<AdmissionPortalModels.EmployeePage>> list(@PathVariable long id){return reply(service.employeeList(id));}
 @PostMapping("/api/admissions/{id}/material-requests") @RequiresPermission("supplier:admission:apply") ResponseEntity<ApiResponse<AdmissionPortalModels.View>> publish(@PathVariable long id,@Valid @RequestBody AdmissionPortalModels.Publish c){return reply(service.publish(id,c));}
 @PostMapping("/api/admission-material-requests/{id}/accept") @RequiresPermission("supplier:admission:apply") ResponseEntity<ApiResponse<AdmissionPortalModels.View>> accept(@PathVariable long id,@Valid @RequestBody AdmissionPortalModels.Version c){return reply(service.accept(id,c));}
 @PostMapping("/api/admission-material-requests/{id}/cancel") @RequiresPermission("supplier:admission:apply") ResponseEntity<ApiResponse<AdmissionPortalModels.View>> cancel(@PathVariable long id,@Valid @RequestBody AdmissionPortalModels.Cancel c){return reply(service.cancel(id,c));}
 @GetMapping("/api/portal/admission-material-requests") ResponseEntity<ApiResponse<AdmissionPortalModels.Page>> portalList(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return reply(service.portalList(page,size));}
 @PostMapping("/api/portal/admission-material-requests/{id}/submit") ResponseEntity<ApiResponse<AdmissionPortalModels.View>> submit(@PathVariable long id,@Valid @RequestBody AdmissionPortalModels.Submit c){return reply(service.submit(id,c));}
 @GetMapping("/api/portal/admission-material-requests/{id}/materials/{type}/content") ResponseEntity<InputStreamResource> portalFile(@PathVariable long id,@PathVariable String type){return content(service.portalDownload(id,type));}
 @GetMapping("/api/admission-material-requests/{id}/materials/{type}/content") @RequiresPermission("supplier:admission:view") ResponseEntity<InputStreamResource> employeeFile(@PathVariable long id,@PathVariable String type){return content(service.employeeDownload(id,type));}
 private <T> ResponseEntity<ApiResponse<T>> reply(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(value));}
 private ResponseEntity<InputStreamResource> content(QualificationFileContent.Download file){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.size()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+URLEncoder.encode(file.name(),StandardCharsets.UTF_8).replace("+","%20")).header("X-Content-Type-Options","nosniff").body(new InputStreamResource(file.stream()));}
}

package io.github.turbopro.ism.qualification;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController public class AdmissionResultController {
 private final AdmissionResultService service;private final ApiResponseFactory responses;
 public AdmissionResultController(AdmissionResultService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
 @GetMapping("/api/admissions/{id}/public-results") @RequiresPermission("supplier:admission:view") ResponseEntity<ApiResponse<AdmissionResultModels.EmployeePage>> list(@PathVariable long id){return reply(service.employeeList(id));}
 @PostMapping("/api/admissions/{id}/public-results") @RequiresPermission("supplier:admission:review") ResponseEntity<ApiResponse<AdmissionResultModels.View>> publish(@PathVariable long id,@Valid @RequestBody AdmissionResultModels.Publish command){return reply(service.publish(id,command));}
 @GetMapping("/api/portal/admission-results") ResponseEntity<ApiResponse<AdmissionResultModels.Page>> portalList(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return reply(service.portalList(page,size));}
 @PostMapping("/api/admissions/{id}/public-results/{publicationId}/correct") @RequiresPermission("supplier:admission:review") ResponseEntity<ApiResponse<AdmissionResultModels.View>> correct(@PathVariable long id,@PathVariable long publicationId,@Valid @RequestBody AdmissionResultModels.Correct command){return reply(service.correct(id,publicationId,command));}
 @PostMapping("/api/admissions/{id}/public-results/{publicationId}/withdraw") @RequiresPermission("supplier:admission:review") ResponseEntity<ApiResponse<AdmissionResultModels.View>> withdraw(@PathVariable long id,@PathVariable long publicationId,@Valid @RequestBody AdmissionResultModels.Withdraw command){return reply(service.withdraw(id,publicationId,command));}
 @GetMapping("/api/admissions/{id}/public-results/{publicationId}/history") @RequiresPermission("supplier:admission:view") ResponseEntity<ApiResponse<AdmissionResultModels.History>> history(@PathVariable long id,@PathVariable long publicationId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return reply(service.history(id,publicationId,page,size));}
 private <T> ResponseEntity<ApiResponse<T>> reply(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(value));}
}

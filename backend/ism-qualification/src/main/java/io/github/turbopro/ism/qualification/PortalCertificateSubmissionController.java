package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/portal/certificate-submissions")
public class PortalCertificateSubmissionController {
    private final PortalCertificateSubmissionService service;private final ApiResponseFactory responses;
    public PortalCertificateSubmissionController(PortalCertificateSubmissionService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping("/types") ResponseEntity<ApiResponse<List<PortalCertificateSubmissionModels.Type>>> types(){return reply(service.types());}
    @GetMapping ResponseEntity<ApiResponse<PortalCertificateSubmissionModels.Page>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return reply(service.list(page,size));}
    @PostMapping ResponseEntity<ApiResponse<PortalCertificateSubmissionModels.View>> submit(@Valid @RequestBody PortalCertificateSubmissionModels.Submit command){return reply(service.submit(command));}
    private <T> ResponseEntity<ApiResponse<T>> reply(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(value));}
}

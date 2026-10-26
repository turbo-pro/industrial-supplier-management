package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController public class CorrectionAttachmentController {
    private final CorrectionAttachmentService service;private final ApiResponseFactory responses;
    public CorrectionAttachmentController(CorrectionAttachmentService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping("/api/portal/certificate-submissions/{id}/attachments") ResponseEntity<ApiResponse<List<PortalCertificateSubmissionModels.Attachment>>> portalList(@PathVariable long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(service.portalList(id)));}
    @GetMapping("/api/qualifications/{id}/correction-attachments") @RequiresPermission("supplier:qualification:view") ApiResponse<List<PortalCertificateSubmissionModels.Attachment>> employeeList(@PathVariable long id){return responses.success(service.employeeList(id));}
    @GetMapping("/api/portal/certificate-submissions/{id}/attachments/{attachmentId}/content") ResponseEntity<InputStreamResource> portalDownload(@PathVariable long id,@PathVariable long attachmentId){return content(service.portalDownload(id,attachmentId));}
    @GetMapping("/api/qualifications/{id}/correction-attachments/{attachmentId}/content") @RequiresPermission("supplier:qualification:view") ResponseEntity<InputStreamResource> employeeDownload(@PathVariable long id,@PathVariable long attachmentId){return content(service.employeeDownload(id,attachmentId));}
    private ResponseEntity<InputStreamResource> content(QualificationFileContent.Download file){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.size()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+URLEncoder.encode(file.name(),StandardCharsets.UTF_8).replace("+","%20")).header("X-Content-Type-Options","nosniff").body(new InputStreamResource(file.stream()));}
}

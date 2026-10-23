package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/portal/qualifications")
public class PortalQualificationController {
    private final PortalQualificationService service;private final ApiResponseFactory responses;
    public PortalQualificationController(PortalQualificationService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping ResponseEntity<ApiResponse<PortalQualificationModels.Page>> list(@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(service.list(keyword,status,page,size)));}
    @GetMapping("/{id}") ResponseEntity<ApiResponse<PortalQualificationModels.Certificate>> detail(@PathVariable long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(service.detail(id)));}
    @GetMapping("/{id}/content") ResponseEntity<InputStreamResource> download(@PathVariable long id){
        var result=service.download(id);
        String name=URLEncoder.encode(result.name(),StandardCharsets.UTF_8).replace("+","%20");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(result.size())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+name)
            .header("X-Content-Type-Options","nosniff").body(new InputStreamResource(result.stream()));
    }
}

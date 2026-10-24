package io.github.turbopro.ism.resource.file;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/portal/certificate-uploads")
public class PortalUploadController {
    private final PortalUploadService service;private final ApiResponseFactory responses;
    public PortalUploadController(PortalUploadService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @PostMapping ResponseEntity<ApiResponse<PortalUploadModels.View>> initialize(@Valid @RequestBody PortalUploadModels.Initialize command){return reply(service.initialize(command));}
    @GetMapping("/{id}") ResponseEntity<ApiResponse<PortalUploadModels.View>> get(@PathVariable long id){return reply(service.get(id));}
    @PutMapping(value="/{id}/chunks/{index}",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE) ResponseEntity<ApiResponse<PortalUploadModels.View>> chunk(@PathVariable long id,@PathVariable int index,@RequestHeader("X-Chunk-SHA256")String hash,@RequestBody byte[] bytes){return reply(service.chunk(id,index,hash,bytes));}
    @PostMapping("/{id}/complete") ResponseEntity<ApiResponse<PortalUploadModels.View>> complete(@PathVariable long id,@Valid @RequestBody PortalUploadModels.Version command){return reply(service.complete(id,command.version()));}
    private ResponseEntity<ApiResponse<PortalUploadModels.View>> reply(PortalUploadModels.View data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(data));}
}

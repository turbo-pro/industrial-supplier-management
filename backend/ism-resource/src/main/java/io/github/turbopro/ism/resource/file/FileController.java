package io.github.turbopro.ism.resource.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService service;private final ApiResponseFactory responses;private final IdempotencyService idempotency;private final AuditService audit;private final ObjectMapper json;
    public FileController(FileService service,ApiResponseFactory responses,IdempotencyService idempotency,AuditService audit,ObjectMapper json){this.service=service;this.responses=responses;this.idempotency=idempotency;this.audit=audit;this.json=json;}
    @PostMapping("/uploads") @RequiresPermission("resource:file:upload") ApiResponse<FileModels.UploadView> initialize(@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody FileModels.InitializeUpload command){return responses.success(once("FILE_UPLOAD_INITIALIZE",key,command,FileModels.UploadView.class,()->service.initialize(command)));}
    @GetMapping("/uploads/{id}") @RequiresPermission("resource:file:upload") ApiResponse<FileModels.UploadView> upload(@PathVariable long id){return responses.success(service.upload(id));}
    @PutMapping(value="/uploads/{id}/chunks/{index}",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE) @RequiresPermission("resource:file:upload") ApiResponse<FileModels.UploadView> chunk(@PathVariable long id,@PathVariable int index,@RequestHeader("X-Chunk-SHA256")String hash,@RequestBody byte[] content){return responses.success(service.putChunk(id,index,hash,content));}
    @PostMapping("/uploads/{id}/complete") @RequiresPermission("resource:file:upload") ApiResponse<FileModels.FileView> complete(@PathVariable long id,@RequestParam int version,@RequestHeader("Idempotency-Key")String key){return responses.success(once("FILE_UPLOAD_COMPLETE:"+id,key,Map.of("version",version),FileModels.FileView.class,()->{var result=service.complete(id,version);append("FILE_UPLOAD_COMPLETE",Long.parseLong(result.id()),Map.of("sha256",result.sha256(),"size",result.size()));return result;}));}
    @GetMapping @RequiresPermission("resource:file:view") ApiResponse<List<FileModels.FileView>> files(){return responses.success(service.files());}
    @GetMapping("/{id}") @RequiresPermission("resource:file:view") ApiResponse<FileModels.FileView> file(@PathVariable long id){return responses.success(service.file(id));}
    @GetMapping("/{id}/content") @RequiresPermission("resource:file:download") ResponseEntity<InputStreamResource> download(@PathVariable long id){var result=service.download(id);String name=URLEncoder.encode(result.metadata().originalName(),StandardCharsets.UTF_8).replace("+","%20");return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.metadata().contentType())).contentLength(result.metadata().size()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+name).header("X-Content-Type-Options","nosniff").body(new InputStreamResource(result.stream()));}
    @DeleteMapping("/{id}") @RequiresPermission("resource:file:manage") ApiResponse<Void> delete(@PathVariable long id,@RequestHeader("Idempotency-Key")String key){once("FILE_DELETE:"+id,key,Map.of(),Done.class,()->{service.delete(id);append("FILE_DELETE",id,Map.of("status","DELETED"));return new Done(true);});return responses.success(null);}
    private void append(String action,long id,Object after){audit.append(new AuditService.AuditCommand(action,"FILE",id,null,Map.of(),after,null,null));}
    private <T>T once(String operation,String key,Object request,Class<T> type,Supplier<T> action){String value=idempotency.execute(operation,key,write(request),Duration.ofHours(24),()->write(action.get()));try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("幂等响应无法解析",e);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("请求无法序列化",e);}}
    private record Done(boolean completed){}
}

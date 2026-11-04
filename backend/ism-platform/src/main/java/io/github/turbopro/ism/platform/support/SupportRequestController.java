package io.github.turbopro.ism.platform.support;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.operation.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.function.Supplier;
@RestController @RequestMapping("/api/console")
public class SupportRequestController {
 private final SupportRequestService service;private final ApiResponseFactory responses;private final IdempotencyService idempotency;private final ObjectMapper json;
 public SupportRequestController(SupportRequestService service,ApiResponseFactory responses,IdempotencyService idempotency,ObjectMapper json){this.service=service;this.responses=responses;this.idempotency=idempotency;this.json=json;}
 @PostMapping("/tenants/{tenantId}/support-requests") @RequiresPermission("platform:support:request")
 ApiResponse<SupportRequestModels.View> create(@PathVariable long tenantId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SupportRequestModels.Create c){return responses.success(once("SUPPORT_REQUEST_CREATE:"+tenantId,key,c,()->service.create(tenantId,c)));}
 @GetMapping("/support-requests") @RequiresPermission("platform:support:view")
 ApiResponse<SupportRequestModels.Page> list(@RequestParam(defaultValue="false") boolean review,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return responses.success(service.list(review,page,size));}
 @GetMapping("/support-requests/{id}") @RequiresPermission("platform:support:view")
 ApiResponse<SupportRequestModels.View> get(@PathVariable long id){return responses.success(service.get(id));}
 @PostMapping("/support-requests/{id}/submit") @RequiresPermission("platform:support:request")
 ApiResponse<SupportRequestModels.View> submit(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SupportRequestModels.Change c){return change(id,"submit",key,c);}
 @PostMapping("/support-requests/{id}/cancel") @RequiresPermission("platform:support:request")
 ApiResponse<SupportRequestModels.View> cancel(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SupportRequestModels.Change c){return change(id,"cancel",key,c);}
 @PostMapping("/support-requests/{id}/approve") @RequiresPermission("platform:support:review")
 ApiResponse<SupportRequestModels.View> approve(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SupportRequestModels.Change c){return change(id,"approve",key,c);}
 @PostMapping("/support-requests/{id}/reject") @RequiresPermission("platform:support:review")
 ApiResponse<SupportRequestModels.View> reject(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SupportRequestModels.Change c){return change(id,"reject",key,c);}
 private ApiResponse<SupportRequestModels.View> change(long id,String action,String key,SupportRequestModels.Change c){if(java.util.Set.of("approve","reject").contains(action)&&!io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext.require().hasAction("platform:support:view"))throw new io.github.turbopro.ism.common.api.error.ApiException(io.github.turbopro.ism.common.api.error.CommonErrorCode.FORBIDDEN);return responses.success(once("SUPPORT_REQUEST_"+action+":"+id,key,c,()->service.change(id,action,c)));}
 private SupportRequestModels.View once(String op,String key,Object c,Supplier<SupportRequestModels.View> action){String value=idempotency.execute(op,key,write(c),Duration.ofHours(24),()->write(action.get()));try{return json.readValue(value,SupportRequestModels.View.class);}catch(Exception e){throw new IllegalStateException(e);}}
 private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
}

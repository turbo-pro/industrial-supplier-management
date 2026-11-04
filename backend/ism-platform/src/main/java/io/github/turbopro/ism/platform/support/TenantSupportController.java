package io.github.turbopro.ism.platform.support;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.api.support.SupportRoleAuthority;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.iam.auth.AuthPrincipal;
import io.github.turbopro.ism.operation.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
@RestController @RequestMapping("/api")
public class TenantSupportController {
 private final TenantSupportService service;private final ApiResponseFactory responses;private final IdempotencyService idempotency;private final ObjectMapper json;
 public TenantSupportController(TenantSupportService service,ApiResponseFactory responses,IdempotencyService idempotency,ObjectMapper json){this.service=service;this.responses=responses;this.idempotency=idempotency;this.json=json;}
 @GetMapping("/tenant-support/policy") @RequiresPermission("iam:support:view")
 ApiResponse<TenantSupportModels.Policy> get(){return responses.success(service.policy());}
 @GetMapping("/tenant-support/role-options") @RequiresPermission("iam:support:manage")
 ApiResponse<List<SupportRoleAuthority.Role>> options(){return responses.success(service.options());}
 @PutMapping("/tenant-support/policy") @RequiresPermission("iam:support:manage")
 ApiResponse<TenantSupportModels.Policy> save(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody TenantSupportModels.SavePolicy c){return responses.success(once("TENANT_SUPPORT_POLICY",key,c,TenantSupportModels.Policy.class,()->service.save(c)));}
 @GetMapping("/tenant-support/requests") @RequiresPermission("iam:support:view")
 ApiResponse<TenantSupportModels.Page> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return responses.success(service.list(page,size));}
 @GetMapping("/tenant-support/requests/{id}") @RequiresPermission("iam:support:view")
 ApiResponse<TenantSupportModels.Request> getRequest(@PathVariable long id){return responses.success(service.get(id));}
 @PostMapping("/tenant-support/requests/{id}/approve") @RequiresPermission("iam:support:review")
 ApiResponse<TenantSupportModels.Request> approve(@PathVariable long id,@Valid @RequestBody TenantSupportModels.Decision c,@RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthPrincipal p){return change(id,"approve",c,key,p);}
 @PostMapping("/tenant-support/requests/{id}/reject") @RequiresPermission("iam:support:review")
 ApiResponse<TenantSupportModels.Request> reject(@PathVariable long id,@Valid @RequestBody TenantSupportModels.Decision c,@RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthPrincipal p){return change(id,"reject",c,key,p);}
 @PostMapping("/tenant-support/requests/{id}/revoke") @RequiresPermission("iam:support:review")
 ApiResponse<TenantSupportModels.Request> revoke(@PathVariable long id,@Valid @RequestBody TenantSupportModels.Decision c,@RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthPrincipal p){return change(id,"revoke",c,key,p);}
 @GetMapping("/console/support-requests/{id}/authorization") @RequiresPermission("platform:support:view")
 ApiResponse<TenantSupportModels.Authorization> authorization(@PathVariable long id){return responses.success(service.authorization(id));}
 private ApiResponse<TenantSupportModels.Request> change(long id,String action,TenantSupportModels.Decision c,String key,AuthPrincipal p){if(p==null||TenantContext.require().tenantId()<=0||!AuthorizationContext.require().hasAction("iam:support:view"))throw new ApiException(CommonErrorCode.FORBIDDEN);return responses.success(once("TENANT_SUPPORT_"+action+":"+id,key,c,TenantSupportModels.Request.class,()->service.decide(id,action,c,p.username())));}
 private <T>T once(String op,String key,Object body,Class<T> type,Supplier<T> action){String result=idempotency.execute(op,key,write(body),Duration.ofHours(24),()->write(action.get()));try{return json.readValue(result,type);}catch(Exception e){throw new IllegalStateException(e);}}
 private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
}

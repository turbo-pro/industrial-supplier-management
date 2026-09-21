package io.github.turbopro.ism.platform.packageplan;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.iam.console.ConsolePrincipal;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/console")
public class PackagePlanController {
    private final PackagePlanService service; private final ApiResponseFactory responses;
    private final IdempotencyService idempotency; private final AuditService audit; private final ObjectMapper json;
    public PackagePlanController(PackagePlanService service,ApiResponseFactory responses,IdempotencyService idempotency,
                                 AuditService audit,ObjectMapper json){this.service=service;this.responses=responses;this.idempotency=idempotency;this.audit=audit;this.json=json;}
    @GetMapping("/packages") @RequiresPermission("platform:package:view")
    ApiResponse<List<PackagePlanModels.PackageView>> list(){return responses.success(service.list());}
    @PostMapping("/packages") @RequiresPermission("platform:package:manage")
    ApiResponse<PackagePlanModels.PackageView> create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PackagePlanModels.CreatePackage c){return responses.success(once("PACKAGE_CREATE",key,c,PackagePlanModels.PackageView.class,()->service.create(c)));}
    @GetMapping("/packages/{id}") @RequiresPermission("platform:package:view")
    ApiResponse<PackagePlanModels.PackageView> get(@PathVariable long id){return responses.success(service.get(id));}
    @PutMapping("/packages/{id}") @RequiresPermission("platform:package:manage")
    ApiResponse<PackagePlanModels.PackageView> update(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PackagePlanModels.UpdatePackage c){return responses.success(once("PACKAGE_UPDATE:"+id,key,c,PackagePlanModels.PackageView.class,()->service.update(id,c)));}
    @PostMapping("/packages/{id}/versions") @RequiresPermission("platform:package:manage")
    ApiResponse<PackagePlanModels.VersionView> version(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PackagePlanModels.CreateVersion c){return responses.success(once("PACKAGE_VERSION_CREATE:"+id,key,c,PackagePlanModels.VersionView.class,()->service.createVersion(id,c)));}
    @PostMapping("/package-versions/{id}/validate") @RequiresPermission("platform:package:manage")
    ApiResponse<PackagePlanModels.ValidationResult> validate(@PathVariable long id){return responses.success(service.validate(id));}
    @PostMapping("/package-versions/{id}/publish") @RequiresPermission("platform:package:publish")
    ApiResponse<PackagePlanModels.VersionView> publish(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PackagePlanModels.PublishCommand c,@AuthenticationPrincipal ConsolePrincipal p){return responses.success(once("PACKAGE_VERSION_PUBLISH:"+id,key,c,PackagePlanModels.VersionView.class,()->{var result=service.publish(id,c.version(),p.userId());audit.append(new AuditService.AuditCommand("PACKAGE_VERSION_PUBLISH","PACKAGE_VERSION",id,null,Map.of("status","DRAFT"),Map.of("status","PUBLISHED"),null,null));return result;}));}
    @PostMapping("/tenants/{tenantId}/subscription-preview") @RequiresPermission("platform:package:assign")
    ApiResponse<PackagePlanModels.SubscriptionPreview> preview(@PathVariable long tenantId,@RequestParam long packageVersionId){return responses.success(service.preview(tenantId,packageVersionId));}
    @PostMapping("/tenants/{tenantId}/subscription") @RequiresPermission("platform:package:assign")
    ApiResponse<PackagePlanModels.SubscriptionPreview> assign(@PathVariable long tenantId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PackagePlanModels.AssignSubscription c,@AuthenticationPrincipal ConsolePrincipal p){return responses.success(once("SUBSCRIPTION_ASSIGN:"+tenantId,key,c,PackagePlanModels.SubscriptionPreview.class,()->{var result=service.assign(tenantId,Long.parseLong(c.packageVersionId()),c);audit.append(new AuditService.AuditCommand("SUBSCRIPTION_ASSIGN","TENANT",tenantId,null,Map.of(),Map.of("packageVersionId",c.packageVersionId()),null,null));return result;}));}
    @GetMapping("/tenants/{tenantId}/quota-usage") @RequiresPermission("platform:package:view")
    ApiResponse<PackagePlanModels.QuotaUsage> quota(@PathVariable long tenantId,@RequestParam String quotaCode){return responses.success(service.quota(tenantId,quotaCode));}

    private <T> T once(String operation,String key,Object request,Class<T> type,Supplier<T> action){
        String value=idempotency.execute(operation,key,write(request),Duration.ofHours(24),()->write(action.get()));
        try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("幂等响应无法解析",e);}
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("请求无法序列化",e);}}
}

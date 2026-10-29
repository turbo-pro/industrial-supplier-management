package io.github.turbopro.ism.iam.site;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.operation.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;
@RestController @RequestMapping("/api/site-preferences")
public class SitePreferenceController {
    private final SitePreferenceService service;private final ApiResponseFactory responses;private final IdempotencyService idempotency;private final AuditService audit;private final ObjectMapper json;
    public SitePreferenceController(SitePreferenceService service,ApiResponseFactory responses,IdempotencyService idempotency,AuditService audit,ObjectMapper json){this.service=service;this.responses=responses;this.idempotency=idempotency;this.audit=audit;this.json=json;}
    @GetMapping @RequiresPermission("iam:site:view")
    ApiResponse<SitePreferenceModels.State> state(){return responses.success(service.state());}
    @GetMapping("/options") @RequiresPermission("iam:site:view")
    ApiResponse<SitePreferenceModels.Page> options(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return responses.success(service.options(q,page,size));}
    @PutMapping @RequiresPermission("iam:site:preference")
    ApiResponse<SitePreferenceModels.State> save(@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody SitePreferenceModels.Update command){
        service.requireTarget(command.siteId());var who=TenantContext.require();
        String result=idempotency.execute("SITE_PREFERENCE:"+who.actorId(),key,write(command),Duration.ofHours(24),()->{
            var changed=service.save(command);audit.append(new AuditService.AuditCommand("SITE_PREFERENCE_CHANGE","USER",who.actorId(),null,Map.of("siteId",changed.beforeSiteId()),Map.of("siteId",changed.siteId(),"version",changed.state().version()),null,null));return write(changed.state());
        });
        try{return responses.success(json.readValue(result,SitePreferenceModels.State.class));}catch(Exception e){throw new IllegalStateException("偏好回执无法解析",e);}
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("偏好请求无法序列化",e);}}
}

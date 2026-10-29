package io.github.turbopro.ism.iam.site;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/site-context")
public class SiteContextController {
    private final SiteContextService service;private final ApiResponseFactory responses;
    public SiteContextController(SiteContextService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @PostMapping @RequiresPermission("iam:site:view")
    ApiResponse<SiteContextModels.Issued> issue(@RequestHeader("Authorization")String authorization,@Valid @RequestBody SiteContextModels.Select command){return responses.success(service.issue(authorization.substring(7),command));}
    @GetMapping @RequiresPermission("iam:site:view")
    ApiResponse<SiteContextModels.Current> current(@RequestHeader("X-Site-Context")String context){return responses.success(service.current());}
}

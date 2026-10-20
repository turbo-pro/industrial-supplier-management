package io.github.turbopro.ism.supplier;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PortalRegistrationController {
    private final PortalRegistrationService service;private final PortalRegistrationRateGuard rate;private final ApiResponseFactory responses;
    public PortalRegistrationController(PortalRegistrationService service,PortalRegistrationRateGuard rate,ApiResponseFactory responses){this.service=service;this.rate=rate;this.responses=responses;}
    @PostMapping("/api/portal/auth/register")
    ResponseEntity<ApiResponse<PortalRegistrationModels.Registered>> register(@Valid @RequestBody PortalRegistrationModels.Register command,HttpServletRequest request){
        rate.check(request.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(service.register(command)));
    }
}

package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.portal.PortalPublicAttemptGuard;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/portal/auth")
public class PortalAuthController {
    private final PortalAuthService auth;private final PortalPublicAttemptGuard rate;private final ApiResponseFactory responses;
    public PortalAuthController(PortalAuthService auth,PortalPublicAttemptGuard rate,ApiResponseFactory responses){this.auth=auth;this.rate=rate;this.responses=responses;}
    @PostMapping("/login") ResponseEntity<ApiResponse<PortalAuthModels.Session>> login(@Valid @RequestBody PortalAuthModels.Login command,HttpServletRequest request){rate.check(request.getRemoteAddr());return reply(auth.login(command));}
    @GetMapping("/me") ResponseEntity<ApiResponse<PortalAuthModels.User>> me(Authentication authentication){return reply(principal(authentication).user());}
    @PostMapping("/logout") ResponseEntity<ApiResponse<Void>> logout(Authentication authentication){auth.logout(principal(authentication));return reply(null);}
    @PostMapping("/password") ResponseEntity<ApiResponse<Void>> password(Authentication authentication,@Valid @RequestBody PortalAuthModels.ChangePassword command){auth.changePassword(principal(authentication),command);return reply(null);}
    private PortalAuthModels.Principal principal(Authentication authentication){if(authentication==null || !(authentication.getPrincipal() instanceof PortalAuthModels.Principal p))throw new ApiException(CommonErrorCode.UNAUTHENTICATED);return p;}
    private <T> ResponseEntity<ApiResponse<T>> reply(T data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.success(data));}
}

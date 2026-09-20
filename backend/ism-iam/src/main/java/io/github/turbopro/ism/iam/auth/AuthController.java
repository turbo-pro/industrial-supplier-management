package io.github.turbopro.ism.iam.auth;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final ApiResponseFactory responses;

    public AuthController(AuthService authService, ApiResponseFactory responses) {
        this.authService = authService;
        this.responses = responses;
    }

    @PostMapping("/login")
    ApiResponse<AuthModels.TokenPair> login(@Valid @RequestBody AuthModels.LoginCommand command,
                                            HttpServletRequest request) {
        return responses.success(authService.login(command, request.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    ApiResponse<AuthModels.TokenPair> refresh(@Valid @RequestBody AuthModels.RefreshCommand command,
                                              HttpServletRequest request) {
        return responses.success(authService.refresh(command, request.getRemoteAddr()));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@Valid @RequestBody AuthModels.LogoutCommand command) {
        authService.logout(command.refreshToken());
        return responses.success(null);
    }

    @GetMapping("/me")
    ApiResponse<AuthModels.UserSummary> me(@AuthenticationPrincipal AuthPrincipal principal) {
        AuthModels.AuthUser user = authService.requireActiveUser(principal.userId(), principal.tokenVersion());
        return responses.success(new AuthModels.UserSummary(Long.toString(user.id()), Long.toString(user.tenantId()),
                user.username(), user.displayName(), user.forcePasswordChange()));
    }

    @PostMapping("/change-password")
    ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody AuthModels.ChangePasswordCommand command) {
        authService.changePassword(principal.userId(), command);
        return responses.success(null);
    }
}

package io.github.turbopro.ism.iam.console;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/console/auth")
public class ConsoleAuthController {
    private final ConsoleAuthService auth;
    private final ApiResponseFactory responses;

    public ConsoleAuthController(ConsoleAuthService auth, ApiResponseFactory responses) {
        this.auth = auth;
        this.responses = responses;
    }

    @PostMapping("/login")
    ApiResponse<ConsoleAuthModels.TokenPair> login(@Valid @RequestBody ConsoleAuthModels.LoginCommand command,
                                                   HttpServletRequest request) {
        return responses.success(auth.login(command, request.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    ApiResponse<ConsoleAuthModels.TokenPair> refresh(@Valid @RequestBody ConsoleAuthModels.RefreshCommand command,
                                                     HttpServletRequest request) {
        return responses.success(auth.refresh(command, request.getRemoteAddr()));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@Valid @RequestBody ConsoleAuthModels.LogoutCommand command) {
        auth.logout(command.refreshToken());
        return responses.success(null);
    }

    @GetMapping("/me")
    ApiResponse<ConsoleAuthModels.UserSummary> me(@AuthenticationPrincipal ConsolePrincipal principal) {
        return responses.success(auth.summary(auth.requireActiveUser(principal.userId(), principal.tokenVersion())));
    }

    @PostMapping("/change-password")
    ApiResponse<Void> changePassword(@AuthenticationPrincipal ConsolePrincipal principal,
                                     @Valid @RequestBody ConsoleAuthModels.ChangePasswordCommand command) {
        auth.changePassword(principal.userId(), command);
        return responses.success(null);
    }
}

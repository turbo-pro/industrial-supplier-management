package io.github.turbopro.ism.iam.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.iam.auth.IamErrorCode;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class ConsoleJwtAuthenticationFilter extends OncePerRequestFilter {
    private final ConsoleJwtTokenService tokens;
    private final ConsoleAuthService auth;
    private final ApiResponseFactory responses;
    private final ObjectMapper objectMapper;

    public ConsoleJwtAuthenticationFilter(ConsoleJwtTokenService tokens, ConsoleAuthService auth,
                                          ApiResponseFactory responses, ObjectMapper objectMapper) {
        this.tokens = tokens; this.auth = auth; this.responses = responses; this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/console/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Jwt jwt = tokens.decode(authorization.substring(7));
            long userId = Long.parseLong(jwt.getSubject());
            int version = ((Number) jwt.getClaim("tokenVersion")).intValue();
            ConsoleAuthModels.PlatformUser user = auth.requireActiveSession(
                    userId, version, jwt.getClaimAsString("tokenFamilyId"));
            ConsolePrincipal principal = new ConsolePrincipal(user.id(), user.username(), user.tokenVersion(),
                    user.forcePasswordChange());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            if (principal.passwordChangeRequired() && !passwordChangeAllowed(request.getRequestURI())) {
                writeError(response, IamErrorCode.PASSWORD_CHANGE_REQUIRED);
                return;
            }
            PermissionSnapshot snapshot = new PermissionSnapshot(auth.permissions(user.id()), Map.of(), Set.of());
            try (AuthorizationContext.Scope ignored = AuthorizationContext.open(snapshot)) {
                chain.doFilter(request, response);
            }
        } catch (JwtException | IllegalArgumentException | ApiException exception) {
            SecurityContextHolder.clearContext();
            writeError(response, exception instanceof ApiException api ? api.errorCode() : IamErrorCode.TOKEN_INVALID);
        }
    }

    private boolean passwordChangeAllowed(String uri) {
        return "/api/console/auth/change-password".equals(uri) || "/api/console/auth/logout".equals(uri);
    }

    private void writeError(HttpServletResponse response, io.github.turbopro.ism.common.api.error.ErrorCode code)
            throws IOException {
        response.setStatus(code.httpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = responses.failure(code, code.defaultMessage());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

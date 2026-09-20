package io.github.turbopro.ism.iam.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;
    private final AuthService authService;
    private final ApiResponseFactory responses;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenService tokens, AuthService authService,
                                   ApiResponseFactory responses, ObjectMapper objectMapper) {
        this.tokens = tokens;
        this.authService = authService;
        this.responses = responses;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Jwt jwt = tokens.decode(authorization.substring(7));
            long userId = Long.parseLong(jwt.getSubject());
            int tokenVersion = ((Number) jwt.getClaim("tokenVersion")).intValue();
            String familyId = jwt.getClaimAsString("tokenFamilyId");
            AuthModels.AuthUser user = authService.requireActiveSession(userId, tokenVersion, familyId);
            AuthPrincipal principal = new AuthPrincipal(user.id(), user.tenantId(), user.username(), user.tokenVersion(),
                    user.forcePasswordChange());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            if (principal.passwordChangeRequired() && !passwordChangeAllowed(request.getRequestURI())) {
                writeError(response, IamErrorCode.PASSWORD_CHANGE_REQUIRED);
                return;
            }
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | ApiException exception) {
            SecurityContextHolder.clearContext();
            writeError(response, exception instanceof ApiException api ? api.errorCode() : IamErrorCode.TOKEN_INVALID);
        }
    }

    private boolean passwordChangeAllowed(String uri) {
        return "/api/auth/change-password".equals(uri) || "/api/auth/logout".equals(uri);
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

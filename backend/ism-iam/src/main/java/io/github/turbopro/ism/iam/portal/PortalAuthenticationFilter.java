package io.github.turbopro.ism.iam.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.io.IOException;
import java.util.List;

@Component
public class PortalAuthenticationFilter extends OncePerRequestFilter {
    private final PortalAuthService auth;private final ObjectMapper json;private final ApiResponseFactory responses;
    public PortalAuthenticationFilter(PortalAuthService auth,ObjectMapper json,ApiResponseFactory responses){this.auth=auth;this.json=json;this.responses=responses;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){
        String path=request.getRequestURI();
        return !path.startsWith("/api/portal/") || ("POST".equals(request.getMethod()) && (path.equals("/api/portal/auth/login") || path.equals("/api/portal/auth/register") || path.equals("/api/portal/auth/reset-password")));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        String header=request.getHeader("Authorization");
        if(header==null || !header.startsWith("Bearer ")){chain.doFilter(request,response);return;}
        PortalAuthModels.Principal principal;
        try{principal=auth.authenticate(header.substring(7));}
        catch(ApiException e){response.setStatus(e.errorCode().httpStatus());response.setCharacterEncoding("UTF-8");response.setContentType("application/json");json.writeValue(response.getOutputStream(),responses.failure(e.errorCode(),e.getMessage()));return;}
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        try(var tenant=TenantContext.open(principal.tenantId(),principal.accountId());
            var portal=io.github.turbopro.ism.common.infrastructure.portal.PortalIdentityContext.open(principal.tenantId(),principal.accountId(),principal.supplierId());
            var grants=AuthorizationContext.open(PermissionSnapshot.denyAll())){chain.doFilter(request,response);}
        finally{SecurityContextHolder.clearContext();}
    }
}

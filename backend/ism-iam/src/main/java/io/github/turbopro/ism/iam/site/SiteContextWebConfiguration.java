package io.github.turbopro.ism.iam.site;
import io.github.turbopro.ism.common.api.error.ApiException;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;
/** Also reject misleading site headers on authenticated Console/Portal and legacy MVC routes. */
@Configuration
public class SiteContextWebConfiguration implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
        @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
            if(request.getHeader("X-Site-Context")!=null&&!SiteContextRoutes.supports(request.getMethod(),request.getServletPath()))throw new ApiException(SiteContextError.UNSUPPORTED);
            return true;
        }
    });}
}

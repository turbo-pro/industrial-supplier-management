package io.github.turbopro.ism.common.infrastructure.authorization;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionAspect {
    private final PermissionGuard guard;

    public PermissionAspect(PermissionGuard guard) {
        this.guard = guard;
    }

    @Around("@annotation(requiredPermission)")
    public Object authorize(ProceedingJoinPoint joinPoint, RequiresPermission requiredPermission) throws Throwable {
        guard.requireAction(requiredPermission.value());
        return joinPoint.proceed();
    }
}

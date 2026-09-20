package io.github.turbopro.ism.common.infrastructure.authorization;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Component;

@Component
public class PermissionGuard {
    public void requireAction(String permission) {
        if (!AuthorizationContext.require().hasAction(permission)) {
            throw new ApiException(CommonErrorCode.FORBIDDEN);
        }
    }

    public void requireData(String resource, DataTarget target) {
        long actorId = TenantContext.require().actorId();
        if (!AuthorizationContext.require().dataScope(resource).allows(target, actorId)) {
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        }
    }

    public void requireField(String permission) {
        if (!AuthorizationContext.require().hasField(permission)) {
            throw new ApiException(CommonErrorCode.FORBIDDEN);
        }
    }

    public boolean canViewField(String permission) {
        return AuthorizationContext.require().hasField(permission);
    }
}

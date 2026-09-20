package io.github.turbopro.ism.common.infrastructure.authorization;

public class DenyAllAuthorizationGrantLoader implements AuthorizationGrantLoader {
    @Override
    public PermissionSnapshot load(long tenantId, long actorId) {
        return PermissionSnapshot.denyAll();
    }
}

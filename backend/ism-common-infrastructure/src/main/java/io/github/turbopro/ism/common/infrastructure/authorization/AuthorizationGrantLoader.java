package io.github.turbopro.ism.common.infrastructure.authorization;

public interface AuthorizationGrantLoader {
    PermissionSnapshot load(long tenantId, long actorId);
}

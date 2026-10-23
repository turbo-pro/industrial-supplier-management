package io.github.turbopro.ism.common.infrastructure.portal;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;

/** Authenticated portal binding only; installed by the portal authentication filter. */
public final class PortalIdentityContext {
    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();
    private PortalIdentityContext() {}

    public static Scope open(long tenantId,long accountId,long supplierId) {
        return open(tenantId,accountId,supplierId,0);
    }
    public static Scope open(long tenantId,long accountId,long supplierId,long sessionId) {
        if(tenantId<=0||accountId<=0||supplierId<=0)throw new IllegalArgumentException("Positive portal identity IDs required");
        Identity previous=CURRENT.get();
        CURRENT.set(new Identity(tenantId,accountId,supplierId,sessionId));
        return new Scope(previous);
    }

    public static Identity require() {
        var identity=CURRENT.get();var tenant=TenantContext.current().orElse(null);
        if(identity==null||tenant==null||identity.tenantId()!=tenant.tenantId()||identity.accountId()!=tenant.actorId())
            throw new ApiException(CommonErrorCode.UNAUTHENTICATED);
        return identity;
    }

    public record Identity(long tenantId,long accountId,long supplierId,long sessionId) {}
    public static final class Scope implements AutoCloseable {
        private final Identity previous;private boolean closed;
        private Scope(Identity previous){this.previous=previous;}
        @Override public void close(){if(closed)return;closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
}

package io.github.turbopro.ism.common.infrastructure.tenant;
import java.time.Instant;
import java.util.Optional;
/** Request-only identity. Open only after server-side token and live authorization validation. */
public final class SiteContext {
    private static final ThreadLocal<Identity> CURRENT=new ThreadLocal<>();
    private SiteContext(){}
    public record Identity(long tenantId,long userId,long siteId,Instant expiresAt){}
    public static Scope open(long siteId,Instant expiresAt){
        var actor=TenantContext.require();if(siteId<=0||expiresAt==null)throw new IllegalArgumentException("Invalid site identity");
        var previous=CURRENT.get();CURRENT.set(new Identity(actor.tenantId(),actor.actorId(),siteId,expiresAt));return new Scope(previous);
    }
    public static Optional<Identity> current(){
        var identity=CURRENT.get();if(identity==null)return Optional.empty();
        var actor=TenantContext.require();if(actor.tenantId()!=identity.tenantId()||actor.actorId()!=identity.userId())throw new TenantIsolationException("Site context does not match authenticated actor");
        return Optional.of(identity);
    }
    public static Identity require(){return current().orElseThrow(()->new TenantIsolationException("Validated site context is required"));}
    public static final class Scope implements AutoCloseable{
        private final Identity previous;private boolean closed;
        private Scope(Identity previous){this.previous=previous;}
        public void close(){if(closed)return;closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
}

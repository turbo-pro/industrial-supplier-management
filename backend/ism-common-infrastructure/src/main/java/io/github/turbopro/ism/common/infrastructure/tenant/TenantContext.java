package io.github.turbopro.ism.common.infrastructure.tenant;

import java.util.Optional;

public final class TenantContext {
    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Scope open(long tenantId, long actorId) {
        if (tenantId < 0 || actorId <= 0) {
            throw new IllegalArgumentException("tenantId must be non-negative and actorId must be positive");
        }
        Identity previous = CURRENT.get();
        CURRENT.set(new Identity(tenantId, actorId));
        return new Scope(previous);
    }

    public static Optional<Identity> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Identity require() {
        Identity identity = CURRENT.get();
        if (identity == null) {
            throw new TenantIsolationException("Tenant context is required");
        }
        return identity;
    }

    public record Identity(long tenantId, long actorId) {
    }

    public static final class Scope implements AutoCloseable {
        private final Identity previous;
        private boolean closed;

        private Scope(Identity previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}

package io.github.turbopro.ism.common.infrastructure.authorization;

public final class AuthorizationContext {
    private static final ThreadLocal<PermissionSnapshot> CURRENT = new ThreadLocal<>();

    private AuthorizationContext() {
    }

    public static Scope open(PermissionSnapshot snapshot) {
        PermissionSnapshot previous = CURRENT.get();
        CURRENT.set(snapshot == null ? PermissionSnapshot.denyAll() : snapshot);
        return new Scope(previous);
    }

    public static PermissionSnapshot require() {
        PermissionSnapshot snapshot = CURRENT.get();
        return snapshot == null ? PermissionSnapshot.denyAll() : snapshot;
    }

    public static final class Scope implements AutoCloseable {
        private final PermissionSnapshot previous;
        private boolean closed;

        private Scope(PermissionSnapshot previous) {
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

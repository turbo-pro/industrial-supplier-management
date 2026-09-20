package io.github.turbopro.ism.common.infrastructure.tenant;

public class TenantIsolationException extends RuntimeException {
    public TenantIsolationException(String message) {
        super(message);
    }
}

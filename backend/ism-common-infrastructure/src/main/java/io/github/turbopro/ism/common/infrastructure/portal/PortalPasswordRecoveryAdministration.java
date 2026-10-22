package io.github.turbopro.ism.common.infrastructure.portal;

import java.time.LocalDateTime;

/** Caller authorizes dedicated permission and supplier scope, and holds supplier row lock. */
public interface PortalPasswordRecoveryAdministration {
    record State(String id,String status,LocalDateTime expiresAt,LocalDateTime createdAt,String createdBy) {}
    record Issued(String token,LocalDateTime expiresAt,int accountVersion) {
        @Override public String toString(){return "PortalRecoveryIssued[token=REDACTED]";}
    }
    boolean enabled();
    State current(long supplierId,long accountId);
    Issued issue(long supplierId,long accountId,int version,String reason,boolean identityVerified);
    void revoke(long supplierId,long accountId,int version,String reason);
}

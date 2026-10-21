package io.github.turbopro.ism.common.infrastructure.portal;

import java.time.LocalDateTime;
import java.util.List;

/** IAM owns credentials. Caller must authorize supplier scope and hold its row lock. */
public interface PortalAccountAdministration {
    record Account(String id, String username, String displayName, String status, int version,
                   long liveSessions, LocalDateTime lastLoginAt) {}
    record Page(int page, int size, long total, List<Account> items) {}
    enum Action { ENABLE, DISABLE, REVOKE_SESSIONS }
    Page list(long supplierId, int page, int size);
    Account change(long supplierId, long accountId, int version, Action action, String reason);
    record ExitRecovery(long accounts, long revokedSessions) {}
    /** Approval-only internal call. Caller holds the supplier lock and owns the exit transaction. */
    ExitRecovery recoverForExit(long supplierId, long applicationId);
}

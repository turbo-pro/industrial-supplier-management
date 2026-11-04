package io.github.turbopro.ism.iam.authorization;

import java.util.Set;

/** Public control-plane contract; implementations own subscription storage. */
public interface TenantModuleEntitlements {
    Set<String> enabledModules(long tenantId);

    /** Fresh committed subscription read for delegation, independent of an outer idempotency snapshot.
     * Implementations that cannot provide this guarantee must deny delegation. */
    default Set<String> currentEnabledModules(long tenantId) { return Set.of(); }
}

package io.github.turbopro.ism.iam.authorization;

import java.util.Set;

/** Public control-plane contract; implementations own subscription storage. */
public interface TenantModuleEntitlements {
    Set<String> enabledModules(long tenantId);
}

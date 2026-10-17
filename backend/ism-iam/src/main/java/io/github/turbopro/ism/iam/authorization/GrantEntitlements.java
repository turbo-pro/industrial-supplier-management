package io.github.turbopro.ism.iam.authorization;

import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Component
public class GrantEntitlements {
    private final TenantModuleEntitlements modules;

    public GrantEntitlements(TenantModuleEntitlements modules) {
        this.modules = modules;
    }

    public Set<String> allowedModules(long tenantId) {
        Set<String> result = new HashSet<>(modules.enabledModules(tenantId));
        result.addAll(Set.of("SYSTEM", "COMMON", "MESSAGE", "WORKFLOW"));
        return Set.copyOf(result);
    }
}

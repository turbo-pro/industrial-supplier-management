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
        return withCoreModules(modules.enabledModules(tenantId));
    }

    public static Set<String> withCoreModules(Set<String> enabled) {
        Set<String> result = new HashSet<>(enabled);
        result.addAll(Set.of("SYSTEM", "COMMON", "MESSAGE", "WORKFLOW"));
        return Set.copyOf(result);
    }
}

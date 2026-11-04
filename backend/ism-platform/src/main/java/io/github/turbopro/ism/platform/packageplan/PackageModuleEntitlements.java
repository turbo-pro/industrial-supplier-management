package io.github.turbopro.ism.platform.packageplan;

import io.github.turbopro.ism.iam.authorization.TenantModuleEntitlements;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class PackageModuleEntitlements implements TenantModuleEntitlements {
    private final PackagePlanMapper mapper;

    public PackageModuleEntitlements(PackagePlanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Set<String> currentEnabledModules(long tenantId) {
        // Current locking reads stay in the caller transaction; no nested pool connection.
        return Set.copyOf(mapper.currentEnabledTenantModules(tenantId));
    }

    @Override
    public Set<String> enabledModules(long tenantId) {
        return Set.copyOf(mapper.enabledTenantModules(tenantId));
    }
}

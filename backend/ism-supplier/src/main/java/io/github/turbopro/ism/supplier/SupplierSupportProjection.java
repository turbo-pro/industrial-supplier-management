package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.support.SupportSupplierProjection;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SupplierSupportProjection implements SupportSupplierProjection {
    private final SupplierMapper mapper;

    public SupplierSupportProjection(SupplierMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Row find(long supplierId, boolean tenantAll, Set<String> organizations) {
        long tenantId = TenantContext.require().tenantId();
        if (tenantId <= 0 || (!tenantAll && organizations.isEmpty())) {
            return null;
        }
        Set<Long> organizationIds = organizations.stream().map(Long::parseLong).collect(Collectors.toUnmodifiableSet());
        return mapper.findSupportProjection(tenantId, supplierId, tenantAll, organizationIds);
    }
}

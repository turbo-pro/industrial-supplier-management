package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.quota.TenantQuotaUsageReader;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class SupplierQuotaUsage implements TenantQuotaUsageReader {
    private final SupplierMapper mapper;
    public SupplierQuotaUsage(SupplierMapper mapper) { this.mapper = mapper; }
    public String quotaCode() { return "SUPPLIER_COUNT"; }
    public long used(long tenantId) {
        long actor = TenantContext.current().map(TenantContext.Identity::actorId).orElse(0L);
        try (var scope = actor>0?TenantContext.open(tenantId, actor):TenantContext.openSystem(tenantId)) {
            return mapper.quotaCount(tenantId);
        }
    }
}

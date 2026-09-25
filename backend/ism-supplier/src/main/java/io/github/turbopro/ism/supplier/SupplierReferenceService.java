package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class SupplierReferenceService {
    private final SupplierMapper mapper;
    private final BlacklistMapper blacklist;
    public SupplierReferenceService(SupplierMapper mapper,BlacklistMapper blacklist) { this.mapper = mapper; this.blacklist=blacklist; }

    public Reference active(long supplierId) {
        var row = mapper.find(TenantContext.require().tenantId(), supplierId);
        if (row == null || !"ACTIVE".equals(row.status()) || blacklist.active(TenantContext.require().tenantId(),supplierId)>0) return null;
        return new Reference(row.organizationId(), row.supplierCode(), row.supplierName());
    }
    public Reference activeForNewBusiness(long supplierId) {
        blacklist.lockSupplier(TenantContext.require().tenantId(), supplierId);
        return active(supplierId);
    }
    public record Reference(long organizationId, String code, String name) {}
}

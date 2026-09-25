package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class SupplierReferenceService {
    private final SupplierMapper mapper;
    public SupplierReferenceService(SupplierMapper mapper) { this.mapper = mapper; }

    public Reference active(long supplierId) {
        var row = mapper.find(TenantContext.require().tenantId(), supplierId);
        if (row == null || !"ACTIVE".equals(row.status())) return null;
        return new Reference(row.organizationId(), row.supplierCode(), row.supplierName());
    }
    public record Reference(long organizationId, String code, String name) {}
}

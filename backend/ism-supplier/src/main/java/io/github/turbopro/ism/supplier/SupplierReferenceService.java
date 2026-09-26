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
        if (row == null || !"ACTIVE".equals(row.status()) || blacklist.active(TenantContext.require().tenantId(),supplierId,RestrictionBusinessDate.today())>0) return null;
        return new Reference(row.organizationId(), row.supplierCode(), row.supplierName());
    }
    public Reference activeForNewBusiness(long supplierId) {
        return eligibleForAction(supplierId,java.util.Set.of("ACTIVE"));
    }
    public Reference eligibleForAdmission(long supplierId) {
        return eligibleForAction(supplierId,java.util.Set.of("DRAFT","ACTIVE","SUSPENDED"));
    }
    private Reference eligibleForAction(long supplierId,java.util.Set<String> allowedStatuses) {
        long tenant=TenantContext.require().tenantId();
        blacklist.lockSupplier(tenant, supplierId);
        var row=mapper.findForNewBusiness(tenant,supplierId);
        if(row==null || !allowedStatuses.contains(row.status())
                || !blacklist.currentActive(tenant,supplierId,RestrictionBusinessDate.today()).isEmpty())return null;
        return new Reference(row.organizationId(),row.supplierCode(),row.supplierName());
    }
    public record Reference(long organizationId, String code, String name) {}
    public boolean lockForExitVerification(long supplierId) {
        long tenant=TenantContext.require().tenantId();
        blacklist.lockSupplier(tenant,supplierId);
        var status=mapper.currentStatusForExit(tenant,supplierId);
        return status!=null && !"EXITED".equals(status);
    }
}

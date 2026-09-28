package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierReferenceService {
    private final SupplierMapper mapper;
    private final BlacklistMapper blacklist;
    private final SupplierRestrictionEvaluator evaluator;
    private final RestrictionGateHitService gateHits;
    public SupplierReferenceService(SupplierMapper mapper,BlacklistMapper blacklist,SupplierRestrictionEvaluator evaluator,RestrictionGateHitService gateHits) { this.mapper = mapper; this.blacklist=blacklist; this.evaluator=evaluator; this.gateHits=gateHits; }

    public Reference active(long supplierId) {
        var row = mapper.find(TenantContext.require().tenantId(), supplierId);
        if (row == null || !"ACTIVE".equals(row.status()) || blacklist.active(TenantContext.require().tenantId(),supplierId,RestrictionBusinessDate.today())>0) return null;
        return new Reference(row.organizationId(), row.supplierCode(), row.supplierName());
    }
    @Transactional public Reference activeForNewBusiness(long supplierId) {
        return eligibleForBusiness(supplierId,SupplierRestrictionEvaluator.Action.GENERIC_NEW_BUSINESS);
    }
    @Transactional public Reference eligibleForAdmission(long supplierId) {
        return eligibleForBusiness(supplierId,SupplierRestrictionEvaluator.Action.QUALIFICATION_APPLY);
    }
    @Transactional public Reference eligibleForBusiness(long supplierId,SupplierRestrictionEvaluator.Action action) {
        if(action==null)throw new io.github.turbopro.ism.common.api.error.ApiException(io.github.turbopro.ism.common.api.error.CommonErrorCode.VALIDATION_FAILED,"请选择受控业务动作");
        long tenant=TenantContext.require().tenantId();
        blacklist.lockSupplier(tenant, supplierId);
        var row=mapper.findForNewBusiness(tenant,supplierId);
        if(row==null)return null;
        var evaluation=evaluator.evaluateLocked(supplierId,row.status(),action);
        if(!evaluation.hits().isEmpty())gateHits.record(supplierId,action,evaluation);
        if(evaluation.decision()==SupplierRestrictionEvaluator.Decision.DENY)return null;
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

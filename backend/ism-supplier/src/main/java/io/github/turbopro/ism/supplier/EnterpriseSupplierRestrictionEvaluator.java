package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import java.util.*;

/** Current enterprise restrictions only; never claims organization/factory scope support. */
@Service
public class EnterpriseSupplierRestrictionEvaluator implements SupplierRestrictionEvaluator {
    private final SupplierMapper suppliers;
    private final BlacklistMapper restrictions;
    public EnterpriseSupplierRestrictionEvaluator(SupplierMapper suppliers,BlacklistMapper restrictions){this.suppliers=suppliers;this.restrictions=restrictions;}
    @Override public Evaluation evaluateLocked(long supplierId,String supplierStatus,Action action){
        if(supplierId<=0||action==null)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"供应商或业务动作无效");
        long tenant=TenantContext.require().tenantId();var date=RestrictionBusinessDate.today();var hits=new ArrayList<Hit>();
        var statuses=action==Action.QUALIFICATION_APPLY?Set.of("DRAFT","ACTIVE","SUSPENDED"):Set.of("ACTIVE");
        if(supplierStatus==null||!statuses.contains(supplierStatus))hits.add(new Hit("SUPPLIER_STATUS",Long.toString(supplierId),Decision.DENY,"供应商当前状态不允许此新增业务："+supplierStatus));
        for(var restriction:restrictions.currentEffectiveCases(tenant,supplierId,date)){
            boolean warning=restriction.restrictionType()==BlacklistModels.RestrictionType.WATCH;
            hits.add(new Hit(restriction.restrictionType().name(),Long.toString(restriction.id()),warning?Decision.WARN:Decision.DENY,
                warning?"供应商处于观察期，请核对风险记录":"存在有效企业级限制，请核对限制记录"));
        }
        for(var id:suppliers.currentPendingExits(tenant,supplierId))hits.add(new Hit("PENDING_EXIT",Long.toString(id),Decision.DENY,"退出申请待处置，禁止新增业务"));
        var decision=hits.stream().anyMatch(h->h.decision()==Decision.DENY)?Decision.DENY:hits.isEmpty()?Decision.ALLOW:Decision.WARN;
        return new Evaluation(decision,date,hits);
    }
}

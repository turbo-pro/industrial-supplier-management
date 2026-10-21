package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.portal.PortalSupplierAccessGate;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.api.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Set;

@Service
public class PortalSupplierAccessGateImpl implements PortalSupplierAccessGate {
    private final SupplierMapper mapper;
    public PortalSupplierAccessGateImpl(SupplierMapper mapper){this.mapper=mapper;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void lockAvailable(long supplierId){
        long tenant=TenantContext.require().tenantId();var supplier=mapper.findForNewBusiness(tenant,supplierId);
        if(supplier==null || !Set.of("DRAFT","ACTIVE").contains(supplier.status()) || !mapper.currentPendingExits(tenant,supplierId).isEmpty())
            throw new ApiException(CommonErrorCode.UNAUTHENTICATED,"门户身份当前不可用");
    }
}

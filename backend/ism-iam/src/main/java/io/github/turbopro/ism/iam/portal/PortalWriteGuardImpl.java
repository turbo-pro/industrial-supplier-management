package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.api.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PortalWriteGuardImpl implements PortalWriteGuard {
    private final PortalRegistrationTenantGate tenants;private final PortalSupplierAccessGate suppliers;
    private final PortalAccountMapper accounts;private final PortalTenantIdentityMapper roots;
    public PortalWriteGuardImpl(PortalRegistrationTenantGate tenants,PortalSupplierAccessGate suppliers,PortalAccountMapper accounts,PortalTenantIdentityMapper roots){this.tenants=tenants;this.suppliers=suppliers;this.accounts=accounts;this.roots=roots;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void lockCurrent(){
        var i=PortalIdentityContext.require();if(i.sessionId()<=0)throw invalid();
        tenants.lockAvailable(i.tenantId());suppliers.lockAvailable(i.supplierId());
        if(!"ACTIVE".equals(roots.status(i.tenantId())))throw invalid();
        var account=accounts.lock(i.tenantId(),i.accountId());
        var session=accounts.lockOwnSession(i.tenantId(),i.sessionId(),i.accountId());
        if(account==null||account.supplierId()!=i.supplierId()||!"ACTIVE".equals(account.status())||session==null||!session.valid()||session.tokenVersion()!=account.tokenVersion())throw invalid();
    }
    private ApiException invalid(){return new ApiException(CommonErrorCode.UNAUTHENTICATED);}
}

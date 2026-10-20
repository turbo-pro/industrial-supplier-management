package io.github.turbopro.ism.platform.tenant;

import io.github.turbopro.ism.common.infrastructure.portal.PortalRegistrationTenantGate;
import io.github.turbopro.ism.common.api.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import io.github.turbopro.ism.platform.packageplan.PackageModuleEntitlements;

@Service
public class PortalRegistrationTenantGateImpl implements PortalRegistrationTenantGate {
    private final TenantMapper mapper;
    private final PackageModuleEntitlements modules;
    public PortalRegistrationTenantGateImpl(TenantMapper mapper,PackageModuleEntitlements modules){this.mapper=mapper;this.modules=modules;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void lockAvailable(long tenantId){
        var tenant=mapper.lockForPortalRegistration(tenantId);
        if(tenant==null || !"ACTIVE".equals(tenant.status()) || !"READY".equals(tenant.initializationStatus()) || !modules.enabledModules(tenantId).contains("SUPPLIER"))
            throw new ApiException(CommonErrorCode.RULE_BLOCKED,"当前无法完成门户注册");
    }
}

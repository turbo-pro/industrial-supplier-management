package io.github.turbopro.ism.platform.tenant;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.platform.packageplan.PackageModuleEntitlements;
import org.springframework.stereotype.Service;

@Service
public class PortalModuleAccessGateImpl implements PortalModuleAccessGate {
    private final PackageModuleEntitlements modules;
    public PortalModuleAccessGateImpl(PackageModuleEntitlements modules){this.modules=modules;}
    @Override public void requireAvailable(String moduleCode){
        var identity=PortalIdentityContext.require();
        if(!modules.enabledModules(identity.tenantId()).contains(moduleCode))
            throw new ApiException(CommonErrorCode.FORBIDDEN,"当前租户未开通此门户业务模块");
    }
}

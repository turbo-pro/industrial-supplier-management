package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.portal.PortalIdentityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalSupplierProfileService {
    private final PortalSupplierProfileMapper mapper;
    public PortalSupplierProfileService(PortalSupplierProfileMapper mapper){this.mapper=mapper;}

    @Transactional(readOnly=true)
    public PortalSupplierProfileModels.View current(){
        var identity=PortalIdentityContext.require();
        var profile=mapper.profile(identity.tenantId(),identity.supplierId());
        if(profile==null)throw new ApiException(CommonErrorCode.NOT_FOUND,"当前企业档案不可用，请联系邀请方");
        return new PortalSupplierProfileModels.View(profile,mapper.categories(identity.tenantId(),identity.supplierId()));
    }
}

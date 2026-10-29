package io.github.turbopro.ism.iam.organization;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Trusted platform provisioning contract. Shares the caller's initialization transaction. */
@Service
public class OrganizationProvisioner {
    private final OrganizationMapper mapper;
    public OrganizationProvisioner(OrganizationMapper mapper){this.mapper=mapper;}
    public record Structure(long primaryOrganizationId,Long defaultSiteId){}
    @Transactional(propagation=Propagation.MANDATORY)
    public Structure initialize(long tenantId,long administratorId,long root,long site,String mode,String rootName,String siteName){
        if(mode!=null&&!java.util.Set.of("SINGLE_SITE","MULTI_SITE").contains(mode))
            throw new io.github.turbopro.ism.common.api.error.ApiException(io.github.turbopro.ism.common.api.error.CommonErrorCode.VALIDATION_FAILED);
        try(var context=TenantContext.open(tenantId,administratorId)){
            boolean single="SINGLE_SITE".equals(mode);
            mapper.insert(tenantId,root,null,single?"MAIN_SITE":"HEADQUARTERS",single?siteName:rootName,single?"SITE":"HEADQUARTERS",0);
            mapper.insertSelf(tenantId,root);
            if(single)return new Structure(root,root);
            // Null mode is reserved for existing trusted in-process initialization callers.
            if(mode==null)return new Structure(root,null);
            mapper.insert(tenantId,site,root,"MAIN_SITE",siteName,"SITE",0);
            mapper.insertSelf(tenantId,site);mapper.insertAncestors(tenantId,site,root);
            return new Structure(root,site);
        }
    }
}

package io.github.turbopro.ism.iam.organization;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Component;
@Component
class OrganizationStructureLock {
    private final OrganizationStructureRootMapper mapper;
    OrganizationStructureLock(OrganizationStructureRootMapper mapper){this.mapper=mapper;}
    // All callers already run inside the structural mutation transaction.
    void acquire(){
        String status=mapper.lock(TenantContext.require().tenantId());
        if(status==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(!"ACTIVE".equals(status))throw new ApiException(CommonErrorCode.RULE_BLOCKED,"租户身份域当前不可变更组织结构");
    }
}

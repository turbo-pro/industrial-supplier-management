package io.github.turbopro.ism.iam.access;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class UserReferenceService {
    private final AccessMapper mapper;
    public UserReferenceService(AccessMapper mapper){this.mapper=mapper;}
    public boolean activeForAssignment(long userId){
        if(userId<=0)return false;
        Long active=mapper.activeUserForAssignment(TenantContext.require().tenantId(),userId);
        return active!=null&&active==userId;
    }
}

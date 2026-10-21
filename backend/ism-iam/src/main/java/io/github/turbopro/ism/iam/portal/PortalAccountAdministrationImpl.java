package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.operation.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Map;

@Service
public class PortalAccountAdministrationImpl implements PortalAccountAdministration {
    private final PortalAccountAdministrationMapper mapper;
    private final AuditService audit;
    public PortalAccountAdministrationImpl(PortalAccountAdministrationMapper mapper,AuditService audit){this.mapper=mapper;this.audit=audit;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Page list(long supplierId,int page,int size){
        if(page<1 || size<1 || size>100)throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        long tenant=TenantContext.require().tenantId();
        return new Page(page,size,mapper.count(tenant,supplierId),mapper.list(tenant,supplierId,size,(long)(page-1)*size));
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Account change(long supplierId,long id,int version,Action action,String reason){
        if(version<0 || action==null || reason==null || reason.isBlank() || reason.trim().length()>500)
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        var tenant=TenantContext.require();
        var before=mapper.lock(tenant.tenantId(),supplierId,id);
        if(before==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(before.version()!=version)throw new ApiException(CommonErrorCode.CONFLICT,"账号已变化，请刷新后重新操作");
        String status=switch(action){case ENABLE -> "ACTIVE";case DISABLE -> "DISABLED";case REVOKE_SESSIONS -> before.status();};
        if(action!=Action.REVOKE_SESSIONS && status.equals(before.status()))throw new ApiException(CommonErrorCode.CONFLICT,"账号已处于目标状态，请刷新");
        if(mapper.change(tenant.tenantId(),supplierId,id,version,status)!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        int revoked=mapper.revokeAll(tenant.tenantId(),id);
        audit.append(new AuditService.AuditCommand("SUPPLIER_PORTAL_ACCOUNT_"+action.name(),"SUPPLIER_PORTAL_ACCOUNT",id,null,
                Map.of("status",before.status(),"version",before.version()),
                Map.of("status",status,"version",version+1,"supplierId",supplierId,"revokedSessions",revoked,"reason",reason.trim()),null,null));
        return mapper.lock(tenant.tenantId(),supplierId,id);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ExitRecovery recoverForExit(long supplierId,long applicationId){
        if(supplierId<=0 || applicationId<=0)throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        var tenant=TenantContext.require();long after=0,processed=0,revoked=0;
        while(true){
            var batch=mapper.exitBatch(tenant.tenantId(),supplierId,after);
            if(batch.isEmpty())break;
            for(var account:batch){
                long id=Long.parseLong(account.id());
                if(mapper.change(tenant.tenantId(),supplierId,id,account.version(),"DISABLED")!=1)throw new ApiException(CommonErrorCode.CONFLICT);
                int count=mapper.revokeAll(tenant.tenantId(),id);
                audit.append(new AuditService.AuditCommand("SUPPLIER_PORTAL_ACCOUNT_EXIT_RECOVERY","SUPPLIER_PORTAL_ACCOUNT",id,null,
                        Map.of("status",account.status(),"version",account.version()),
                        Map.of("status","DISABLED","version",account.version()+1,"supplierId",supplierId,"applicationId",applicationId,"revokedSessions",count),null,null));
                after=id;processed++;revoked+=count;
            }
        }
        return new ExitRecovery(processed,revoked);
    }
}

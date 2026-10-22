package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration;
import io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.*;
import io.github.turbopro.ism.common.infrastructure.portal.PortalPasswordRecoveryAdministration;
import io.github.turbopro.ism.common.infrastructure.portal.PortalRegistrationTenantGate;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class PortalAccountManagementService {
    public record View(boolean canManage,boolean canRecover,Page accounts) {}
    private final SupplierMapper suppliers;
    private final PortalAccountAdministration accounts;
    private final PortalPasswordRecoveryAdministration recovery;
    private final PortalRegistrationTenantGate tenants;
    public PortalAccountManagementService(SupplierMapper suppliers,PortalAccountAdministration accounts,PortalPasswordRecoveryAdministration recovery,PortalRegistrationTenantGate tenants){this.suppliers=suppliers;this.accounts=accounts;this.recovery=recovery;this.tenants=tenants;}
    @Transactional public View list(long supplierId,int page,int size){
        guard(supplierId,"supplier:master:view");
        return new View(AuthorizationContext.require().hasAction("supplier:master:update"),recovery.enabled()&&AuthorizationContext.require().hasAction("supplier:portal:recover"),accounts.list(supplierId,page,size));
    }
    @Transactional public PortalPasswordRecoveryAdministration.State recoveryState(long supplierId,long accountId){guard(supplierId,"supplier:portal:recover");return recovery.current(supplierId,accountId);}
    @Transactional public PortalPasswordRecoveryAdministration.Issued issueRecovery(long supplierId,long accountId,int version,String reason,boolean verified){
        if(!AuthorizationContext.require().hasAction("supplier:portal:recover"))throw new ApiException(CommonErrorCode.FORBIDDEN);
        tenants.lockAvailable(TenantContext.require().tenantId());
        var supplier=guard(supplierId,"supplier:portal:recover");
        if(!Set.of("DRAFT","ACTIVE").contains(supplier.status()) || !suppliers.currentPendingExits(TenantContext.require().tenantId(),supplierId).isEmpty())throw new ApiException(CommonErrorCode.RULE_BLOCKED,"供应商当前不能签发恢复凭证");
        return recovery.issue(supplierId,accountId,version,reason,verified);
    }
    @Transactional public void revokeRecovery(long supplierId,long accountId,int version,String reason){guard(supplierId,"supplier:portal:recover");recovery.revoke(supplierId,accountId,version,reason);}
    @Transactional public Account change(long supplierId,long accountId,int version,Action action,String reason){
        var supplier=guard(supplierId,"supplier:master:update");
        if(action==Action.ENABLE && (!Set.of("DRAFT","ACTIVE").contains(supplier.status()) || !suppliers.currentPendingExits(TenantContext.require().tenantId(),supplierId).isEmpty()))
            throw new ApiException(CommonErrorCode.RULE_BLOCKED,"供应商不可用或正在退出处置，不能恢复门户账号");
        return accounts.change(supplierId,accountId,version,action,reason);
    }
    private SupplierModels.SupplierRow guard(long id,String permission){
        if(!AuthorizationContext.require().hasAction(permission))throw new ApiException(CommonErrorCode.FORBIDDEN);
        var tenant=TenantContext.require();var row=suppliers.findForNewBusiness(tenant.tenantId(),id);
        if(row==null || !AuthorizationContext.require().dataScope("supplier:master").allows(new DataTarget(row.organizationId(),null,row.createdBy(),row.createdBy()),tenant.actorId()))throw new ApiException(CommonErrorCode.NOT_FOUND);
        return row;
    }
}

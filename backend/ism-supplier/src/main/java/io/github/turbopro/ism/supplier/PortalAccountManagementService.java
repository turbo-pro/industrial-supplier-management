package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration;
import io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class PortalAccountManagementService {
    public record View(boolean canManage,Page accounts) {}
    private final SupplierMapper suppliers;
    private final PortalAccountAdministration accounts;
    public PortalAccountManagementService(SupplierMapper suppliers,PortalAccountAdministration accounts){this.suppliers=suppliers;this.accounts=accounts;}
    @Transactional public View list(long supplierId,int page,int size){
        guard(supplierId,"supplier:master:view");
        return new View(AuthorizationContext.require().hasAction("supplier:master:update"),accounts.list(supplierId,page,size));
    }
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

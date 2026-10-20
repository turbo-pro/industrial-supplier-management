package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.operation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class PortalRegistrationService {
    private final PortalInvitationTokenLocator locator;private final PortalInvitationMapper invitations;
    private final SupplierMapper suppliers;private final PortalAccountProvisioner accounts;
    private final PortalRegistrationTenantGate tenants;private final OperationIdGenerator ids;private final AuditService audit;
    private final boolean enabled;
    public PortalRegistrationService(PortalInvitationTokenLocator locator,PortalInvitationMapper invitations,SupplierMapper suppliers,PortalAccountProvisioner accounts,PortalRegistrationTenantGate tenants,OperationIdGenerator ids,AuditService audit,@Value("${ism.portal.registration-enabled:false}") boolean enabled){
        this.locator=locator;this.invitations=invitations;this.suppliers=suppliers;this.accounts=accounts;this.tenants=tenants;this.ids=ids;this.audit=audit;this.enabled=enabled;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public PortalRegistrationModels.Registered register(PortalRegistrationModels.Register command){
        if(!enabled)throw new ApiException(CommonErrorCode.RULE_BLOCKED,"门户注册尚未启用");
        if(command.invitationToken()==null || !command.invitationToken().matches("[A-Za-z0-9_-]{43}"))throw unavailable();
        var location=locator.locate(hash(command.invitationToken()));
        if(location==null)throw unavailable();
        long accountId=ids.nextId();
        // Scope is derived solely from the capability, never headers/body tenant IDs; no internal grants are installed.
        try(var scope=TenantContext.open(location.tenantId(),accountId)){
            tenants.lockAvailable(location.tenantId());
            var supplier=suppliers.findForNewBusiness(location.tenantId(),location.supplierId());
            if(supplier==null || !Set.of("DRAFT","ACTIVE").contains(supplier.status()) || !suppliers.currentPendingExits(location.tenantId(),location.supplierId()).isEmpty())throw unavailable();
            var invitation=invitations.lockRedemption(location.tenantId(),location.supplierId(),location.id());
            if(invitation==null || !"PENDING".equals(invitation.status()) || !invitation.valid())throw unavailable();
            accounts.create(accountId,location.supplierId(),command.username(),invitation.recipientName(),invitation.recipientEmail(),command.password());
            if(invitations.accept(location.tenantId(),location.supplierId(),location.id(),accountId)!=1)throw unavailable();
            audit.append(new AuditService.AuditCommand("SUPPLIER_PORTAL_REGISTER","PORTAL_ACCOUNT",accountId,null,Map.of(),Map.of("supplierId",location.supplierId(),"invitationId",location.id()),null,null));
            return new PortalRegistrationModels.Registered(Long.toString(accountId),Long.toString(location.tenantId()),Long.toString(location.supplierId()),command.username().toLowerCase(Locale.ROOT));
        }
    }
    static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private ApiException unavailable(){return new ApiException(CommonErrorCode.RULE_BLOCKED,"邀请无效或当前无法注册，请联系邀请方");}
}

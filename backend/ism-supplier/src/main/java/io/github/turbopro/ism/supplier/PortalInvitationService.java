package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class PortalInvitationService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final SupplierMapper suppliers;
    private final PortalInvitationMapper invitations;
    private final OperationIdGenerator ids;
    private final AuditService audit;
    public PortalInvitationService(SupplierMapper suppliers,PortalInvitationMapper invitations,OperationIdGenerator ids,AuditService audit) {
        this.suppliers=suppliers;this.invitations=invitations;this.ids=ids;this.audit=audit;
    }
    @Transactional public PortalInvitationModels.History history(long supplierId) {
        var tenant=TenantContext.require();lockAllowed(supplierId);
        return new PortalInvitationModels.History(invitations.latest(tenant.tenantId(),supplierId),AuthorizationContext.require().hasAction("supplier:master:update"),invitations.history(tenant.tenantId(),supplierId));
    }
    @Transactional public PortalInvitationModels.Issued issue(long supplierId,PortalInvitationModels.Issue command) {
        var tenant=TenantContext.require();var supplier=lockAllowed(supplierId);
        if(!Set.of("DRAFT","ACTIVE").contains(supplier.status()) || !suppliers.currentPendingExits(tenant.tenantId(),supplierId).isEmpty())
            throw new ApiException(CommonErrorCode.RULE_BLOCKED,"供应商已停用、退出或正在退出处置，不能签发门户邀请");
        int generation=invitations.latest(tenant.tenantId(),supplierId);
        if(generation!=command.expectedGeneration())throw new ApiException(CommonErrorCode.CONFLICT,"邀请已发生变化，请刷新后重新签发");
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash;
        try {hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        invitations.replacePending(tenant.tenantId(),supplierId);
        long id=ids.nextId();
        invitations.insert(id,tenant.tenantId(),supplierId,Math.incrementExact(generation),command.recipientName().trim(),command.recipientEmail().trim(),hash,command.validHours(),tenant.actorId());
        audit.append(new AuditService.AuditCommand("SUPPLIER_PORTAL_INVITATION_ISSUE","SUPPLIER",supplierId,null,Map.of(),Map.of("invitationId",id,"generation",generation+1),null,null));
        return new PortalInvitationModels.Issued(invitations.find(tenant.tenantId(),supplierId,id),token);
    }
    @Transactional public PortalInvitationModels.Invitation revoke(long supplierId,long id,PortalInvitationModels.Revoke command) {
        var tenant=TenantContext.require();lockAllowed(supplierId);
        if(invitations.find(tenant.tenantId(),supplierId,id)==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(invitations.revoke(tenant.tenantId(),supplierId,id,command.version(),tenant.actorId(),command.reason().trim())!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        audit.append(new AuditService.AuditCommand("SUPPLIER_PORTAL_INVITATION_REVOKE","SUPPLIER",supplierId,null,Map.of(),Map.of("invitationId",id,"reason",command.reason().trim()),null,null));
        return invitations.find(tenant.tenantId(),supplierId,id);
    }
    private SupplierModels.SupplierRow lockAllowed(long id) {
        var tenant=TenantContext.require();var row=suppliers.findForNewBusiness(tenant.tenantId(),id);
        if(row==null || !AuthorizationContext.require().dataScope("supplier:master").allows(new DataTarget(row.organizationId(),null,row.createdBy(),row.createdBy()),tenant.actorId()))
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        return row;
    }
}

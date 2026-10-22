package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.operation.*;
import io.github.turbopro.ism.iam.auth.PasswordPolicy;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Service
public class PortalPasswordRecoveryService implements PortalPasswordRecoveryAdministration {
    public record Reset(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String recoveryToken,@NotBlank @Size(min=12,max=72) String newPassword) {
        @Override public String toString(){return "PortalRecoveryReset[credentials=REDACTED]";}
    }
    private final PortalPasswordRecoveryMapper mapper;private final PortalPasswordRecoveryLocator locator;
    private final PortalAccountMapper accounts;private final PortalAccountAdministrationMapper management;
    private final PortalRegistrationTenantGate tenants;private final PortalSupplierAccessGate suppliers;private final PortalTenantIdentityMapper identity;
    private final PasswordEncoder passwords;private final OperationIdGenerator ids;private final AuditService audit;private final boolean enabled;
    private final TransactionTemplate tx;
    private static final SecureRandom RANDOM=new SecureRandom();
    public PortalPasswordRecoveryService(PortalPasswordRecoveryMapper mapper,PortalPasswordRecoveryLocator locator,PortalAccountMapper accounts,
            PortalAccountAdministrationMapper management,PortalRegistrationTenantGate tenants,PortalSupplierAccessGate suppliers,
            PortalTenantIdentityMapper identity,PasswordEncoder passwords,OperationIdGenerator ids,AuditService audit,PlatformTransactionManager manager,
            @Value("${ism.portal.password-recovery-enabled:false}") boolean enabled,@Value("${ism.portal.authentication-enabled:false}") boolean authentication){
        this.mapper=mapper;this.locator=locator;this.accounts=accounts;this.management=management;this.tenants=tenants;this.suppliers=suppliers;
        this.identity=identity;this.passwords=passwords;this.ids=ids;this.audit=audit;this.enabled=enabled&&authentication;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Override public boolean enabled(){return enabled;}
    private void requireEnabled(){if(!enabled)throw new ApiException(CommonErrorCode.RULE_BLOCKED,"门户密码恢复尚未启用");}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public State current(long supplierId,long accountId){
        requireEnabled();var t=TenantContext.require();
        if(management.lock(t.tenantId(),supplierId,accountId)==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        return mapper.current(t.tenantId(),accountId);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Issued issue(long supplierId,long accountId,int version,String reason,boolean identityVerified){
        requireEnabled();validate(reason,version);if(!identityVerified)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"必须先核实账号持有人身份");
        var t=TenantContext.require();var before=management.lock(t.tenantId(),supplierId,accountId);
        if(before==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(before.version()!=version)throw new ApiException(CommonErrorCode.CONFLICT);
        if(!"ACTIVE".equals(before.status()))throw new ApiException(CommonErrorCode.RULE_BLOCKED,"停用账号不能签发恢复凭证");
        var account=accounts.lock(t.tenantId(),accountId);
        if(management.advanceVersion(t.tenantId(),supplierId,accountId,version)!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        mapper.retire(t.tenantId(),accountId,"REPLACED",t.actorId());
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);long id=ids.nextId();
        mapper.insert(id,t.tenantId(),accountId,PortalAuthService.hash(token),version+1,account.tokenVersion(),t.actorId(),reason.trim());
        audit.append(new AuditService.AuditCommand("PORTAL_RECOVERY_ISSUE","PORTAL_ACCOUNT",accountId,null,Map.of(),Map.of("recoveryId",id,"reason",reason.trim(),"identityVerified",true,"version",version+1),null,null));
        return new Issued(token,mapper.current(t.tenantId(),accountId).expiresAt(),version+1);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void revoke(long supplierId,long accountId,int version,String reason){
        requireEnabled();validate(reason,version);var t=TenantContext.require();var account=management.lock(t.tenantId(),supplierId,accountId);
        if(account==null)throw new ApiException(CommonErrorCode.NOT_FOUND);if(account.version()!=version)throw new ApiException(CommonErrorCode.CONFLICT);
        if(mapper.retire(t.tenantId(),accountId,"REVOKED",t.actorId())!=1)throw new ApiException(CommonErrorCode.CONFLICT,"无待撤销凭证，请刷新核查");
        if(management.advanceVersion(t.tenantId(),supplierId,accountId,version)!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        audit.append(new AuditService.AuditCommand("PORTAL_RECOVERY_REVOKE","PORTAL_ACCOUNT",accountId,null,Map.of(),Map.of("reason",reason.trim(),"version",version+1),null,null));
    }
    public void reset(Reset command){
        requireEnabled();
        if(command.recoveryToken()==null || !command.recoveryToken().matches("[A-Za-z0-9_-]{43}"))throw unavailable();
        if(!PasswordPolicy.isStrong(command.newPassword()) || command.newPassword().getBytes(StandardCharsets.UTF_8).length>72)throw new ApiException(CommonErrorCode.RULE_BLOCKED,"新密码不符合强度或72字节要求");
        String hash=PortalAuthService.hash(command.recoveryToken());var location=locator.locate(hash);if(location==null)throw unavailable();
        tx.executeWithoutResult(status->{try(var context=TenantContext.open(location.tenantId(),location.accountId())){
            tenants.lockAvailable(location.tenantId());suppliers.lockAvailable(location.supplierId());
            if(!"ACTIVE".equals(identity.status(location.tenantId())))throw unavailable();
            var account=accounts.lock(location.tenantId(),location.accountId());var managed=management.lock(location.tenantId(),location.supplierId(),location.accountId());
            var grant=mapper.lock(location.tenantId(),location.accountId(),hash);
            if(account==null || managed==null || !"ACTIVE".equals(account.status()) || grant==null || !grant.valid() || grant.accountVersion()!=managed.version() || grant.tokenVersion()!=account.tokenVersion())throw unavailable();
            if(passwords.matches(command.newPassword(),account.passwordHash()))throw new ApiException(CommonErrorCode.RULE_BLOCKED,"新密码不能与原密码相同");
            accounts.changePassword(location.tenantId(),account.id(),passwords.encode(command.newPassword()));
            int revoked=accounts.revokeAll(location.tenantId(),account.id());
            if(mapper.consume(location.tenantId(),grant.id())!=1)throw unavailable();
            audit.append(new AuditService.AuditCommand("PORTAL_RECOVERY_RESET","PORTAL_ACCOUNT",account.id(),null,Map.of(),Map.of("recoveryId",grant.id(),"revokedSessions",revoked),null,null));
        }});
    }
    private ApiException unavailable(){return new ApiException(CommonErrorCode.RULE_BLOCKED,"恢复凭证无效或当前不能恢复，请联系管理员核查");}
    private void validate(String reason,int version){if(version<0 || reason==null || reason.isBlank() || reason.trim().length()>500)throw new ApiException(CommonErrorCode.VALIDATION_FAILED);}
}

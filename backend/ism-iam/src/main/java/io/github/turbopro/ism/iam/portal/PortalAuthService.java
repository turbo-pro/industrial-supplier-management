package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.operation.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PortalAuthService {
    private final PortalIdentityLocator locator;private final PortalAccountMapper accounts;private final PortalTenantIdentityMapper identity;
    private final PortalRegistrationTenantGate tenants;private final PortalSupplierAccessGate suppliers;
    private final PasswordEncoder passwords;private final String dummyHash;private final OperationIdGenerator ids;private final AuditService audit;
    private final TransactionTemplate tx;private final boolean enabled;
    private static final SecureRandom RANDOM=new SecureRandom();
    public PortalAuthService(PortalIdentityLocator locator,PortalAccountMapper accounts,PortalTenantIdentityMapper identity,
            PortalRegistrationTenantGate tenants,PortalSupplierAccessGate suppliers,PasswordEncoder passwords,OperationIdGenerator ids,
            AuditService audit,PlatformTransactionManager manager,@Value("${ism.portal.authentication-enabled:false}") boolean enabled){
        this.locator=locator;this.accounts=accounts;this.identity=identity;this.tenants=tenants;this.suppliers=suppliers;this.passwords=passwords;
        this.ids=ids;this.audit=audit;this.enabled=enabled;this.dummyHash=passwords.encode("portal-invalid-user-dummy");
        this.tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public PortalAuthModels.Session login(PortalAuthModels.Login command){
        requireEnabled();
        if(command.password()==null || command.password().getBytes(StandardCharsets.UTF_8).length>72)throw invalid();
        var location=locator.account(command.tenantCode(),command.username().toLowerCase(Locale.ROOT));
        if(location==null){passwords.matches(command.password(),dummyHash);throw invalid();}
        // Returning null commits failure counters; no broad noRollbackFor that might commit a partial session/audit.
        var result=tx.execute(status->{try(var context=TenantContext.open(location.tenantId(),location.accountId())){
            var before=accounts.find(location.tenantId(),location.accountId());if(before==null)throw invalid();
            gates(location.tenantId(),before.supplierId());
            var account=accounts.lock(location.tenantId(),location.accountId());
            if(account==null || account.supplierId()!=before.supplierId() || !account.username().equalsIgnoreCase(command.username()) || !"ACTIVE".equals(account.status()) || account.locked())return null;
            if(!passwords.matches(command.password(),account.passwordHash())){accounts.failure(location.tenantId(),account.id());return null;}
            byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);long sessionId=ids.nextId();
            accounts.success(location.tenantId(),account.id());accounts.session(sessionId,location.tenantId(),account.id(),hash(token),account.tokenVersion());
            audit.append(new AuditService.AuditCommand("PORTAL_LOGIN","PORTAL_ACCOUNT",account.id(),null,Map.of(),Map.of("sessionId",sessionId),null,null));
            var user=new PortalAuthModels.Principal(account.id(),location.tenantId(),account.supplierId(),sessionId,account.username(),account.displayName());
            return new PortalAuthModels.Session(user.user(),token,accounts.expiry(location.tenantId(),sessionId));
        }});
        if(result==null)throw invalid();return result;
    }
    public PortalAuthModels.Principal authenticate(String token){
        requireEnabled();if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))throw invalid();
        String hash=hash(token);var location=locator.session(hash);if(location==null)throw invalid();
        return tx.execute(status->{try(var context=TenantContext.open(location.tenantId(),location.accountId())){
            var before=accounts.find(location.tenantId(),location.accountId());if(before==null)throw invalid();gates(location.tenantId(),before.supplierId());
            var account=accounts.lock(location.tenantId(),location.accountId());var session=accounts.lockSession(location.tenantId(),hash);
            if(account==null || account.supplierId()!=before.supplierId() || !"ACTIVE".equals(account.status()) || session==null || !session.valid() || session.accountId()!=account.id() || session.tokenVersion()!=account.tokenVersion())throw invalid();
            return new PortalAuthModels.Principal(account.id(),location.tenantId(),account.supplierId(),session.id(),account.username(),account.displayName());
        }});
    }
    public void logout(PortalAuthModels.Principal principal){
        tx.executeWithoutResult(status->{try(var context=TenantContext.open(principal.tenantId(),principal.accountId())){
            // Scope and session/account predicates prevent a token from revoking another session.
            if(accounts.revoke(principal.tenantId(),principal.sessionId(),principal.accountId())==1)
                audit.append(new AuditService.AuditCommand("PORTAL_LOGOUT","PORTAL_ACCOUNT",principal.accountId(),null,Map.of(),Map.of("sessionId",principal.sessionId()),null,null));
        }});
    }
    public void changePassword(PortalAuthModels.Principal principal,PortalAuthModels.ChangePassword command){
        requireEnabled();
        if(!io.github.turbopro.ism.iam.auth.PasswordPolicy.isStrong(command.newPassword())
                || command.newPassword().getBytes(StandardCharsets.UTF_8).length>72)
            throw new ApiException(CommonErrorCode.RULE_BLOCKED,"新密码至少12位，包含大小写字母、数字和符号，且不超过72个UTF-8字节");
        if(command.currentPassword()==null || command.currentPassword().getBytes(StandardCharsets.UTF_8).length>72)
            throw new ApiException(CommonErrorCode.RULE_BLOCKED,"原密码不正确或账号暂时锁定");
        boolean changed=Boolean.TRUE.equals(tx.execute(status->{try(var context=TenantContext.open(principal.tenantId(),principal.accountId())){
            gates(principal.tenantId(),principal.supplierId());
            var account=accounts.lock(principal.tenantId(),principal.accountId());
            var session=accounts.lockOwnSession(principal.tenantId(),principal.sessionId(),principal.accountId());
            // Recheck inside the write transaction: filter authentication may precede a concurrent revocation.
            if(account==null || account.supplierId()!=principal.supplierId() || !"ACTIVE".equals(account.status())
                    || session==null || !session.valid() || session.tokenVersion()!=account.tokenVersion())throw invalid();
            if(account.locked())return false;
            if(!passwords.matches(command.currentPassword(),account.passwordHash())){accounts.failure(principal.tenantId(),account.id());return false;}
            if(passwords.matches(command.newPassword(),account.passwordHash()))throw new ApiException(CommonErrorCode.RULE_BLOCKED,"新密码不能与原密码相同");
            accounts.changePassword(principal.tenantId(),account.id(),passwords.encode(command.newPassword()));
            int revoked=accounts.revokeAll(principal.tenantId(),account.id());
            audit.append(new AuditService.AuditCommand("PORTAL_PASSWORD_CHANGE","PORTAL_ACCOUNT",account.id(),null,Map.of(),Map.of("revokedSessions",revoked),null,null));
            return true;
        }}));
        // Commit failed-password counters, but never commit a partial password/session/audit change.
        if(!changed)throw new ApiException(CommonErrorCode.RULE_BLOCKED,"原密码不正确或账号暂时锁定，请稍后再试");
    }
    private void gates(long tenantId,long supplierId){
        try{tenants.lockAvailable(tenantId);suppliers.lockAvailable(supplierId);if(!"ACTIVE".equals(identity.status(tenantId)))throw invalid();}
        catch(ApiException unavailable){throw invalid();}
    }
    private void requireEnabled(){if(!enabled)throw new ApiException(CommonErrorCode.RULE_BLOCKED,"门户登录尚未启用");}
    private ApiException invalid(){return new ApiException(CommonErrorCode.UNAUTHENTICATED,"门户凭证无效或身份当前不可用");}
    static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}

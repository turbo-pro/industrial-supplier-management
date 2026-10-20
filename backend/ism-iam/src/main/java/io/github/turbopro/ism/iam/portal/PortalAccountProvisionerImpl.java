package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.infrastructure.portal.PortalAccountProvisioner;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.iam.auth.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.dao.DuplicateKeyException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class PortalAccountProvisionerImpl implements PortalAccountProvisioner {
    private final PortalAccountMapper mapper;private final PasswordEncoder passwords;private final PortalTenantIdentityMapper tenants;
    public PortalAccountProvisionerImpl(PortalAccountMapper mapper,PasswordEncoder passwords,PortalTenantIdentityMapper tenants){this.mapper=mapper;this.passwords=passwords;this.tenants=tenants;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void create(long id,long supplierId,String username,String displayName,String email,String password){
        if(!"ACTIVE".equals(tenants.status(TenantContext.require().tenantId())))throw new ApiException(CommonErrorCode.RULE_BLOCKED,"当前无法完成门户注册");
        if(username==null || !username.matches("[A-Za-z][A-Za-z0-9_.-]{2,63}"))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"门户用户名格式不正确");
        if(!PasswordPolicy.isStrong(password) || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"密码须至少12位并含大小写字母、数字、符号，UTF-8长度不超过72字节");
        try {mapper.insert(id,TenantContext.require().tenantId(),supplierId,username.toLowerCase(Locale.ROOT),displayName,email,passwords.encode(password));}
        catch(DuplicateKeyException conflict){throw new ApiException(CommonErrorCode.CONFLICT,"无法创建门户身份，请联系邀请方核查账号或绑定");}
    }
}

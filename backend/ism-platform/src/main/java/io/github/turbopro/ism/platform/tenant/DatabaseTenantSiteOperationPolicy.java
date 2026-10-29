package io.github.turbopro.ism.platform.tenant;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantSiteOperationPolicy;
import io.github.turbopro.ism.common.api.error.*;
import org.springframework.stereotype.Component;
@Component
public class DatabaseTenantSiteOperationPolicy implements TenantSiteOperationPolicy {
    private final TenantMapper mapper;
    public DatabaseTenantSiteOperationPolicy(TenantMapper mapper){this.mapper=mapper;}
    public boolean requiresSiteContext(long tenantId){
        String mode=mapper.siteOperationMode(tenantId);
        if(mode==null)return false; // Unclassified legacy tenants are not silently reclassified.
        if("SINGLE_SITE".equals(mode)||"MULTI_SITE".equals(mode))return true;
        throw new ApiException(CommonErrorCode.RULE_BLOCKED,"租户运营模式无法识别，请联系管理员核对");
    }
}

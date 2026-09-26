package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.iam.configuration.ConfigurationMapper;
import io.github.turbopro.ism.supplier.ObservationPolicy;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class TenantObservationPolicy implements ObservationPolicy {
    public static final String KEY="restriction.watchPeriodDays";
    private final ConfigurationMapper mapper;
    public TenantObservationPolicy(ConfigurationMapper mapper){this.mapper=mapper;}
    @Override public Duration period(){
        String value=mapper.currentSettingValue(TenantContext.require().tenantId(),KEY);
        if(value==null)return Duration.ofDays(7);
        try{
            long days=Long.parseLong(value);
            if(days<1||days>3650)throw new IllegalArgumentException();
            return Duration.ofDays(days);
        }catch(IllegalArgumentException e){
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"租户观察期配置无效，解除核验已阻止");
        }
    }
}

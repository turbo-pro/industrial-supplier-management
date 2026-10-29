package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.iam.configuration.TenantObservationSettings;
import io.github.turbopro.ism.supplier.ObservationPolicy;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class TenantObservationPolicy implements ObservationPolicy {
    public static final String KEY=TenantObservationSettings.KEY;
    private final TenantObservationSettings settings;
    public TenantObservationPolicy(TenantObservationSettings settings){this.settings=settings;}
    @Override public Duration period(){
        String value=settings.currentWatchPeriodDays();
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

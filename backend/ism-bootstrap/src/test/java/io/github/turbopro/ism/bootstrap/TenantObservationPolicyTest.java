package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.iam.configuration.ConfigurationMapper;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantObservationPolicyTest {
    @Test void resolvesCurrentTenantAndDefault(){
        var mapper=mock(ConfigurationMapper.class);var policy=new TenantObservationPolicy(mapper);
        when(mapper.currentSettingValue(10,TenantObservationPolicy.KEY)).thenReturn("30");
        try(var t=TenantContext.open(10,1)){assertEquals(Duration.ofDays(30),policy.period());}
        try(var t=TenantContext.open(20,1)){assertEquals(Duration.ofDays(7),policy.period());}
    }
    @Test void corruptStoredPolicyNeverGrantsEarlyRelease(){
        var mapper=mock(ConfigurationMapper.class);var policy=new TenantObservationPolicy(mapper);
        for(String value:new String[]{"0","-1","3651","abc","999999999999999999999"}){
            when(mapper.currentSettingValue(10,TenantObservationPolicy.KEY)).thenReturn(value);
            try(var t=TenantContext.open(10,1)){assertThrows(ApiException.class,policy::period);}
        }
    }
}

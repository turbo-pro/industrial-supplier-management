package io.github.turbopro.ism.iam.configuration;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationServiceTest {
    @Test void rejectsInvalidObservationDaysBeforeWriting(){
        var mapper=mock(ConfigurationMapper.class);var definitions=mock(SystemConfigurationMapper.class);
        when(definitions.settingType("restriction.watchPeriodDays")).thenReturn("INTEGER");
        var service=new ConfigurationService(mapper,definitions,mock(OperationIdGenerator.class));
        for(String value:new String[]{"0","-1","3651","7.5","abc","999999999999999999999"}){
            try(var t=TenantContext.open(10,1)){
                assertThrows(ApiException.class,()->service.updateSetting("restriction.watchPeriodDays",new ConfigurationModels.UpdateSetting(value,0)));
            }
        }
        verifyNoInteractions(mapper);
    }
    @Test void concurrentFirstWriteReturnsConflict(){
        var mapper=mock(ConfigurationMapper.class);var definitions=mock(SystemConfigurationMapper.class);var ids=mock(OperationIdGenerator.class);
        when(definitions.settingType("restriction.watchPeriodDays")).thenReturn("INTEGER");when(ids.nextId()).thenReturn(80L);
        when(mapper.insertSetting(10,80,"restriction.watchPeriodDays","INTEGER","7")).thenThrow(new DuplicateKeyException("duplicate"));
        var service=new ConfigurationService(mapper,definitions,ids);
        try(var t=TenantContext.open(10,1)){
            var failure=assertThrows(ApiException.class,()->service.updateSetting("restriction.watchPeriodDays",new ConfigurationModels.UpdateSetting("7",0)));
            assertEquals(CommonErrorCode.CONFLICT,failure.errorCode());
        }
    }
}

package io.github.turbopro.ism.iam.configuration;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationServiceTest {
    @Test void escalationDirectoryIsBoundedAndTenantScoped(){
        var mapper=mock(ConfigurationMapper.class);var service=new ConfigurationService(mapper,mock(SystemConfigurationMapper.class),mock(OperationIdGenerator.class));
        when(mapper.countEscalationUsers(10,"张")).thenReturn(1L);
        when(mapper.escalationUsers(10,"张",0,20)).thenReturn(java.util.List.of(new ConfigurationModels.EscalationUserRow(99,"zhang","张经理")));
        try(var tenant=TenantContext.open(10,7)){
            var page=service.escalationUsers(" 张 ",0,20);
            assertEquals(1,page.total());assertEquals("99",page.items().get(0).id());
            assertThrows(ApiException.class,()->service.escalationUsers("",0,51));
            assertThrows(ApiException.class,()->service.escalationUsers("x".repeat(101),0,20));
        }
        verify(mapper).countEscalationUsers(10,"张");
        verify(mapper).escalationUsers(10,"张",0,20);
        verifyNoMoreInteractions(mapper);
    }
    @Test void escalationPolicyRejectsInvalidRecipientAndThreshold(){
        var mapper=mock(ConfigurationMapper.class);var definitions=mock(SystemConfigurationMapper.class);
        when(definitions.settingType("exit.escalationRecipientId")).thenReturn("INTEGER");
        when(definitions.settingType("exit.escalationAfterDays")).thenReturn("INTEGER");
        var service=new ConfigurationService(mapper,definitions,mock(OperationIdGenerator.class));
        try(var t=TenantContext.open(10,1)){
            for(String value:new String[]{"0","366","bad"})
                assertThrows(ApiException.class,()->service.updateSetting("exit.escalationAfterDays",new ConfigurationModels.UpdateSetting(value,0)));
            for(String value:new String[]{"-1","9223372036854775808","bad"})
                assertThrows(ApiException.class,()->service.updateSetting("exit.escalationRecipientId",new ConfigurationModels.UpdateSetting(value,0)));
            assertThrows(ApiException.class,()->service.updateSetting("exit.escalationRecipientId",new ConfigurationModels.UpdateSetting("9",0)));
        }
        verify(mapper).activeTenantUser(10,9);
        verify(mapper,never()).insertSetting(anyLong(),anyLong(),anyString(),anyString(),anyString());
    }
    @Test void rejectsInvalidAutoReminderPolicyBeforeWriting(){
        var mapper=mock(ConfigurationMapper.class);var definitions=mock(SystemConfigurationMapper.class);
        when(definitions.settingType("exit.autoReminderEnabled")).thenReturn("INTEGER");
        when(definitions.settingType("exit.autoReminderIntervalHours")).thenReturn("INTEGER");
        var service=new ConfigurationService(mapper,definitions,mock(OperationIdGenerator.class));
        try(var t=TenantContext.open(10,1)){
            for(String value:new String[]{"-1","2","true","01"})
                assertThrows(ApiException.class,()->service.updateSetting("exit.autoReminderEnabled",new ConfigurationModels.UpdateSetting(value,0)));
            for(String value:new String[]{"0","23","721","24.5","bad"})
                assertThrows(ApiException.class,()->service.updateSetting("exit.autoReminderIntervalHours",new ConfigurationModels.UpdateSetting(value,0)));
        }
        verifyNoInteractions(mapper);
    }
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

package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExitReminderFailureServiceTest {
    @Test void ordinaryUsersCannotForgeBackgroundFailureOrRecovery(){
        var mapper=mock(ExitReminderFailureMapper.class);
        var service=new ExitReminderFailureService(mapper,mock(SupplierService.class));
        try(var context=TenantContext.open(10,7)){
            assertThrows(IllegalArgumentException.class,()->service.record(30,90,100,"WORKER_FAILED"));
            assertThrows(IllegalArgumentException.class,()->service.delivered(30,90,100));
            assertThrows(ApiException.class,()->service.list(30,-1,20));
        }
        verifyNoInteractions(mapper);
    }
    @Test void reasonCodeCannotContainExceptionText(){
        var mapper=mock(ExitReminderFailureMapper.class);
        var service=new ExitReminderFailureService(mapper,mock(SupplierService.class));
        try(var context=TenantContext.openSystem(10)){
            assertThrows(IllegalArgumentException.class,()->service.record(30,90,100,"recipient alice@example.com"));
        }
        verifyNoInteractions(mapper);
    }
}

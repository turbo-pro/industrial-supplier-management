package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExitDeadlineReminderTest extends ExitApplicationServiceTest {
    @Test void automaticReminderUsesSystemActorAndCurrentFacts(){
        var service=pendingTask();
        when(mapper.entity(10,90,100)).thenReturn(owner(RestrictionBusinessDate.today().minusDays(1),null));
        when(assignees.active(8)).thenReturn(true);
        when(mapper.reminded(eq(10L),eq(90L),eq(100L),any(),eq(0))).thenReturn(1);
        when(mapper.advanceVersion(10,30,90,0)).thenReturn(1);
        try(var t=TenantContext.openSystem(10)){
            assertTrue(service.autoRemind(30,90,100,24));
        }
        verify(notifications).reminded(30,90,100,"OPEN_CONTRACT",77,8,RestrictionBusinessDate.today().minusDays(1));
        verify(mapper).event(anyLong(),eq(10L),eq(90L),eq("AUTO_ENTITY_REMIND"),anyString(),eq(0L));
    }
    @Test void automaticReminderSkipsDisabledFactsAndCannotBeCalledAsUser(){
        var service=pendingTask();
        when(mapper.entity(10,90,100)).thenReturn(owner(RestrictionBusinessDate.today(),null));
        try(var t=TenantContext.openSystem(10)){
            assertFalse(service.autoRemind(30,90,100,24));
        }
        when(mapper.entity(10,90,100)).thenReturn(owner(RestrictionBusinessDate.today().minusDays(1),null));
        when(assignees.active(8)).thenReturn(true);
        try(var t=TenantContext.openSystem(10)){
            assertFalse(service(List.of(contractFact(0,List.of()))).autoRemind(30,90,100,24));
        }
        try(var t=TenantContext.open(10,7)){
            assertThrows(IllegalArgumentException.class,()->service.autoRemind(30,90,100,24));
        }
        verifyNoInteractions(notifications);
    }
    ExitModels.EntityRow owner(LocalDate dueDate,LocalDateTime reminded){return new ExitModels.EntityRow(100,"OPEN_CONTRACT",77,"/projects/contracts","OPEN",8L,"说明",7L,LocalDateTime.now(),LocalDateTime.now(),null,0,dueDate,reminded);}
    ExitApplicationService pendingTask(){setup();return service(List.of(contractFact(1,List.of(new SupplierExitCheck.Entity("OPEN_CONTRACT",77,"/projects/contracts")))));}
    @Test void deadlineCanBeClearedWithAuditedReasonAndDoesNotComplete(){var service=pendingTask();when(mapper.entity(10,90,100)).thenReturn(owner(RestrictionBusinessDate.today(),null));when(mapper.deadline(10,90,100,null,0)).thenReturn(1);when(mapper.advanceVersion(10,30,90,0)).thenReturn(1);
        try(var t=TenantContext.open(10,7);var a=auth()){service.deadline(30,90,100,new ExitModels.Deadline(null,"取消期限",0,0));}
        verify(mapper).deadline(10,90,100,null,0);verifyNoInteractions(notifications);verify(suppliers,never()).completeExit(anyLong(),anyLong(),anyLong(),anyInt());
    }
    @Test void invalidDeadlineOrReasonDoesNotWrite(){var service=pendingTask();when(mapper.entity(10,90,100)).thenReturn(owner(null,null));
        try(var t=TenantContext.open(10,7);var a=auth()){
            for(var date:List.of(RestrictionBusinessDate.today().minusDays(1),RestrictionBusinessDate.today().plusYears(1).plusDays(1)))assertThrows(ApiException.class,()->service.deadline(30,90,100,new ExitModels.Deadline(date,"调整",0,0)));
            assertThrows(ApiException.class,()->service.deadline(30,90,100,new ExitModels.Deadline(null," ",0,0)));
        }verify(mapper,never()).deadline(anyLong(),anyLong(),anyLong(),any(),anyInt());
    }
    @Test void reminderCooldownRejectsRepeatedDelivery(){var service=pendingTask();when(mapper.entity(10,90,100)).thenReturn(owner(null,LocalDateTime.now(ZoneOffset.UTC).minusHours(1)));when(assignees.active(8)).thenReturn(true);
        try(var t=TenantContext.open(10,7);var a=auth()){assertThrows(ApiException.class,()->service.remind(30,90,100,new ExitModels.Reminder(0,0)));}
        verifyNoInteractions(notifications);verify(mapper,never()).reminded(anyLong(),anyLong(),anyLong(),any(),anyInt());
    }
    @Test void reminderAfterCooldownNotifiesOnlyCurrentActiveOwner(){var service=pendingTask();var due=RestrictionBusinessDate.today();when(mapper.entity(10,90,100)).thenReturn(owner(due,LocalDateTime.now(ZoneOffset.UTC).minusHours(25)));when(assignees.active(8)).thenReturn(true);when(mapper.reminded(eq(10L),eq(90L),eq(100L),any(),eq(0))).thenReturn(1);when(mapper.advanceVersion(10,30,90,0)).thenReturn(1);
        try(var t=TenantContext.open(10,7);var a=auth()){service.remind(30,90,100,new ExitModels.Reminder(0,0));}
        verify(notifications).reminded(30,90,100,"OPEN_CONTRACT",77,8,due);verify(mapper,never()).clearEntities(anyLong(),anyLong(),anySet());
    }
    @Test void inactiveOwnerMissingEntityAndStaleVersionsCannotRemind(){var service=pendingTask();when(mapper.entity(10,90,100)).thenReturn(owner(null,null));
        try(var t=TenantContext.open(10,7);var a=auth()){
            assertThrows(ApiException.class,()->service.remind(30,90,100,new ExitModels.Reminder(0,0)));
            assertThrows(ApiException.class,()->service.remind(30,90,999,new ExitModels.Reminder(0,0)));
            assertThrows(ApiException.class,()->service.remind(30,90,100,new ExitModels.Reminder(1,0)));
            assertThrows(ApiException.class,()->service.remind(30,90,100,new ExitModels.Reminder(0,1)));
        }verifyNoInteractions(notifications);verify(mapper,never()).reminded(anyLong(),anyLong(),anyLong(),any(),anyInt());
    }
    @Test void clearedOrBusinessClosedFactsCannotBeScheduledOrReminded(){var service=pendingTask();when(mapper.entity(10,90,100)).thenReturn(entity("CLEARED",0));
        try(var t=TenantContext.open(10,7);var a=auth()){
            assertThrows(ApiException.class,()->service.deadline(30,90,100,new ExitModels.Deadline(null,"变更",0,0)));
            assertThrows(ApiException.class,()->service.remind(30,90,100,new ExitModels.Reminder(0,0)));
            when(mapper.entity(10,90,100)).thenReturn(owner(null,null));
            assertThrows(ApiException.class,()->service(List.of(contractFact(0,List.of()))).remind(30,90,100,new ExitModels.Reminder(0,0)));
        }verifyNoInteractions(notifications);
    }
}

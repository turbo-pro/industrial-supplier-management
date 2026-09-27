package io.github.turbopro.ism.iam.access;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserReferenceServiceTest {
    @Test void assignmentAlwaysUsesCurrentTenantAndRejectsAbsentAccounts(){
        var mapper=mock(AccessMapper.class);var service=new UserReferenceService(mapper);
        when(mapper.activeUserForAssignment(10,7)).thenReturn(7L);
        try(var tenant=TenantContext.open(10,8)){
            assertTrue(service.activeForAssignment(7));assertFalse(service.activeForAssignment(9));assertFalse(service.activeForAssignment(0));
        }
        verify(mapper).activeUserForAssignment(10,7);verify(mapper).activeUserForAssignment(10,9);verifyNoMoreInteractions(mapper);
    }
}

package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import io.github.turbopro.ism.operation.message.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExitAssignmentNotificationTest {
    final MessageMapper mapper=mock(MessageMapper.class);
    final OperationIdGenerator ids=mock(OperationIdGenerator.class);
    final MessageService service=new MessageService(mapper,ids,new ObjectMapper());
    void template(String status,String content,String schema){when(mapper.exitAssignmentTemplate(10)).thenReturn(new MessageModels.TemplateRow(50,"SUPPLIER_EXIT_ASSIGNMENT","退出分派","IN_APP","退出事项待处置",content,schema,status,0));}
    void send(){service.notifyExitAssignment(30,90,100,"OPEN_CONTRACT",77,8);}
    @Test void sendsOnlyMinimalReferencesToSameTenantOwner(){
        template("ACTIVE","{{supplierId}}/{{applicationId}}/{{code}}/{{sourceId}}","[\"supplierId\",\"applicationId\",\"code\",\"sourceId\"]");
        when(mapper.validUsers(10,Set.of(8L))).thenReturn(Set.of(8L));when(ids.nextId()).thenReturn(101L,102L,103L);
        try(var ignored=TenantContext.open(10,7)){send();}
        verify(mapper).ensureExitAssignmentTemplate(10,101);
        verify(mapper).insertDelivery(eq(10L),eq(102L),eq(50L),eq(8L),eq("退出事项待处置"),eq("30/90/OPEN_CONTRACT/77"),eq("SUPPLIER_EXIT_ENTITY"),eq(100L),any());
        verify(mapper).insertInbox(10,103,102,8);
    }
    @Test void disabledTemplateIsNotReactivatedOrDelivered(){template("DISABLED","业务提示","[]");
        try(var ignored=TenantContext.open(10,7)){assertThrows(ApiException.class,this::send);}
        verify(mapper,never()).insertInbox(anyLong(),anyLong(),anyLong(),anyLong());
    }
    @Test void invalidOwnerCannotReceive(){template("ACTIVE","业务提示","[]");when(mapper.validUsers(10,Set.of(8L))).thenReturn(Set.of());
        try(var ignored=TenantContext.open(10,7)){assertThrows(ApiException.class,this::send);}
        verify(mapper,never()).insertInbox(anyLong(),anyLong(),anyLong(),anyLong());
    }
    @Test void unsupportedCustomVariableFailsBeforeDelivery(){template("ACTIVE","{{secret}}","[\"secret\"]");when(mapper.validUsers(10,Set.of(8L))).thenReturn(Set.of(8L));
        try(var ignored=TenantContext.open(10,7)){assertThrows(ApiException.class,this::send);}
        verify(mapper,never()).insertInbox(anyLong(),anyLong(),anyLong(),anyLong());
    }
}

package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlacklistServiceTest {
    private final BlacklistMapper mapper=mock(BlacklistMapper.class);
    private final SupplierService suppliers=mock(SupplierService.class);
    private final BlacklistService service=new BlacklistService(mapper,suppliers,mock(OperationIdGenerator.class),mock(AuditService.class));
    @Test void cannotOpenSecondActiveCase(){
        when(suppliers.get(30)).thenReturn(supplier());when(mapper.openCase(10,30)).thenReturn(1);
        try(var tenant=TenantContext.open(10,7);var auth=auth()){
            assertThrows(ApiException.class,()->service.create(new BlacklistModels.Create("30","重大违约",null)));
        }
        verify(mapper,never()).insert(anyLong(),anyLong(),anyLong(),anyLong(),anyString(),anyString(),anyString(),any(),anyLong());
    }
    @Test void creatorCannotApproveOwnCase(){
        when(mapper.get(10,90)).thenReturn(row("SUBMITTED",7));
        try(var tenant=TenantContext.open(10,7);var auth=auth()){
            assertThrows(ApiException.class,()->service.review(90,new BlacklistModels.Review(BlacklistModels.Decision.APPROVE,"同意",0)));
        }
        verify(mapper,never()).review(anyLong(),anyLong(),anyString(),anyString(),anyLong(),anyInt());
    }
    @Test void cannotRevokeRejectedCase(){
        when(mapper.get(10,90)).thenReturn(row("REJECTED",7));
        try(var tenant=TenantContext.open(10,8);var auth=auth()){
            assertThrows(ApiException.class,()->service.revoke(90,new BlacklistModels.Revoke("解除",0)));
        }
    }
    private AuthorizationContext.Scope auth(){return AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()));}
    private SupplierModels.SupplierView supplier(){return new SupplierModels.SupplierView("30","20","S-1","供应商",null,null,SupplierModels.Type.MANUFACTURER,null,"CN",null,null,null,null,null,null,null,null,"MANUAL",SupplierModels.Status.ACTIVE,SupplierModels.RiskLevel.LOW,null,0,null,null,List.of());}
    private BlacklistModels.Row row(String status,long creator){return new BlacklistModels.Row(90,30,20,"S-1","供应商","重大违约",null,status,null,null,null,null,null,null,creator,0,LocalDateTime.now(),LocalDateTime.now());}
}

package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SupplierServiceTest {
    private final SupplierMapper mapper=mock(SupplierMapper.class);
    private final BlacklistMapper blacklist=mock(BlacklistMapper.class);
    private final SupplierService service=new SupplierService(mapper,mock(OperationIdGenerator.class),mock(AuditService.class),blacklist);
    @Test void deniesSupplierOutsideOrganizationScope(){when(mapper.find(10,99)).thenReturn(row("ACTIVE",20,7));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.organizations(Set.of(21L))),Set.of()))){assertThrows(ApiException.class,()->service.get(99));}}
    @Test void exitedSupplierCannotBeReactivated(){when(mapper.find(10,99)).thenReturn(row("EXITED",20,7));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){assertThrows(ApiException.class,()->service.changeStatus(99,new SupplierModels.ChangeStatus(SupplierModels.Status.ACTIVE,"再次启用",0)));}verify(mapper,never()).changeStatus(anyLong(),anyLong(),anyLong(),anyString(),any(),anyInt());}
    @Test void blacklistedSupplierCannotBeReactivated(){when(mapper.find(10,99)).thenReturn(row("SUSPENDED",20,7));when(blacklist.active(eq(10L),eq(99L),any())).thenReturn(1);try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){assertThrows(ApiException.class,()->service.changeStatus(99,new SupplierModels.ChangeStatus(SupplierModels.Status.ACTIVE,"再次启用",0)));}verify(mapper,never()).changeStatus(anyLong(),anyLong(),anyLong(),anyString(),any(),anyInt());}
    private SupplierModels.SupplierRow row(String status,long organization,long creator){return new SupplierModels.SupplierRow(99,organization,"SUP-001","测试供应商",null,null,"MANUFACTURER",null,"CN",null,null,null,null,null,null,null,null,"MANUAL",status,"LOW",null,creator,0,LocalDateTime.now(),LocalDateTime.now());}
}

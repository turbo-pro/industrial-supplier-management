package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExitTaskServiceTest {
    final ExitTaskMapper mapper=mock(ExitTaskMapper.class);
    final ExitTaskService service=new ExitTaskService(mapper);
    AuthorizationContext.Scope auth(DataScope scope){return AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",scope),Set.of()));}
    @Test void usesSessionIdentityScopesAndEscapedKeywordBeforePagination(){
        when(mapper.count(10,7,"ORGANIZATION_SET",Set.of(20L),"%a=%=_==%","OPEN_CONTRACT")).thenReturn(21L);
        when(mapper.list(10,7,"ORGANIZATION_SET",Set.of(20L),"%a=%=_==%","OPEN_CONTRACT",20,20)).thenReturn(List.of(new ExitTaskModels.Row(100,90,30,"S30","测试供应商",20,"OPEN_CONTRACT",77,"/projects/contracts","处理说明",LocalDateTime.now(),LocalDateTime.now(),2,3,null,null)));
        try(var identity=TenantContext.open(10,7);var scope=auth(DataScope.organizations(Set.of(20L)))){
            var page=service.mine(" a%_= ","OPEN_CONTRACT",1,20);
            assertEquals(21,page.total());assertEquals("100",page.items().get(0).id());assertEquals("90",page.items().get(0).applicationId());assertEquals(3,page.items().get(0).applicationVersion());
        }
    }
    @Test void rejectsInvalidPagingTypesAndOversizedSearchBeforeDatabase(){
        for(int[] args:List.of(new int[]{-1,20},new int[]{10001,20},new int[]{0,0},new int[]{0,101}))assertThrows(ApiException.class,()->service.mine(null,null,args[0],args[1]));
        assertThrows(ApiException.class,()->service.mine(null,"UNKNOWN",0,20));assertThrows(ApiException.class,()->service.mine("x".repeat(101),null,0,20));verifyNoInteractions(mapper);
    }
    @Test void absentScopeRemainsNoneAndNoOtherAssigneeParameterExists(){
        try(var identity=TenantContext.open(10,7);var scope=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of(),Set.of()))){assertTrue(service.mine(" "," ",0,20).items().isEmpty());}
        verify(mapper).count(10,7,"NONE",Set.of(),null,null);verify(mapper).list(10,7,"NONE",Set.of(),null,null,0,20);
    }
}

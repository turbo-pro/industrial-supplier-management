package io.github.turbopro.ism.integration.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchServiceTest {
    private final SearchMapper mapper=mock(SearchMapper.class);
    private final SearchService service=new SearchService(mapper,mock(OperationIdGenerator.class),new ObjectMapper().findAndRegisterModules());

    @Test void searchesOnlyEntityTypesAllowedByUnderlyingPermissions(){
        when(mapper.users(eq(8L),anyString(),anySet(),isNull(),isNull(),eq(21),eq("ORGANIZATION_SET"),eq(Set.of(18L)),eq(9L))).thenReturn(List.of(new SearchModels.SearchRow(1,"USER","张三","zhangsan","ACTIVE","/system/users/1",LocalDateTime.of(2026,1,1,0,0),18L,null)));
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("iam:user:view"),Map.of("iam:user",DataScope.organizations(Set.of(18L))),Set.of()))){
            var result=service.search(new SearchModels.SearchRequest("张",Set.of(SearchModels.EntityType.USER,SearchModels.EntityType.FILE),Set.of(),null,null,0,20));
            assertThat(result.searchedTypes()).containsExactly(SearchModels.EntityType.USER);assertThat(result.items()).extracting(SearchModels.SearchItem::title).containsExactly("张三");
            verify(mapper).users(eq(8L),anyString(),anySet(),isNull(),isNull(),eq(21),eq("ORGANIZATION_SET"),eq(Set.of(18L)),eq(9L));verify(mapper,never()).files(anyLong(),anyString(),anySet(),any(),any(),anyInt(),anyString(),anySet(),anyLong());verifyNoMoreInteractions(mapper);
        }
    }
    @Test void rejectsConditionlessAndReversedDateQueries(){
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(PermissionSnapshot.denyAll())){
            assertThatThrownBy(()->service.search(new SearchModels.SearchRequest("",Set.of(),Set.of(),null,null,0,20))).isInstanceOf(ApiException.class);
            assertThatThrownBy(()->service.search(new SearchModels.SearchRequest("x",Set.of(),Set.of(),LocalDateTime.of(2026,2,1,0,0),LocalDateTime.of(2026,1,1,0,0),0,20))).isInstanceOf(ApiException.class);
        }
    }
}

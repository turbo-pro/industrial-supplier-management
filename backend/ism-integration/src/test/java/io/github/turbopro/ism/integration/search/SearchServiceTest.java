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

    @Test void qualificationSearchRequiresItsOwnPermissionAndNonemptyScope(){
        var query=new SearchModels.SearchRequest("证号",Set.of(SearchModels.EntityType.SUPPLIER_QUALIFICATION),Set.of("EXPIRED"),null,null,0,20);
        when(mapper.supplierQualifications(eq(8L),eq("%证号%"),eq(Set.of("EXPIRED")),isNull(),isNull(),eq(21),eq("ORGANIZATION_SET"),eq(Set.of(18L)),eq(9L)))
            .thenReturn(List.of(new SearchModels.SearchRow(55,"SUPPLIER_QUALIFICATION","证号-55","供应商甲 · 安全许可证","EXPIRED","/suppliers/qualifications",LocalDateTime.of(2026,1,1,0,0),18L,9L)));
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:qualification:view"),Map.of("supplier:qualification",DataScope.organizations(Set.of(18L))),Set.of()))){
            assertThat(service.search(query).items()).extracting(SearchModels.SearchItem::id).containsExactly("55");
        }
        clearInvocations(mapper);
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:qualification:view"),Map.of("supplier:qualification",DataScope.organizations(Set.of())),Set.of()))){
            assertThat(service.search(query).items()).isEmpty();verifyNoInteractions(mapper);
        }
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:qualification",DataScope.all()),Set.of()))){
            assertThat(service.search(query).items()).isEmpty();verifyNoInteractions(mapper);
        }
    }

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
    @Test void searchesBusinessTypesUsingTheirOwnPermissionAndDataScope(){
        when(mapper.suppliers(eq(8L),anyString(),anySet(),isNull(),isNull(),eq(21),eq("CREATED"),eq(Set.of()),eq(9L))).thenReturn(List.of(new SearchModels.SearchRow(11,"SUPPLIER","供应商甲","SUP-11","ACTIVE","/suppliers/master",LocalDateTime.of(2026,1,1,0,0),18L,9L)));
        when(mapper.projects(eq(8L),anyString(),anySet(),isNull(),isNull(),eq(21),eq("PROJECT_SET"),eq(Set.of()),eq(Set.of(31L)),eq(9L))).thenReturn(List.of(new SearchModels.SearchRow(31,"PROJECT","项目甲","PRJ-31","ACTIVE","/projects/ledger",LocalDateTime.of(2026,1,2,0,0),18L,9L)));
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view","project:view"),Map.of("supplier:master",DataScope.created(),"project",DataScope.projects(Set.of(31L)),"contract",DataScope.all()),Set.of()))){
            var result=service.search(new SearchModels.SearchRequest("甲",Set.of(SearchModels.EntityType.SUPPLIER,SearchModels.EntityType.CONTRACT,SearchModels.EntityType.PROJECT),Set.of(),null,null,0,20));
            assertThat(result.searchedTypes()).containsExactlyInAnyOrder(SearchModels.EntityType.SUPPLIER,SearchModels.EntityType.PROJECT);
            assertThat(result.items()).extracting(SearchModels.SearchItem::id).containsExactly("31","11");
            verify(mapper,never()).contracts(anyLong(),anyString(),anySet(),any(),any(),anyInt(),anyString(),anySet(),anyLong());
        }
    }
    @Test void emptyBusinessScopeDoesNotIssueSearchSql(){
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view","contract:view","project:view"),Map.of("supplier:master",DataScope.none(),"contract",DataScope.organizations(Set.of()),"project",DataScope.projects(Set.of())),Set.of()))){
            var result=service.search(new SearchModels.SearchRequest("甲",Set.of(SearchModels.EntityType.SUPPLIER,SearchModels.EntityType.CONTRACT,SearchModels.EntityType.PROJECT),Set.of(),null,null,0,20));
            assertThat(result.searchedTypes()).isEmpty();assertThat(result.items()).isEmpty();verifyNoInteractions(mapper);
        }
    }
    @Test void resourceSearchNeverUsesOtherObjectPermissionOrEmptyProjectScope(){
        when(mapper.persons(eq(8L),anyString(),anySet(),isNull(),isNull(),eq(21),eq("PROJECT_SET"),eq(Set.of()),eq(Set.of(31L)),eq(9L)))
            .thenReturn(List.of(new SearchModels.SearchRow(51,"PERSON","张三","PER-51 · 供应商甲","ACTIVE","/resources/persons",LocalDateTime.of(2026,1,1,0,0),18L,null)));
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("resource:person:view"),Map.of("resource:person",DataScope.projects(Set.of(31L)),"resource:asset",DataScope.all()),Set.of()))){
            var result=service.search(new SearchModels.SearchRequest("张",Set.of(SearchModels.EntityType.PERSON,SearchModels.EntityType.ASSET),Set.of(),null,null,0,20));
            assertThat(result.searchedTypes()).containsExactly(SearchModels.EntityType.PERSON);
            assertThat(result.items()).extracting(SearchModels.SearchItem::title).containsExactly("张三");
            verify(mapper,never()).assets(anyLong(),anyString(),anySet(),any(),any(),anyInt(),anyString(),anySet(),anySet(),anyLong());
        }
        clearInvocations(mapper);
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("resource:person:view","resource:asset:view"),Map.of("resource:person",DataScope.projects(Set.of()),"resource:asset",DataScope.organizations(Set.of())),Set.of()))){
            assertThat(service.search(new SearchModels.SearchRequest("张",Set.of(SearchModels.EntityType.PERSON,SearchModels.EntityType.ASSET),Set.of(),null,null,0,20)).items()).isEmpty();
            verifyNoInteractions(mapper);
        }
    }
    @Test void issueSearchUsesSeparatePermissionsAndScopes(){
        when(mapper.safetyIssues(eq(8L),anyString(),anySet(),isNull(),isNull(),eq(21),eq("PROJECT_SET"),eq(Set.of()),eq(Set.of(31L)),eq(9L)))
            .thenReturn(List.of(new SearchModels.SearchRow(71,"SAFETY_ISSUE","隐患甲","SAFE-71 · 供应商甲","OPEN","/safety/issues",LocalDateTime.of(2026,1,1,0,0),18L,9L)));
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("safety:issue:view"),Map.of("safety:issue",DataScope.projects(Set.of(31L)),"quality:ncr",DataScope.all()),Set.of()))){
            var result=service.search(new SearchModels.SearchRequest("甲",Set.of(SearchModels.EntityType.SAFETY_ISSUE,SearchModels.EntityType.QUALITY_NCR),Set.of(),null,null,0,20));
            assertThat(result.searchedTypes()).containsExactly(SearchModels.EntityType.SAFETY_ISSUE);
            assertThat(result.items()).extracting(SearchModels.SearchItem::id).containsExactly("71");
            verify(mapper,never()).qualityNcrs(anyLong(),anyString(),anySet(),any(),any(),anyInt(),anyString(),anySet(),anySet(),anyLong());
        }
        clearInvocations(mapper);
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("safety:issue:view","quality:ncr:view"),Map.of("safety:issue",DataScope.projects(Set.of()),"quality:ncr",DataScope.organizations(Set.of())),Set.of()))){
            assertThat(service.search(new SearchModels.SearchRequest("甲",Set.of(SearchModels.EntityType.SAFETY_ISSUE,SearchModels.EntityType.QUALITY_NCR),Set.of(),null,null,0,20)).items()).isEmpty();
            verifyNoInteractions(mapper);
        }
    }
    @Test void performanceSearchRequiresEvaluationPermissionAndItsOwnScope(){
        when(mapper.performanceEvaluations(eq(8L),anyString(),anySet(),isNull(),isNull(),eq(21),eq("OWNED"),eq(Set.of()),eq(9L)))
            .thenReturn(List.of(new SearchModels.SearchRow(91,"PERFORMANCE_EVALUATION","供应商甲","SUP-91 · 2026-01-01 ~ 2026-01-31","DRAFT","/performance/evaluations",LocalDateTime.of(2026,1,31,0,0),18L,9L)));
        var query=new SearchModels.SearchRequest("供应商",Set.of(SearchModels.EntityType.PERFORMANCE_EVALUATION),Set.of(),null,null,0,20);
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("performance:evaluation:view"),Map.of("performance:evaluation",DataScope.owned()),Set.of()))){
            assertThat(service.search(query).items()).extracting(SearchModels.SearchItem::id).containsExactly("91");
        }
        clearInvocations(mapper);
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("performance:evaluation:view"),Map.of("performance:evaluation",DataScope.organizations(Set.of())),Set.of()))){
            assertThat(service.search(query).items()).isEmpty();verifyNoInteractions(mapper);
        }
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("performance:evaluation",DataScope.all()),Set.of()))){
            assertThat(service.search(query).items()).isEmpty();verifyNoInteractions(mapper);
        }
    }
    @Test void attendanceSearchRejectsEmptyProjectScopeAndMissingPermission(){
        var query=new SearchModels.SearchRequest("厂区",Set.of(SearchModels.EntityType.SITE_ATTENDANCE),Set.of("OPEN"),null,null,0,20);
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("safety:attendance:view"),Map.of("safety:attendance",DataScope.projects(Set.of())),Set.of()))){
            assertThat(service.search(query).items()).isEmpty();verifyNoInteractions(mapper);
        }
        try(var tenant=TenantContext.open(8,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("safety:attendance",DataScope.all()),Set.of()))){
            assertThat(service.search(query).items()).isEmpty();verifyNoInteractions(mapper);
        }
    }
}

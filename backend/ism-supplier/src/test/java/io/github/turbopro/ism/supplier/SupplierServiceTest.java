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
    private final SupplierExitCheck exitCheck=mock(SupplierExitCheck.class);
    private final AuditService audit=mock(AuditService.class);
    private final SupplierService service=new SupplierService(mapper,mock(OperationIdGenerator.class),audit,blacklist,List.of(exitCheck));
    @Test void listPassesTypeRiskCategoryAndScopeToCountAndItems(){try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.organizations(Set.of(20L))),Set.of()))){service.list("SUP", "ACTIVE",null,SupplierModels.Type.CONTRACTOR,SupplierModels.RiskLevel.HIGH,50L,1,20);}verify(mapper).count(10,"%SUP%","ACTIVE",null,"CONTRACTOR","HIGH",50L,"ORGANIZATION_SET",Set.of(20L),7);verify(mapper).list(10,"%SUP%","ACTIVE",null,"CONTRACTOR","HIGH",50L,"ORGANIZATION_SET",Set.of(20L),7,20,20);}
    @Test void supplierEditReplacesContactsAndAuditsOnlyAfterVersionMatch(){
        var command=new SupplierModels.SaveSupplier("SUP-001","新名称",null,null,SupplierModels.Type.MANUFACTURER,null,"CN",null,null,null,null,null,null,null,null,SupplierModels.RiskLevel.LOW,null,"20",List.of(new SupplierModels.ContactCommand("张三",null,"13800138000",null,null,true,0)),0);
        when(mapper.find(10,99)).thenReturn(row("ACTIVE",20,7));when(mapper.organizationExists(10,20)).thenReturn(1);
        when(mapper.update(eq(10L),eq(7L),eq(99L),eq(20L),eq("新名称"),any(),any(),eq("MANUFACTURER"),any(),eq("CN"),any(),any(),any(),any(),any(),any(),any(),any(),eq("LOW"),any(),eq(0))).thenReturn(1);
        try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){service.update(99,command);}
        verify(mapper).deleteContacts(10,99);verify(mapper).insertContact(anyLong(),eq(10L),eq(99L),eq("张三"),isNull(),eq("13800138000"),isNull(),isNull(),eq(true),eq(0));verify(audit).append(any(AuditService.AuditCommand.class));
        reset(mapper,audit);when(mapper.find(10,99)).thenReturn(row("ACTIVE",20,7));when(mapper.organizationExists(10,20)).thenReturn(1);
        try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){assertThrows(ApiException.class,()->service.update(99,command));}
        verify(mapper,never()).deleteContacts(anyLong(),anyLong());verifyNoInteractions(audit);
    }
    @Test void unfinishedContractBlocksExit(){when(mapper.find(10,99)).thenReturn(row("ACTIVE",20,7));when(exitCheck.blockers(99)).thenReturn(List.of(new SupplierExitCheck.Blocker("OPEN_CONTRACT","未结束合同",1,"/projects/contracts")));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){assertThrows(ApiException.class,()->service.changeStatus(99,new SupplierModels.ChangeStatus(SupplierModels.Status.EXITED,"结束合作",0)));}verify(mapper,never()).changeStatus(anyLong(),anyLong(),anyLong(),anyString(),any(),anyInt());}
    @Test void directExitCannotBypassApplicationApproval(){when(mapper.find(10,99)).thenReturn(row("ACTIVE",20,7));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){assertThrows(ApiException.class,()->service.changeStatus(99,new SupplierModels.ChangeStatus(SupplierModels.Status.EXITED,"结束合作",0)));}verify(mapper,never()).changeStatus(anyLong(),anyLong(),anyLong(),anyString(),any(),anyInt());}
    @Test void deniesSupplierOutsideOrganizationScope(){when(mapper.find(10,99)).thenReturn(row("ACTIVE",20,7));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.organizations(Set.of(21L))),Set.of()))){assertThrows(ApiException.class,()->service.get(99));}}
    @Test void exitedSupplierCannotBeReactivated(){when(mapper.find(10,99)).thenReturn(row("EXITED",20,7));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){assertThrows(ApiException.class,()->service.changeStatus(99,new SupplierModels.ChangeStatus(SupplierModels.Status.ACTIVE,"再次启用",0)));}verify(mapper,never()).changeStatus(anyLong(),anyLong(),anyLong(),anyString(),any(),anyInt());}
    @Test void blacklistedSupplierCannotBeReactivated(){when(mapper.find(10,99)).thenReturn(row("SUSPENDED",20,7));when(blacklist.currentActive(eq(10L),eq(99L),any())).thenReturn(List.of(101L));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){assertThrows(ApiException.class,()->service.changeStatus(99,new SupplierModels.ChangeStatus(SupplierModels.Status.ACTIVE,"再次启用",0)));}verify(mapper,never()).changeStatus(anyLong(),anyLong(),anyLong(),anyString(),any(),anyInt());}
    @Test void pendingExitPreventsReactivation(){
        when(mapper.find(10,99)).thenReturn(row("SUSPENDED",20,7));when(mapper.currentPendingExits(10,99)).thenReturn(List.of(101L));
        try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){
            assertThrows(ApiException.class,()->service.changeStatus(99,new SupplierModels.ChangeStatus(SupplierModels.Status.ACTIVE,"启用",0)));
        }verify(mapper,never()).changeStatus(anyLong(),anyLong(),anyLong(),anyString(),any(),anyInt());
    }
    private SupplierModels.SupplierRow row(String status,long organization,long creator){return new SupplierModels.SupplierRow(99,organization,"SUP-001","测试供应商",null,null,"MANUFACTURER",null,"CN",null,null,null,null,null,null,null,null,"MANUAL",status,"LOW",null,creator,0,LocalDateTime.now(),LocalDateTime.now());}
}

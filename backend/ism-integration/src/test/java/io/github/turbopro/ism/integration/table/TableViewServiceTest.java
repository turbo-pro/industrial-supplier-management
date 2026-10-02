package io.github.turbopro.ism.integration.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TableViewServiceTest {
    final TableViewMapper mapper=mock(TableViewMapper.class);final OperationIdGenerator ids=mock(OperationIdGenerator.class);
    final AuditService audit=mock(AuditService.class);final TableViewService service=new TableViewService(mapper,ids,new ObjectMapper(),audit);
    AuthorizationContext.Scope auth(){return AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view"),Map.of(),Set.of()));}
    List<TableViewModels.Column> columns(){return List.of("code","name","type","riskLevel","status","updatedAt").stream().map(k->new TableViewModels.Column(k,true,160)).toList();}
    TableViewModels.Save save(int version){return new TableViewModels.Save("常用",columns(),true,version);}
    void locked(){when(mapper.lockOwner(10,7,"supplier.master")).thenReturn(7L);}
    TableViewModels.Row row(int version){return new TableViewModels.Row(90,"常用","[]",true,version,LocalDateTime.now());}
    @Test void validatesColumnsWithoutWriting(){try(var t=TenantContext.open(10,7);var a=auth()){
        var hidden=new ArrayList<>(columns());hidden.set(0,new TableViewModels.Column("code",false,160));
        var duplicate=new ArrayList<>(columns());duplicate.set(5,columns().get(0));
        var unknown=new ArrayList<>(columns());unknown.set(5,new TableViewModels.Column("password",true,160));
        var wide=new ArrayList<>(columns());wide.set(5,new TableViewModels.Column("updatedAt",true,601));
        for(var c:List.of(hidden,duplicate,unknown,wide))assertThrows(ApiException.class,()->service.create("supplier.master",new TableViewModels.Save("列方案",c,false,0)));
        assertThrows(ApiException.class,()->service.create("supplier.master",save(1)));
    }verifyNoInteractions(mapper,audit);}
    @Test void unregisteredTablesAndMissingBusinessPermissionAreRejected(){try(var t=TenantContext.open(10,7);var a=auth()){
        assertThrows(ApiException.class,()->service.list("arbitrary.sql"));
    }try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(PermissionSnapshot.denyAll())){
        assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list("supplier.master")).errorCode());
    }verifyNoInteractions(mapper);}
    @Test void privateListUsesOnlyCurrentIdentity(){try(var t=TenantContext.open(10,7);var a=auth()){
        assertEquals(6,service.list("supplier.master").catalog().size());
        assertFalse(service.list("supplier.master").canPublish());
    }verify(mapper,times(2)).list(10,7,"supplier.master");}
    @Test void sharedViewsRequireSeparatePublishPermissionAndRegisteredBusinessTable(){
        try(var t=TenantContext.open(10,7);var a=auth()){
            assertThrows(ApiException.class,()->service.createShared("supplier.master",save(0)));
            assertThrows(ApiException.class,()->service.updateShared("supplier.master",90,save(0)));
            assertThrows(ApiException.class,()->service.deleteShared("supplier.master",90,0));
        }
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("table:view:manage","table:view:publish"),Map.of(),Set.of()))){
            assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.createShared("supplier.master",save(0))).errorCode());
        }
        verify(mapper,never()).ensureOwner(anyLong(),eq(0L),anyString());
    }
    @Test void catalogRequiresEachTablesOwnBusinessPermission(){
        try(var t=TenantContext.open(10,7);var a=auth()){
            for(var key:List.of("contract.ledger","project.ledger","resource.person","resource.asset"))assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list(key)).errorCode());
        }
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("contract:view"),Map.of(),Set.of()))){
            assertEquals(List.of("contractNo","name","amount","period","status"),service.list("contract.ledger").catalog().stream().map(TableViewModels.Definition::key).toList());
            assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list("project.ledger")).errorCode());
        }
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("project:view"),Map.of(),Set.of()))){
            assertEquals(List.of("projectCode","name","contractNo","period","status"),service.list("project.ledger").catalog().stream().map(TableViewModels.Definition::key).toList());
        }
        verify(mapper).list(10,7,"contract.ledger");verify(mapper).list(10,7,"project.ledger");
    }
    @Test void admissionAndQualificationCatalogsHaveSeparateRightsAndRequiredIdentity(){
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:admission:view"),Map.of(),Set.of()))){
            var catalog=service.list("supplier.admission").catalog();
            assertEquals(List.of("applicationNo","supplierName","purchaseCategory","status","submittedAt"),catalog.stream().map(TableViewModels.Definition::key).toList());
            assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list("supplier.qualification")).errorCode());
            var hidden=catalog.stream().map(c->new TableViewModels.Column(c.key(),!c.required(),c.width())).toList();
            assertEquals(CommonErrorCode.VALIDATION_FAILED,assertThrows(ApiException.class,()->service.create("supplier.admission",new TableViewModels.Save("隐藏申请单号",hidden,false,0))).errorCode());
        }
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:qualification:view"),Map.of(),Set.of()))){
            var catalog=service.list("supplier.qualification").catalog();
            assertEquals(List.of("supplierName","typeName","certificateNo","expiryDate","status"),catalog.stream().map(TableViewModels.Definition::key).toList());
            assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list("supplier.admission")).errorCode());
            var hidden=catalog.stream().map(c->new TableViewModels.Column(c.key(),!c.required(),c.width())).toList();
            assertEquals(CommonErrorCode.VALIDATION_FAILED,assertThrows(ApiException.class,()->service.create("supplier.qualification",new TableViewModels.Save("隐藏证号",hidden,false,0))).errorCode());
        }
        verify(mapper,never()).ensureOwner(anyLong(),anyLong(),anyString());
    }
    @Test void newTablesRejectHiddenIdentityAndForeignColumnsBeforeWrites(){
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("contract:view","project:view","resource:person:view","resource:asset:view"),Map.of(),Set.of()))){
            for(var key:List.of("contract.ledger","project.ledger","resource.person","resource.asset")){
                var definitions=service.list(key).catalog();var hidden=definitions.stream().map(c->new TableViewModels.Column(c.key(),!c.required(),160)).toList();
                assertThrows(ApiException.class,()->service.create(key,new TableViewModels.Save("隐藏标识",hidden,false,0)));
                var wrong=new ArrayList<>(definitions.stream().map(c->new TableViewModels.Column(c.key(),true,160)).toList());
                wrong.set(wrong.size()-1,new TableViewModels.Column("password",true,160));
                assertThrows(ApiException.class,()->service.create(key,new TableViewModels.Save("未知列",wrong,false,0)));
            }
        }verify(mapper,never()).ensureOwner(anyLong(),anyLong(),anyString());
    }
    @Test void resourceCatalogsHaveSeparatePermissionsAndNeverExposeRawIdentity(){
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("resource:person:view"),Map.of(),Set.of()))){
            assertEquals(List.of("code","name","idNumberMasked","tradeType","projectCode","status"),service.list("resource.person").catalog().stream().map(TableViewModels.Definition::key).toList());
            assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list("resource.asset")).errorCode());
        }
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("resource:asset:view"),Map.of(),Set.of()))){
            assertEquals(List.of("code","name","type","plateNo","projectCode","status"),service.list("resource.asset").catalog().stream().map(TableViewModels.Definition::key).toList());
            assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list("resource.person")).errorCode());
        }
        verify(mapper).list(10,7,"resource.person");verify(mapper).list(10,7,"resource.asset");
    }
    @Test void qualityAndPerformanceCatalogsAreIsolatedAndEnforceRequiredColumns(){
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("quality:ncr:view"),Map.of(),Set.of()))){
            var quality=service.list("quality.ncr").catalog();
            assertEquals(List.of("ncrNo","title","severity","quantity","deadline","status"),quality.stream().map(TableViewModels.Definition::key).toList());
            assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list("performance.evaluation")).errorCode());
            var hidden=quality.stream().map(c->new TableViewModels.Column(c.key(),!c.required(),c.width())).toList();
            assertEquals(CommonErrorCode.VALIDATION_FAILED,assertThrows(ApiException.class,()->service.create("quality.ncr",new TableViewModels.Save("隐藏编号",hidden,false,0))).errorCode());
        }
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("performance:evaluation:view","table:view:manage","table:view:publish"),Map.of(),Set.of()))){
            var performance=service.list("performance.evaluation").catalog();
            assertEquals(List.of("supplierName","period","score","facts","status"),performance.stream().map(TableViewModels.Definition::key).toList());
            assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list("quality.ncr")).errorCode());
            var hidden=performance.stream().map(c->new TableViewModels.Column(c.key(),!c.required(),c.width())).toList();
            assertEquals(CommonErrorCode.VALIDATION_FAILED,assertThrows(ApiException.class,()->service.createShared("performance.evaluation",new TableViewModels.Save("隐藏供应商",hidden,false,0))).errorCode());
        }
        verify(mapper,never()).ensureOwner(anyLong(),anyLong(),anyString());
    }
    @Test void safetyIssueAndAttendanceCatalogsHaveSeparateReadRightsAndFixedIdentityColumns(){
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("safety:issue:view"),Map.of(),Set.of()))){
            var issue=service.list("safety.issue").catalog();
            assertEquals(List.of("issueNo","title","severity","deadline","status"),issue.stream().map(TableViewModels.Definition::key).toList());
            assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list("safety.attendance")).errorCode());
            var hidden=issue.stream().map(c->new TableViewModels.Column(c.key(),!c.required(),c.width())).toList();
            assertEquals(CommonErrorCode.VALIDATION_FAILED,assertThrows(ApiException.class,()->service.create("safety.issue",new TableViewModels.Save("隐藏编号",hidden,false,0))).errorCode());
        }
        try(var t=TenantContext.open(10,7);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of("safety:attendance:view"),Map.of(),Set.of()))){
            var attendance=service.list("safety.attendance").catalog();
            assertEquals(List.of("personName","supplierName","projectName","siteName","checkInAt","checkOutAt","status"),attendance.stream().map(TableViewModels.Definition::key).toList());
            assertEquals(CommonErrorCode.FORBIDDEN,assertThrows(ApiException.class,()->service.list("safety.issue")).errorCode());
            var hidden=attendance.stream().map(c->new TableViewModels.Column(c.key(),!c.required(),c.width())).toList();
            assertEquals(CommonErrorCode.VALIDATION_FAILED,assertThrows(ApiException.class,()->service.create("safety.attendance",new TableViewModels.Save("隐藏人员",hidden,false,0))).errorCode());
        }
        verify(mapper,never()).ensureOwner(anyLong(),anyLong(),anyString());
    }
    @Test void staleVersionCannotChangeOtherDefault(){locked();when(mapper.find(10,7,"supplier.master",90)).thenReturn(row(2));
        try(var t=TenantContext.open(10,7);var a=auth()){
            assertEquals(CommonErrorCode.CONFLICT,assertThrows(ApiException.class,()->service.update("supplier.master",90,save(1))).errorCode());
        }verify(mapper,never()).clearDefault(anyLong(),anyLong(),anyString(),anyLong());verifyNoInteractions(audit);
    }
    @Test void foreignViewIsNotFoundBeforeMutation(){locked();try(var t=TenantContext.open(10,7);var a=auth()){
        assertEquals(CommonErrorCode.NOT_FOUND,assertThrows(ApiException.class,()->service.delete("supplier.master",90,0)).errorCode());
    }verify(mapper,never()).delete(anyLong(),anyLong(),anyString(),anyLong(),anyInt());}
    @Test void quotaIsCheckedUnderOwnerLock(){locked();when(mapper.count(10,7,"supplier.master")).thenReturn(20);
        try(var t=TenantContext.open(10,7);var a=auth()){assertThrows(ApiException.class,()->service.create("supplier.master",save(0)));}
        verify(mapper,never()).insert(anyLong(),anyLong(),anyLong(),anyString(),anyString(),anyString(),anyBoolean());
    }
    @Test void duplicateNameIsConflict(){locked();when(ids.nextId()).thenReturn(90L);
        when(mapper.insert(eq(90L),eq(10L),eq(7L),eq("supplier.master"),eq("常用"),anyString(),eq(true))).thenThrow(new DuplicateKeyException("name"));
        try(var t=TenantContext.open(10,7);var a=auth()){assertEquals(CommonErrorCode.CONFLICT,assertThrows(ApiException.class,()->service.create("supplier.master",save(0))).errorCode());}
    }
}

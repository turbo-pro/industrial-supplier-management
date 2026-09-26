package io.github.turbopro.ism.resource.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierResourceServiceTest {
    private final SupplierResourceMapper mapper=mock(SupplierResourceMapper.class);
    private final OperationIdGenerator ids=mock(OperationIdGenerator.class);
    private final SupplierResourceService service=new SupplierResourceService(mapper,ids,mock(AuditService.class));

    @Test void rawIdentityNumberIsNeverPersisted(){
        when(ids.nextId()).thenReturn(99L);when(mapper.supplierOrg(10,30)).thenReturn(20L);
        when(mapper.person(10,99)).thenReturn(person("PENDING"));
        try(var tenant=TenantContext.open(10,7);var auth=auth()){
            service.createPerson(new SupplierResourceModels.SavePerson("EMP-001","张三","30",null,SupplierResourceModels.IdType.NATIONAL_ID,"110101199001011234","13800138000",null,null,null,LocalDate.now(),0));
        }
        var hash=ArgumentCaptor.forClass(String.class);var masked=ArgumentCaptor.forClass(String.class);
        verify(mapper).insertPerson(eq(99L),eq(10L),eq(7L),eq(20L),eq(30L),isNull(),eq("EMP-001"),eq("张三"),eq("NATIONAL_ID"),hash.capture(),masked.capture(),eq("13800138000"),isNull(),isNull(),isNull(),any());
        assertEquals(64,hash.getValue().length());assertNotEquals("110101199001011234",hash.getValue());
        assertEquals("**************1234",masked.getValue());assertFalse(masked.getValue().contains("19900101"));
    }

    @Test void projectMustBelongToSameSupplier(){
        when(mapper.supplierOrg(10,30)).thenReturn(20L);when(mapper.projectMatches(10,60,30)).thenReturn(0);
        try(var tenant=TenantContext.open(10,7);var auth=auth()){
            assertThrows(ApiException.class,()->service.createAsset(new SupplierResourceModels.SaveAsset("VEH-001","运输车辆","30","60",SupplierResourceModels.AssetType.VEHICLE,"沪A12345",null,null,null,null,null,0)));
        }
        verify(mapper,never()).insertAsset(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),any(),anyString(),anyString(),anyString(),anyString(),any(),any(),any(),any(),any());
    }

    @Test void exitedPersonAndRetiredAssetAreTerminal(){
        when(mapper.person(10,99)).thenReturn(person("EXITED"));when(mapper.asset(10,88)).thenReturn(asset("RETIRED"));
        try(var tenant=TenantContext.open(10,7);var auth=auth()){
            assertThrows(ApiException.class,()->service.personStatus(99,new SupplierResourceModels.PersonStatusCommand(SupplierResourceModels.PersonStatus.ACTIVE,null,1)));
            assertThrows(ApiException.class,()->service.assetStatus(88,new SupplierResourceModels.AssetStatusCommand(SupplierResourceModels.AssetStatus.AVAILABLE,null,1)));
        }
        verify(mapper,never()).personStatus(anyLong(),anyLong(),anyLong(),anyString(),any(),anyInt());
        verify(mapper,never()).assetStatus(anyLong(),anyLong(),anyLong(),anyString(),any(),anyInt());
    }

    private AuthorizationContext.Scope auth(){return AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("resource:person",DataScope.all(),"resource:asset",DataScope.all()),Set.of()));}
    @Test void inUseAssetCannotBeHandedOver(){
        when(mapper.asset(10,88)).thenReturn(asset("IN_USE"));
        try(var tenant=TenantContext.open(10,7);var auth=auth()){
            assertThrows(ApiException.class,()->service.handoverAsset(88,new SupplierResourceModels.AssetHandoverCommand("交接","接收人",1)));
        }
        verify(mapper,never()).handoverAsset(anyLong(),anyLong(),anyLong(),anyString(),anyString(),anyInt());
    }
    @Test void staleHandoverVersionIsRejected(){
        when(mapper.asset(10,88)).thenReturn(asset("AVAILABLE"));
        try(var tenant=TenantContext.open(10,7);var auth=auth()){
            assertThrows(ApiException.class,()->service.handoverAsset(88,new SupplierResourceModels.AssetHandoverCommand("交接","接收人",0)));
        }
        verify(mapper).handoverAsset(10,7,88,"接收人","交接",0);
    }
    @Test void activationNeedsVerifiedTrainingAndMatchingSpecialWork(){
        when(mapper.person(10,99)).thenReturn(person("PENDING", "WELDING"));
        try(var tenant=TenantContext.open(10,7);var auth=auth()){
            assertThrows(ApiException.class,()->service.personStatus(99,new SupplierResourceModels.PersonStatusCommand(SupplierResourceModels.PersonStatus.ACTIVE,null,1)));
            when(mapper.validTraining(10,99)).thenReturn(1);
            assertThrows(ApiException.class,()->service.personStatus(99,new SupplierResourceModels.PersonStatusCommand(SupplierResourceModels.PersonStatus.ACTIVE,null,1)));
            when(mapper.validSpecialWork(10,99,"WELDING")).thenReturn(1);
            when(mapper.personStatus(10,7,99,"ACTIVE",null,1)).thenReturn(1);
            assertEquals(SupplierResourceModels.PersonStatus.PENDING,service.personStatus(99,new SupplierResourceModels.PersonStatusCommand(SupplierResourceModels.PersonStatus.ACTIVE,null,1)).status());
        }
        verify(mapper,times(1)).personStatus(10,7,99,"ACTIVE",null,1);
    }
    private SupplierResourceModels.PersonRow person(String status){return person(status,null);}
    private SupplierResourceModels.PersonRow person(String status,String workType){return new SupplierResourceModels.PersonRow(99,20,30,null,"SUP-001","测试供应商",null,"EMP-001","张三","NATIONAL_ID","110101********1234","13800138000",null,null,workType,LocalDate.now(),null,status,null,7,1,LocalDateTime.now());}
    private SupplierResourceModels.AssetRow asset(String status){return new SupplierResourceModels.AssetRow(88,20,30,null,"SUP-001","测试供应商",null,"VEH-001","运输车辆","VEHICLE","沪A12345",null,null,null,null,null,status,null,7,1,LocalDateTime.now(),null,null,null);}
}

package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdmissionServiceTest {
    private final AdmissionMapper mapper=mock(AdmissionMapper.class);
    private final io.github.turbopro.ism.supplier.SupplierReferenceService suppliers=mock(io.github.turbopro.ism.supplier.SupplierReferenceService.class);
    private final AdmissionService service=new AdmissionService(mapper,mock(OperationIdGenerator.class),mock(AuditService.class),suppliers);
    @Test void restrictionBlocksApproval(){
        when(mapper.find(10,99)).thenReturn(row("SUBMITTED"));
        try(var tenant=TenantContext.open(10,8);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:admission",DataScope.all()),Set.of()))){
            assertThrows(ApiException.class,()->service.review(99,new AdmissionModels.ReviewCommand(AdmissionModels.Decision.APPROVE,"批准",0)));
        }
        verify(mapper,never()).activateSupplier(anyLong(),anyLong(),anyLong());
        verify(mapper,never()).review(anyLong(),anyLong(),anyLong(),anyString(),anyInt());
    }
    @Test void applicantCannotApproveOwnApplication(){
        when(mapper.find(10,99)).thenReturn(row("SUBMITTED"));
        try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:admission",DataScope.all()),Set.of()))){
            assertThrows(ApiException.class,()->service.review(99,new AdmissionModels.ReviewCommand(AdmissionModels.Decision.APPROVE,"批准",0)));
        }
        verify(mapper,never()).activateSupplier(anyLong(),anyLong(),anyLong());
    }
    @Test void requiredMaterialMustBeProvidedBeforeSubmit(){when(mapper.find(10,99)).thenReturn(row("DRAFT"));when(mapper.materials(10,99)).thenReturn(List.of(new AdmissionModels.MaterialRow(1,"LICENSE","营业执照",null,true,false,null,0)));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:admission",DataScope.all()),Set.of()))){assertThrows(ApiException.class,()->service.submit(99,0));}verify(mapper,never()).submit(anyLong(),anyLong(),anyLong(),anyInt());}
    @Test void terminalApplicationCannotBeReviewedAgain(){when(mapper.find(10,99)).thenReturn(row("APPROVED"));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:admission",DataScope.all()),Set.of()))){assertThrows(ApiException.class,()->service.review(99,new AdmissionModels.ReviewCommand(AdmissionModels.Decision.REJECT,"重复审核",1)));}verify(mapper,never()).review(anyLong(),anyLong(),anyLong(),anyString(),anyInt());}
    private AdmissionModels.Row row(String status){var now=LocalDateTime.now();return new AdmissionModels.Row(99,20,30,"ADM-001","SUP-001","测试供应商","设备", "新增供应来源",null,null,status,null,null,7,0,null,null,now,now);}
}

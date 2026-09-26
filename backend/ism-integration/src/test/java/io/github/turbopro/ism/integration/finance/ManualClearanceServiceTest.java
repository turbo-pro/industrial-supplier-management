package io.github.turbopro.ism.integration.finance;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.resource.file.FileReferenceService;
import io.github.turbopro.ism.supplier.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ManualClearanceServiceTest {
    private final ManualClearanceMapper mapper=mock(ManualClearanceMapper.class);
    private final SupplierService suppliers=mock(SupplierService.class);
    private final SupplierReferenceService references=mock(SupplierReferenceService.class);
    private final FileReferenceService files=mock(FileReferenceService.class);
    private ManualClearanceService service(String mode){
        return new ManualClearanceService(mapper,suppliers,references,files,mock(AuditService.class),mode,Duration.ofMinutes(15));
    }
    private ManualClearanceModels.Row row(String status){
        return new ManualClearanceModels.Row(status,88,"全部未结事项已核验",7,Instant.now().toEpochMilli(),8L,Instant.now().toEpochMilli(),"证据完整",0);
    }
    @Test void erpModeCannotBeBypassed(){
        try(var tenant=TenantContext.open(10,7)){
            assertThrows(ApiException.class,()->service("ERP").submit(99,new ManualClearanceModels.Submit("88","结清",0)));
        }
        verifyNoInteractions(mapper);
    }
    @Test void submitterCannotReviewOwnEvidence(){
        when(references.lockForExitVerification(99)).thenReturn(true);
        when(mapper.get(10,99)).thenReturn(row("SUBMITTED"));
        try(var tenant=TenantContext.open(10,7)){
            assertThrows(ApiException.class,()->service("MANUAL").review(99,new ManualClearanceModels.Review(ManualClearanceModels.Decision.APPROVE,"确认",0)));
        }
        verify(mapper,never()).review(anyLong(),anyLong(),anyString(),anyString(),anyLong(),anyInt());
    }
    @Test void missingEvidenceAndExitedSupplierBlockSubmission(){
        try(var tenant=TenantContext.open(10,7)){
            assertThrows(ApiException.class,()->service("MANUAL").submit(99,new ManualClearanceModels.Submit("88","结清",0)));
            when(references.lockForExitVerification(99)).thenReturn(true);
            assertThrows(ApiException.class,()->service("MANUAL").submit(99,new ManualClearanceModels.Submit("88","结清",0)));
        }
        verify(mapper,never()).insert(anyLong(),anyLong(),anyLong(),anyString(),anyLong());
    }
    @Test void pendingAndValidApprovalCannotBeOverwritten(){
        when(references.lockForExitVerification(99)).thenReturn(true);when(files.active(88)).thenReturn(true);
        try(var tenant=TenantContext.open(10,7)){
            for(String status:new String[]{"SUBMITTED","APPROVED"}){
                when(mapper.get(10,99)).thenReturn(row(status));
                assertThrows(ApiException.class,()->service("MANUAL").submit(99,new ManualClearanceModels.Submit("88","结清",0)));
            }
        }
        verify(mapper,never()).resubmit(anyLong(),anyLong(),anyLong(),anyString(),anyLong(),anyInt());
    }
    @Test void independentReviewerCanApproveButStaleVersionFails(){
        when(references.lockForExitVerification(99)).thenReturn(true);when(files.active(88)).thenReturn(true);
        when(mapper.get(10,99)).thenReturn(row("SUBMITTED"));
        try(var tenant=TenantContext.open(10,8)){
            assertThrows(ApiException.class,()->service("MANUAL").review(99,new ManualClearanceModels.Review(ManualClearanceModels.Decision.APPROVE,"确认",1)));
            when(mapper.review(10,99,"APPROVED","确认",8,0)).thenReturn(1);
            assertNotNull(service("MANUAL").review(99,new ManualClearanceModels.Review(ManualClearanceModels.Decision.APPROVE,"确认",0)));
        }
    }
    @Test void revokedOrMissingEvidenceIsNotClearance(){
        var gateway=new ManualClearanceGateway(mapper,files);
        try(var tenant=TenantContext.open(10,8)){
            when(mapper.get(10,99)).thenReturn(row("APPROVED"));
            assertEquals(FinancialClearanceGateway.Status.UNKNOWN,gateway.check(10,99).status());
            when(files.active(88)).thenReturn(true);
            assertEquals(FinancialClearanceGateway.Status.CLEAR,gateway.check(10,99).status());
            when(mapper.get(10,99)).thenReturn(row("REVOKED"));
            assertEquals(FinancialClearanceGateway.Status.UNKNOWN,gateway.check(10,99).status());
        }
    }
}

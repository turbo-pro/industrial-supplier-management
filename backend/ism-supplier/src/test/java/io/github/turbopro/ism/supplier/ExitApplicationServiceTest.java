package io.github.turbopro.ism.supplier;

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

class ExitApplicationServiceTest {
    final ExitMapper mapper=mock(ExitMapper.class);final SupplierMapper suppliers=mock(SupplierMapper.class);
    final SupplierService scope=mock(SupplierService.class);final AppealEvidenceVerifier evidence=mock(AppealEvidenceVerifier.class);
    final OperationIdGenerator ids=mock(OperationIdGenerator.class);final SupplierModels.SupplierRow current=mock(SupplierModels.SupplierRow.class);
    ExitApplicationService service(List<SupplierExitCheck> checks){return new ExitApplicationService(mapper,suppliers,scope,evidence,checks,ids,mock(AuditService.class));}
    void setup(){
        when(suppliers.findForNewBusiness(10,30)).thenReturn(current);when(current.status()).thenReturn("ACTIVE");
        when(current.organizationId()).thenReturn(20L);when(current.createdBy()).thenReturn(7L);
        when(evidence.available(50)).thenReturn(true);when(mapper.get(10,30,90)).thenReturn(row("SUBMITTED",0));
    }
    ExitModels.Row row(String status,int version){return new ExitModels.Row(90,30,"NORMAL","合作结束",50,status,7,LocalDateTime.now(),null,null,null,version);}
    AuthorizationContext.Scope auth(){return AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()));}
    ExitModels.Review approve(){return new ExitModels.Review(ExitModels.Decision.APPROVE,"核验通过",0);}
    @Test void applicantCannotApproveOwnExit(){setup();try(var t=TenantContext.open(10,7);var a=auth()){
        assertThrows(ApiException.class,()->service(List.of(id->ExitReadinessEvaluatorTest.clear())).review(30,90,approve()));
    }verify(suppliers,never()).completeExit(anyLong(),anyLong(),anyLong(),anyInt());}
    @Test void approvalRechecksMissingCapabilitiesAndEvidence(){setup();try(var t=TenantContext.open(10,8);var a=auth()){
        assertThrows(ApiException.class,()->service(List.of()).review(30,90,approve()));
        when(evidence.available(50)).thenReturn(false);
        assertThrows(ApiException.class,()->service(List.of(id->ExitReadinessEvaluatorTest.clear())).review(30,90,approve()));
    }verify(mapper,never()).insertResult(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyString());}
    @Test void approvalSeparatesLocalClosureFromExternalRecovery(){setup();when(suppliers.completeExit(10,8,30,0)).thenReturn(1);
        when(mapper.finish(10,30,90,"BUSINESS_CLOSED","核验通过",8,0)).thenReturn(1);
        when(mapper.get(10,30,90)).thenReturn(row("SUBMITTED",0),row("BUSINESS_CLOSED",1));
        when(ids.nextId()).thenReturn(100L);
        try(var t=TenantContext.open(10,8);var a=auth()){assertEquals("BUSINESS_CLOSED",service(List.of(id->ExitReadinessEvaluatorTest.clear())).review(30,90,approve()).status());}
        verify(suppliers).completeExit(10,8,30,0);verify(mapper).insertResult(100,10,90,30,8,"核验通过");
    }
    @Test void staleOrTerminalReviewNeverMutatesSupplier(){setup();try(var t=TenantContext.open(10,8);var a=auth()){
        when(mapper.get(10,30,90)).thenReturn(row("SUBMITTED",1));assertThrows(ApiException.class,()->service(List.of()).review(30,90,approve()));
        when(mapper.get(10,30,90)).thenReturn(row("BUSINESS_CLOSED",1));assertThrows(ApiException.class,()->service(List.of()).review(30,90,approve()));
    }verify(suppliers,never()).completeExit(anyLong(),anyLong(),anyLong(),anyInt());}
    @Test void onlyApplicantCanWithdraw(){setup();try(var t=TenantContext.open(10,8);var a=auth()){
        assertThrows(ApiException.class,()->service(List.of()).cancel(30,90,new ExitModels.Version(0)));
    }verify(mapper,never()).finish(anyLong(),anyLong(),anyLong(),anyString(),anyString(),anyLong(),anyInt());}
    @Test void hiddenAndMovedSupplierNeverLeaksApplications(){setup();when(scope.get(30)).thenThrow(new ApiException(CommonErrorCode.NOT_FOUND));
        try(var t=TenantContext.open(11,8);var a=auth()){assertThrows(ApiException.class,()->service(List.of()).list(30,0,20));}
        verifyNoInteractions(mapper);
    }
    @Test void currentScopeIsRecheckedAfterSupplierLock(){setup();
        try(var t=TenantContext.open(10,8);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.organizations(Set.of(21L))),Set.of()))){
            assertThrows(ApiException.class,()->service(List.of()).create(30,new ExitModels.Create(ExitModels.Type.NORMAL,"结束合作","50")));
        }verifyNoInteractions(mapper);
    }
    @Test void duplicatePendingApplicationBecomesConflict(){setup();when(ids.nextId()).thenReturn(90L);
        when(mapper.insert(90,10,30,"NORMAL","结束合作",50,7)).thenThrow(new DuplicateKeyException("pending"));
        try(var t=TenantContext.open(10,7);var a=auth()){
            assertEquals(CommonErrorCode.CONFLICT,assertThrows(ApiException.class,()->service(List.of()).create(30,new ExitModels.Create(ExitModels.Type.NORMAL,"结束合作","50"))).errorCode());
        }
    }
    @Test void recheckIsVersionedAndTerminalFactsStayFrozen(){setup();when(mapper.advanceVersion(10,30,90,0)).thenReturn(1);
        when(mapper.get(10,30,90)).thenReturn(row("SUBMITTED",0),row("SUBMITTED",1));
        try(var t=TenantContext.open(10,8);var a=auth()){assertEquals(1,service(List.of(id->ExitReadinessEvaluatorTest.clear())).recheck(30,90,new ExitModels.Version(0)).version());}
        verify(mapper).advanceVersion(10,30,90,0);
    }
    @Test void creationRequiresActiveEvidenceAndNonExitedSupplier(){setup();
        try(var t=TenantContext.open(10,7);var a=auth()){
            when(evidence.available(50)).thenReturn(false);
            assertThrows(ApiException.class,()->service(List.of()).create(30,new ExitModels.Create(ExitModels.Type.NORMAL,"退出","50")));
            when(current.status()).thenReturn("EXITED");
            assertThrows(ApiException.class,()->service(List.of()).create(30,new ExitModels.Create(ExitModels.Type.NORMAL,"退出","50")));
        }verify(mapper,never()).insert(anyLong(),anyLong(),anyLong(),anyString(),anyString(),anyLong(),anyLong());
    }
    @Test void rejectionDoesNotRequireClearedBusinessAndDoesNotCloseSupplier(){setup();
        when(mapper.finish(10,30,90,"REJECTED","继续处置",8,0)).thenReturn(1);
        try(var t=TenantContext.open(10,8);var a=auth()){
            service(List.of()).review(30,90,new ExitModels.Review(ExitModels.Decision.REJECT,"继续处置",0));
        }
        verify(suppliers,never()).completeExit(anyLong(),anyLong(),anyLong(),anyInt());
        verify(mapper,never()).insertResult(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyString());
    }
}

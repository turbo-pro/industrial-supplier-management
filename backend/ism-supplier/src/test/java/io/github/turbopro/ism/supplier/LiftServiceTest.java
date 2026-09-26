package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LiftServiceTest {
    final LiftMapper mapper=mock(LiftMapper.class);
    final BlacklistMapper restrictions=mock(BlacklistMapper.class);
    final BlacklistService cases=mock(BlacklistService.class);
    final AppealEvidenceVerifier evidence=mock(AppealEvidenceVerifier.class);
    final OperationIdGenerator ids=mock(OperationIdGenerator.class);
    final BlacklistModels.Row restriction=mock(BlacklistModels.Row.class);
    LiftService service(List<SupplierLiftCheck> checks){return new LiftService(mapper,restrictions,cases,evidence,checks,ids,mock(AuditService.class),Duration.ofDays(7));}
    List<SupplierLiftCheck> checks(long quality){return List.of(id->List.of(
        new SupplierExitCheck.Blocker("OPEN_QUALITY","质量整改",quality,"/quality"),
        new SupplierExitCheck.Blocker("OPEN_SAFETY","安全整改",0,"/safety"),
        new SupplierExitCheck.Blocker("OPEN_IMPROVEMENT","绩效改进",0,"/performance")));}
    void setup(){
        var visible=mock(BlacklistModels.View.class);when(visible.supplierId()).thenReturn("30");when(cases.get(80)).thenReturn(visible);
        when(restrictions.currentCase(10,80)).thenReturn(restriction);when(restriction.id()).thenReturn(80L);
        when(restriction.supplierId()).thenReturn(30L);when(restriction.status()).thenReturn("APPROVED");
        when(restriction.restrictionType()).thenReturn(BlacklistModels.RestrictionType.BLACKLIST);
        when(mapper.get(10,80,90)).thenReturn(new LiftModels.Row(90,80,"整改完成",50,"SUBMITTED",7,LocalDateTime.now(),null,null,null,0));
        when(evidence.available(50)).thenReturn(true);
    }
    @Test void applicantCannotApprove(){setup();try(var t=TenantContext.open(10,7)){
        assertThrows(ApiException.class,()->service(checks(0)).review(80,90,new LiftModels.Review(LiftModels.Decision.APPROVE,"通过",0)));
    }verify(mapper,never()).result(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyString());}
    @Test void correctionsAndMissingProvidersFailClosed(){setup();try(var t=TenantContext.open(10,8)){
        assertFalse(service(checks(1)).readiness(80).ready());assertFalse(service(List.of()).readiness(80).ready());
    }}
    @Test void watchRequiresCompletedObservation(){setup();when(restriction.restrictionType()).thenReturn(BlacklistModels.RestrictionType.WATCH);
        when(restrictions.approvedAtMillis(10,80)).thenReturn(null);
        try(var t=TenantContext.open(10,8)){
            assertFalse(service(checks(0)).readiness(80).ready());
            when(restrictions.approvedAtMillis(10,80)).thenReturn(Instant.now().toEpochMilli());
            assertFalse(service(checks(0)).readiness(80).ready());
            when(restrictions.approvedAtMillis(10,80)).thenReturn(Instant.now().minus(Duration.ofDays(8)).toEpochMilli());
            assertTrue(service(checks(0)).readiness(80).ready());
        }
    }
    @Test void approvalCreatesSeparateResultWithoutChangingOriginal(){setup();when(ids.nextId()).thenReturn(100L);
        when(mapper.review(10,80,90,"APPROVED","通过",8,0)).thenReturn(1);
        try(var t=TenantContext.open(10,8)){service(checks(0)).review(80,90,new LiftModels.Review(LiftModels.Decision.APPROVE,"通过",0));}
        verify(mapper).result(100,10,80,90,8,"通过");
        verify(restrictions,never()).revoke(anyLong(),anyLong(),anyString(),anyLong(),anyInt());
    }
    @Test void staleVersionCannotProduceResult(){setup();try(var t=TenantContext.open(10,8)){
        assertThrows(ApiException.class,()->service(checks(0)).review(80,90,new LiftModels.Review(LiftModels.Decision.APPROVE,"通过",1)));
    }verify(mapper,never()).result(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyString());}
    @Test void unavailableEvidenceCannotApprove(){setup();when(evidence.available(50)).thenReturn(false);
        try(var t=TenantContext.open(10,8)){assertThrows(ApiException.class,()->service(checks(0)).review(80,90,new LiftModels.Review(LiftModels.Decision.APPROVE,"通过",0)));}
    }
    @Test void liftedCaseCannotAcceptNewApplications(){setup();when(restrictions.lifted(10,80)).thenReturn(1);
        try(var t=TenantContext.open(10,7)){assertThrows(ApiException.class,()->service(checks(0)).create(80,new LiftModels.Create("完成","50")));}
    }
    @Test void rejectionDoesNotRequireReadinessAndNeverLifts(){setup();when(mapper.review(10,80,90,"REJECTED","证据不足",8,0)).thenReturn(1);
        try(var t=TenantContext.open(10,8)){service(List.of()).review(80,90,new LiftModels.Review(LiftModels.Decision.REJECT,"证据不足",0));}
        verify(mapper,never()).result(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyString());
    }
    @Test void hiddenCaseNeverLeaksHistory(){when(cases.get(80)).thenThrow(new ApiException(io.github.turbopro.ism.common.api.error.CommonErrorCode.NOT_FOUND));
        try(var t=TenantContext.open(11,8)){assertThrows(ApiException.class,()->service(checks(0)).list(80,0,20));}
        verifyNoInteractions(mapper);
    }
}

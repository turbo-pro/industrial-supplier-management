package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EnterpriseSupplierRestrictionEvaluatorTest {
    @Test void incompleteProviderResultsCannotRepresentAnAllow(){
        assertThrows(NullPointerException.class,()->new SupplierRestrictionEvaluator.Evaluation(null,RestrictionBusinessDate.today(),List.of()));
        assertThrows(NullPointerException.class,()->new SupplierRestrictionEvaluator.Evaluation(SupplierRestrictionEvaluator.Decision.ALLOW,null,List.of()));
        assertThrows(NullPointerException.class,()->new SupplierRestrictionEvaluator.Hit("RULE","99",null,"命中"));
    }
    final SupplierMapper suppliers=mock(SupplierMapper.class);final BlacklistMapper restrictions=mock(BlacklistMapper.class);
    final SupplierRestrictionEvaluator evaluator=new EnterpriseSupplierRestrictionEvaluator(suppliers,restrictions);
    BlacklistModels.Row row(long id,BlacklistModels.RestrictionType type){var r=mock(BlacklistModels.Row.class);when(r.id()).thenReturn(id);when(r.restrictionType()).thenReturn(type);return r;}
    @Test void returnsAllReasonsAndImmutableSnapshotWithoutSensitiveReasons(){
        var cases=List.of(row(1,BlacklistModels.RestrictionType.WATCH),row(2,BlacklistModels.RestrictionType.TEMPORARY));
        when(restrictions.currentEffectiveCases(eq(10L),eq(99L),any())).thenReturn(cases);when(suppliers.currentPendingExits(10,99)).thenReturn(List.of(3L));
        try(var tenant=TenantContext.open(10,7)){
            var result=evaluator.evaluateLocked(99,"SUSPENDED",SupplierRestrictionEvaluator.Action.PROJECT_CREATE);
            assertEquals(SupplierRestrictionEvaluator.Decision.DENY,result.decision());
            assertEquals(List.of("SUPPLIER_STATUS","WATCH","TEMPORARY","PENDING_EXIT"),result.hits().stream().map(SupplierRestrictionEvaluator.Hit::code).toList());
            assertThrows(UnsupportedOperationException.class,()->result.hits().clear());assertEquals(RestrictionBusinessDate.today(),result.businessDate());
        }
        for(var restriction:cases)verify(restriction,never()).reason();
    }
    @Test void admissionStatusesDifferButUnknownStatusAlwaysFailsClosed(){
        try(var tenant=TenantContext.open(10,7)){
            for(var status:List.of("DRAFT","ACTIVE","SUSPENDED"))assertEquals(SupplierRestrictionEvaluator.Decision.ALLOW,evaluator.evaluateLocked(99,status,SupplierRestrictionEvaluator.Action.QUALIFICATION_APPLY).decision());
            for(var status:List.of("DRAFT","SUSPENDED","EXITED","UNKNOWN"))assertEquals(SupplierRestrictionEvaluator.Decision.DENY,evaluator.evaluateLocked(99,status,SupplierRestrictionEvaluator.Action.GENERIC_NEW_BUSINESS).decision());
            assertEquals(SupplierRestrictionEvaluator.Decision.DENY,evaluator.evaluateLocked(99,null,SupplierRestrictionEvaluator.Action.QUALIFICATION_APPLY).decision());
        }
    }
    @Test void invalidInputsNeverLoadRestrictions(){try(var tenant=TenantContext.open(10,7)){
        assertThrows(ApiException.class,()->evaluator.evaluateLocked(99,"ACTIVE",null));
        assertThrows(ApiException.class,()->evaluator.evaluateLocked(0,"ACTIVE",SupplierRestrictionEvaluator.Action.SITE_ENTER));
    }verifyNoInteractions(suppliers,restrictions);}
}

package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RestrictionGateHitServiceTest {
    final RestrictionGateHitMapper mapper=mock(RestrictionGateHitMapper.class);
    final OperationIdGenerator ids=mock(OperationIdGenerator.class);
    final SupplierService suppliers=mock(SupplierService.class);
    final RestrictionGateHitService service=new RestrictionGateHitService(mapper,ids,suppliers);
    @Test void recordsOnlyStableWarnAndDenyHitFields(){
        when(ids.nextId()).thenReturn(101L,102L);
        var evaluation=new SupplierRestrictionEvaluator.Evaluation(SupplierRestrictionEvaluator.Decision.DENY,LocalDate.of(2026,9,28),List.of(
            new SupplierRestrictionEvaluator.Hit("WATCH","77",SupplierRestrictionEvaluator.Decision.WARN,"敏感原因"),
            new SupplierRestrictionEvaluator.Hit("BLACKLIST","88",SupplierRestrictionEvaluator.Decision.DENY,"另一个敏感原因")));
        try(var tenant=TenantContext.open(10,7)){service.record(30,SupplierRestrictionEvaluator.Action.CONTRACT_CREATE,evaluation);}
        verify(mapper).insert(101,10,30,7,"CONTRACT_CREATE","WARN","WATCH","77",evaluation.businessDate());
        verify(mapper).insert(102,10,30,7,"CONTRACT_CREATE","DENY","BLACKLIST","88",evaluation.businessDate());
        verifyNoMoreInteractions(mapper);
    }
    @Test void readChecksSupplierVisibilityBeforeLedgerQuery(){
        doThrow(new ApiException(io.github.turbopro.ism.common.api.error.CommonErrorCode.NOT_FOUND)).when(suppliers).get(30);
        try(var tenant=TenantContext.open(10,7)){
            assertThrows(ApiException.class,()->service.list(30,0,20));
            assertThrows(ApiException.class,()->service.list(30,-1,20));
        }
        verifyNoInteractions(mapper);
    }
}

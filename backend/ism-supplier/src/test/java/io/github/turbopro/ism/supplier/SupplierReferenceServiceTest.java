package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupplierReferenceServiceTest {
    private final SupplierMapper mapper=mock(SupplierMapper.class);
    private final BlacklistMapper blacklist=mock(BlacklistMapper.class);
    private final RestrictionGateHitService gateHits=mock(RestrictionGateHitService.class);
    private final SupplierReferenceService service=new SupplierReferenceService(mapper,blacklist,new EnterpriseSupplierRestrictionEvaluator(mapper,blacklist),gateHits);
    @Test void usesCurrentSupplierAndRestrictionReads(){
        var row=mock(SupplierModels.SupplierRow.class);
        when(row.status()).thenReturn("ACTIVE");
        when(mapper.findForNewBusiness(10,99)).thenReturn(row);
        var restriction=mock(BlacklistModels.Row.class);when(restriction.id()).thenReturn(101L);when(restriction.restrictionType()).thenReturn(BlacklistModels.RestrictionType.BLACKLIST);
        when(blacklist.currentEffectiveCases(eq(10L),eq(99L),any())).thenReturn(List.of(restriction));
        try(var tenant=TenantContext.open(10,7)){
            assertNull(service.activeForNewBusiness(99));
            when(blacklist.currentEffectiveCases(eq(10L),eq(99L),any())).thenReturn(List.of());
            assertNotNull(service.activeForNewBusiness(99));
        }
        verify(blacklist,times(2)).lockSupplier(10,99);
        verify(mapper,never()).find(anyLong(),anyLong());
        verify(gateHits,only()).record(eq(99L),eq(SupplierRestrictionEvaluator.Action.GENERIC_NEW_BUSINESS),argThat(e->e.decision()==SupplierRestrictionEvaluator.Decision.DENY));
    }
    @Test void currentExitedSupplierCannotStartBusiness(){
        var row=mock(SupplierModels.SupplierRow.class);when(row.status()).thenReturn("EXITED");
        when(mapper.findForNewBusiness(10,99)).thenReturn(row);
        try(var tenant=TenantContext.open(10,7)){assertNull(service.activeForNewBusiness(99));}
    }
    @Test void pendingExitBlocksAllNewBusinessUntilCancelledOrRejected(){
        var row=mock(SupplierModels.SupplierRow.class);when(row.status()).thenReturn("ACTIVE");
        when(mapper.findForNewBusiness(10,99)).thenReturn(row);when(mapper.currentPendingExits(10,99)).thenReturn(List.of(101L));
        try(var tenant=TenantContext.open(10,7)){
            assertNull(service.activeForNewBusiness(99));assertNull(service.eligibleForAdmission(99));
            when(mapper.currentPendingExits(10,99)).thenReturn(List.of());
            assertNotNull(service.activeForNewBusiness(99));
        }
        verify(gateHits,times(2)).record(eq(99L),any(),any());
    }
    @ParameterizedTest @EnumSource(SupplierRestrictionEvaluator.Action.class)
    void everyControlledActionWarnsOnWatchAndBlocksOnRestrictionOrExit(SupplierRestrictionEvaluator.Action action){
        var row=mock(SupplierModels.SupplierRow.class);when(row.status()).thenReturn("ACTIVE");when(mapper.findForNewBusiness(10,99)).thenReturn(row);
        var watch=mock(BlacklistModels.Row.class);when(watch.id()).thenReturn(101L);when(watch.restrictionType()).thenReturn(BlacklistModels.RestrictionType.WATCH);
        var deny=mock(BlacklistModels.Row.class);when(deny.id()).thenReturn(102L);when(deny.restrictionType()).thenReturn(BlacklistModels.RestrictionType.BLACKLIST);
        when(blacklist.currentEffectiveCases(eq(10L),eq(99L),any())).thenReturn(List.of(watch));
        try(var tenant=TenantContext.open(10,7)){
            assertNotNull(service.eligibleForBusiness(99,action));
            when(blacklist.currentEffectiveCases(eq(10L),eq(99L),any())).thenReturn(List.of(watch,deny));assertNull(service.eligibleForBusiness(99,action));
            when(blacklist.currentEffectiveCases(eq(10L),eq(99L),any())).thenReturn(List.of(watch));
            when(mapper.currentPendingExits(10,99)).thenReturn(List.of(103L));assertNull(service.eligibleForBusiness(99,action));
        }
        verify(gateHits,times(3)).record(eq(99L),eq(action),any());
    }
    @Test void missingActionCannotAuthorizeOrReadBusiness(){
        try(var tenant=TenantContext.open(10,7)){assertThrows(io.github.turbopro.ism.common.api.error.ApiException.class,()->service.eligibleForBusiness(99,null));}
        verifyNoInteractions(mapper,blacklist);
    }
}

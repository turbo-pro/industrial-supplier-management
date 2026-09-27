package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RestrictionExplanationServiceTest {
    final SupplierService scope=mock(SupplierService.class);
    final SupplierMapper suppliers=mock(SupplierMapper.class);
    final BlacklistMapper restrictions=mock(BlacklistMapper.class);
    final AuditService audit=mock(AuditService.class);
    final SupplierModels.SupplierRow supplier=mock(SupplierModels.SupplierRow.class);
    final RestrictionExplanationService service=new RestrictionExplanationService(scope,suppliers,new EnterpriseSupplierRestrictionEvaluator(suppliers,restrictions),audit);
    AuthorizationContext.Scope auth(){return AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()));}
    void setup(){when(suppliers.findForNewBusiness(10,30)).thenReturn(supplier);when(supplier.organizationId()).thenReturn(20L);when(supplier.status()).thenReturn("ACTIVE");}
    BlacklistModels.Row restriction(long id,BlacklistModels.RestrictionType type){var row=mock(BlacklistModels.Row.class);when(row.id()).thenReturn(id);when(row.restrictionType()).thenReturn(type);return row;}
    @Test void allHitsAreRetainedAndDenyDominatesWarning(){setup();
        var cases=List.of(restriction(51,BlacklistModels.RestrictionType.WATCH),restriction(52,BlacklistModels.RestrictionType.BLACKLIST),restriction(53,BlacklistModels.RestrictionType.TEMPORARY));
        when(restrictions.currentEffectiveCases(eq(10L),eq(30L),any())).thenReturn(cases);
        when(suppliers.currentPendingExits(10,30)).thenReturn(List.of(90L));
        try(var t=TenantContext.open(10,8);var a=auth()){
            var view=service.explain(30,RestrictionExplanationService.Action.PROJECT_CREATE);
            assertEquals(RestrictionExplanationService.Decision.DENY,view.decision());assertEquals(4,view.hits().size());
            assertTrue(view.advisoryOnly());assertEquals("TENANT_ENTERPRISE",view.scope());
            assertEquals(List.of("WATCH","BLACKLIST","TEMPORARY","PENDING_EXIT"),view.hits().stream().map(RestrictionExplanationService.Hit::code).toList());
        }verify(audit).append(any());
    }
    @Test void watchOnlyWarnsAndEmptyActiveSupplierAllows(){setup();
        var watch=restriction(51,BlacklistModels.RestrictionType.WATCH);
        try(var t=TenantContext.open(10,8);var a=auth()){
            assertEquals(RestrictionExplanationService.Decision.ALLOW,service.explain(30,RestrictionExplanationService.Action.SITE_ENTER).decision());
            when(restrictions.currentEffectiveCases(eq(10L),eq(30L),any())).thenReturn(List.of(watch));
            assertEquals(RestrictionExplanationService.Decision.WARN,service.explain(30,RestrictionExplanationService.Action.SITE_ENTER).decision());
        }
    }
    @Test void admissionAllowsDraftButProjectAndExitedAreDenied(){setup();when(supplier.status()).thenReturn("DRAFT");
        try(var t=TenantContext.open(10,8);var a=auth()){
            assertEquals(RestrictionExplanationService.Decision.ALLOW,service.explain(30,RestrictionExplanationService.Action.QUALIFICATION_APPLY).decision());
            assertEquals(RestrictionExplanationService.Decision.DENY,service.explain(30,RestrictionExplanationService.Action.PROJECT_CREATE).decision());
            when(supplier.status()).thenReturn("EXITED");
            assertEquals(RestrictionExplanationService.Decision.DENY,service.explain(30,RestrictionExplanationService.Action.QUALIFICATION_APPLY).decision());
        }
    }
    @Test void hiddenSupplierNeverLoadsHitsOrWritesAudit(){when(scope.get(30)).thenThrow(new ApiException(io.github.turbopro.ism.common.api.error.CommonErrorCode.NOT_FOUND));
        try(var t=TenantContext.open(11,8);var a=auth()){assertThrows(ApiException.class,()->service.explain(30,RestrictionExplanationService.Action.PROJECT_CREATE));}
        verifyNoInteractions(suppliers,restrictions,audit);
    }
    @Test void movedSupplierScopeIsCheckedUnderLock(){setup();
        try(var t=TenantContext.open(10,8);var a=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.organizations(Set.of(21L))),Set.of()))){
            assertThrows(ApiException.class,()->service.explain(30,RestrictionExplanationService.Action.PROJECT_CREATE));
        }verifyNoInteractions(restrictions,audit);
    }
    @Test void unknownActionCannotBecomeAnAllow(){assertThrows(ApiException.class,()->service.explain(30,null));verifyNoInteractions(scope,suppliers,restrictions,audit);}
}

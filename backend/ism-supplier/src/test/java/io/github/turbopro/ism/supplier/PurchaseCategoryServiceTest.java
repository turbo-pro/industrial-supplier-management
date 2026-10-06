package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchaseCategoryServiceTest {
    private final PurchaseCategoryMapper mapper=mock(PurchaseCategoryMapper.class);
    private final SupplierService suppliers=mock(SupplierService.class);
    private final AuditService audit=mock(AuditService.class);
    private final PurchaseCategoryService service=new PurchaseCategoryService(mapper,suppliers,mock(OperationIdGenerator.class),audit);
    private void supplier(){var view=mock(SupplierModels.SupplierView.class);when(view.id()).thenReturn("99");when(view.status()).thenReturn(SupplierModels.Status.ACTIVE);when(suppliers.get(99)).thenReturn(view);}
    @Test void staleVersionCannotReplaceAssignments(){supplier();when(mapper.assigned(10,99)).thenReturn(List.of(50L));when(mapper.activeIdsForUpdate(10,List.of(51L))).thenReturn(List.of(51L));try(var tenant=TenantContext.open(10,7)){assertThrows(ApiException.class,()->service.assign(99,new PurchaseCategoryModels.Assign(List.of("50","51"),0)));}verify(mapper,never()).clearAssignments(anyLong(),anyLong());verifyNoInteractions(audit);}
    @Test void inactiveCategoryCannotBeAddedButExistingCanRemain(){supplier();when(mapper.assigned(10,99)).thenReturn(List.of(50L));try(var tenant=TenantContext.open(10,7)){assertThrows(ApiException.class,()->service.assign(99,new PurchaseCategoryModels.Assign(List.of("50","51"),0)));}verify(mapper,never()).claimSupplier(anyLong(),anyLong(),anyInt());when(mapper.claimSupplier(10,99,0)).thenReturn(1);try(var tenant=TenantContext.open(10,7)){var updated=service.assign(99,new PurchaseCategoryModels.Assign(List.of("50"),0));assertEquals(List.of("50"),updated.categoryIds());}verify(mapper).assign(10,99,50);verify(audit).append(any(AuditService.AuditCommand.class));}
    @Test void duplicateOrStaleMaterialPolicyCannotReplaceRules(){
        var row=mock(PurchaseCategoryModels.Row.class);when(mapper.category(10,50)).thenReturn(row);
        var duplicate=new PurchaseCategoryModels.SaveRequiredMaterials(List.of(new PurchaseCategoryModels.RequiredMaterial("SAFETY","安全资质"),new PurchaseCategoryModels.RequiredMaterial("SAFETY","重复")),0);
        try(var tenant=TenantContext.open(10,7)){assertThrows(ApiException.class,()->service.saveMaterialPolicy(50,duplicate));}
        verify(mapper,never()).clearRequiredMaterials(anyLong(),anyLong());
        var valid=new PurchaseCategoryModels.SaveRequiredMaterials(List.of(new PurchaseCategoryModels.RequiredMaterial("SAFETY","安全资质")),0);
        try(var tenant=TenantContext.open(10,7)){assertThrows(ApiException.class,()->service.saveMaterialPolicy(50,valid));}
        verify(mapper,never()).clearRequiredMaterials(anyLong(),anyLong());verifyNoInteractions(audit);
    }
}

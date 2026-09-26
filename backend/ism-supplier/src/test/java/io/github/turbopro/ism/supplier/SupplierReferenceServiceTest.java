package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupplierReferenceServiceTest {
    private final SupplierMapper mapper=mock(SupplierMapper.class);
    private final BlacklistMapper blacklist=mock(BlacklistMapper.class);
    private final SupplierReferenceService service=new SupplierReferenceService(mapper,blacklist);
    @Test void usesCurrentSupplierAndRestrictionReads(){
        var row=mock(SupplierModels.SupplierRow.class);
        when(row.status()).thenReturn("ACTIVE");
        when(mapper.findForNewBusiness(10,99)).thenReturn(row);
        when(blacklist.currentActive(eq(10L),eq(99L),any())).thenReturn(List.of(101L));
        try(var tenant=TenantContext.open(10,7)){
            assertNull(service.activeForNewBusiness(99));
            when(blacklist.currentActive(eq(10L),eq(99L),any())).thenReturn(List.of());
            assertNotNull(service.activeForNewBusiness(99));
        }
        verify(blacklist,times(2)).lockSupplier(10,99);
        verify(mapper,never()).find(anyLong(),anyLong());
    }
    @Test void currentExitedSupplierCannotStartBusiness(){
        var row=mock(SupplierModels.SupplierRow.class);when(row.status()).thenReturn("EXITED");
        when(mapper.findForNewBusiness(10,99)).thenReturn(row);
        try(var tenant=TenantContext.open(10,7)){assertNull(service.activeForNewBusiness(99));}
    }
}

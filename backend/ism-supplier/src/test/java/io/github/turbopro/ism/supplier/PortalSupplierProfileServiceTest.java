package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.portal.PortalIdentityContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalSupplierProfileServiceTest {
    private final PortalSupplierProfileMapper mapper=mock(PortalSupplierProfileMapper.class);
    private final PortalSupplierProfileService service=new PortalSupplierProfileService(mapper);

    @Test void employeeTenantContextAloneNeverGrantsPortalAccess(){
        try(var tenant=TenantContext.open(10,20)){
            assertThatThrownBy(service::current).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.errorCode()).isEqualTo(CommonErrorCode.UNAUTHENTICATED));
        }
        verifyNoInteractions(mapper);
    }
    @Test void mismatchedTenantOrActorFailsClosedAndScopeRestores(){
        try(var tenant=TenantContext.open(10,20);var portal=PortalIdentityContext.open(10,20,30)){
            for(var binding:new long[][]{{11,20,30},{10,21,30}}){
                try(var invalid=PortalIdentityContext.open(binding[0],binding[1],binding[2])){
                    assertThatThrownBy(service::current).isInstanceOf(ApiException.class);
                }
                assertThat(PortalIdentityContext.require().supplierId()).isEqualTo(30);
            }
        }
        try(var tenant=TenantContext.open(10,20)){assertThatThrownBy(PortalIdentityContext::require).isInstanceOf(ApiException.class);}
        verifyNoInteractions(mapper);
    }
    @Test void missingOrNewlyUnavailableProfileDoesNotLoadCategories(){
        try(var tenant=TenantContext.open(10,20);var portal=PortalIdentityContext.open(10,20,30)){
            assertThatThrownBy(service::current).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.errorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        }
        verify(mapper).profile(10,30);verifyNoMoreInteractions(mapper);
    }
}

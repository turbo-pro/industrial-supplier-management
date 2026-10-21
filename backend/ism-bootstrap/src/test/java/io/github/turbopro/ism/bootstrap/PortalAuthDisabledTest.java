package io.github.turbopro.ism.bootstrap;
import io.github.turbopro.ism.iam.portal.*;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PortalAuthDisabledTest {
    @Test void disabledAuthDoesNotReadIdentitiesOrCreateSessions(){
        var locator=mock(PortalIdentityLocator.class);var accounts=mock(PortalAccountMapper.class);var identity=mock(PortalTenantIdentityMapper.class);
        var tenants=mock(PortalRegistrationTenantGate.class);var suppliers=mock(PortalSupplierAccessGate.class);var passwords=mock(PasswordEncoder.class);
        var ids=mock(OperationIdGenerator.class);var audit=mock(AuditService.class);var manager=mock(PlatformTransactionManager.class);
        var service=new PortalAuthService(locator,accounts,identity,tenants,suppliers,passwords,ids,audit,manager,false);
        var command=new PortalAuthModels.Login("TENANT","portaluser","Portal#Pass123");
        assertThatThrownBy(()->service.login(command)).isInstanceOf(ApiException.class).hasMessageContaining("尚未启用");
        assertThatThrownBy(()->service.authenticate("A".repeat(43))).isInstanceOf(ApiException.class).hasMessageContaining("尚未启用");
        verifyNoInteractions(locator,accounts,identity,tenants,suppliers,ids,audit,manager);
        assertThat(command.toString()).doesNotContain(command.password());
    }
}

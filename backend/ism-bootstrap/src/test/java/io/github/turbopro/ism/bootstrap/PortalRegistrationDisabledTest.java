package io.github.turbopro.ism.bootstrap;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.operation.*;
import io.github.turbopro.ism.supplier.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PortalRegistrationDisabledTest {
    @Test void disabledRegistrationFailsBeforeAnyLookupOrAccountWrite(){
        var locator=mock(PortalInvitationTokenLocator.class);var invites=mock(PortalInvitationMapper.class);var suppliers=mock(SupplierMapper.class);
        var accounts=mock(PortalAccountProvisioner.class);var gate=mock(PortalRegistrationTenantGate.class);var ids=mock(OperationIdGenerator.class);var audit=mock(AuditService.class);
        var service=new PortalRegistrationService(locator,invites,suppliers,accounts,gate,ids,audit,false);
        var command=new PortalRegistrationModels.Register("A".repeat(43),"portaluser","Portal#Pass123");
        assertThatThrownBy(()->service.register(command)).isInstanceOf(ApiException.class).hasMessageContaining("尚未启用");
        verifyNoInteractions(locator,invites,suppliers,accounts,gate,ids,audit);
        assertThat(command.toString()).doesNotContain(command.password(),command.invitationToken());
    }
}

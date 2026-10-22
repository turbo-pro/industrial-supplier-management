package io.github.turbopro.ism.bootstrap;
import io.github.turbopro.ism.iam.portal.*;
import io.github.turbopro.ism.common.api.error.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PortalPasswordRecoveryDisabledTest {
    @Test void eitherDisabledSwitchPreventsLookupAndMutation(){
        for(boolean[] flags:java.util.List.of(new boolean[]{false,true},new boolean[]{true,false})){
            var manager=org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
            var service=new PortalPasswordRecoveryService(null,null,null,null,null,null,null,null,null,null,manager,flags[0],flags[1]);
            assertThat(service.enabled()).isFalse();
            assertThatThrownBy(()->service.reset(new PortalPasswordRecoveryService.Reset("A".repeat(43),"Recovered#Pass456"))).isInstanceOf(ApiException.class).hasMessageContaining("尚未启用");
            org.mockito.Mockito.verifyNoInteractions(manager);
        }
    }
}

package io.github.turbopro.ism.iam.site;
import io.github.turbopro.ism.common.infrastructure.tenant.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
class SiteContextScopeTest {
    @Test void nestedContextRestoresAndRequestCloseClears(){
        assertThat(SiteContext.current()).isEmpty();
        try(var tenant=TenantContext.open(10,20);var site=SiteContext.open(30,Instant.now().plusSeconds(60))){
            assertThat(SiteContext.require().siteId()).isEqualTo(30);
            try(var nested=SiteContext.open(31,Instant.now().plusSeconds(60))){assertThat(SiteContext.require().siteId()).isEqualTo(31);}
            assertThat(SiteContext.require().siteId()).isEqualTo(30);
            try(var other=TenantContext.open(11,20)){assertThatThrownBy(SiteContext::require).isInstanceOf(TenantIsolationException.class);}
        }
        assertThat(SiteContext.current()).isEmpty();
    }
}

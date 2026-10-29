package io.github.turbopro.ism.bootstrap;
import io.github.turbopro.ism.platform.tenant.TenantModels;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class SiteUpgradeBrowserFixture extends SiteInitializationSupport {
    @LocalServerPort int port;
    @Test void serveBrowserAndVerifyOwnership()throws Exception{
        Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();consoleUsers();
        long target=pending(),other=pending();
        for(long id:new long[]{target,other})tenants.initialize(id,new TenantModels.InitializeTenant("browser-site-admin","原场站管理员","Site#Initial123",null,"SINGLE_SITE","工厂","原场站"));
        long site=jdbc.queryForObject("SELECT default_site_id FROM plt_tenant WHERE id=?",Long.class,target);
        var userBefore=jdbc.queryForMap("SELECT * FROM iam_user WHERE tenant_id=?",target);
        Files.writeString(control.resolve("fixture.json"),"{\"tenant\":\""+target+"\",\"site\":\""+site+"\"}");
        Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
        long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();
        while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(250);}
        assertThat(Files.exists(control.resolve("finish"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT operation_mode FROM plt_tenant WHERE id=?",String.class,target)).isEqualTo("MULTI_SITE");
        assertThat(jdbc.queryForObject("SELECT version FROM plt_tenant WHERE id=?",Integer.class,target)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT default_site_id FROM plt_tenant WHERE id=?",Long.class,target)).isEqualTo(site);
        assertThat(jdbc.queryForObject("SELECT organization_id FROM iam_user_organization WHERE tenant_id=? AND is_primary=1",Long.class,target)).isEqualTo(site);
        assertThat(jdbc.queryForObject("SELECT current_organization_id FROM iam_user_context WHERE tenant_id=?",Long.class,target)).isEqualTo(site);
        assertThat(jdbc.queryForMap("SELECT * FROM iam_user WHERE tenant_id=?",target)).isEqualTo(userBefore);
        assertThat(count("iam_organization",target)).isEqualTo(2);assertThat(count("iam_organization_closure",target)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE action='TENANT_SITE_UPGRADE' AND object_id=?",Integer.class,target)).isOne();
        assertThat(jdbc.queryForObject("SELECT operation_mode FROM plt_tenant WHERE id=?",String.class,other)).isEqualTo("SINGLE_SITE");
    }
}

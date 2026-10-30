package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class TenantCreationBrowserFixture extends SiteInitializationSupport {
    @LocalServerPort int port;
    @Test void serveCreationInitializationAndFirstLoginAndCheckDurableResults()throws Exception{
        Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();consoleUsers();
        jdbc.update("INSERT INTO plt_package(id,package_code,package_name,status) VALUES(880000,'BROWSER_CREATE','浏览器创建套餐','ACTIVE')");jdbc.update("INSERT INTO plt_package_version(id,package_id,version_no,version_name,status) VALUES(880001,880000,1,'正式发布','PUBLISHED')");
        Files.writeString(control.resolve("fixture.json"),"{\"code\":\"BROWSER-CREATE\"}");Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
        long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}assertThat(Files.exists(control.resolve("finish"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_tenant WHERE tenant_code='BROWSER-CREATE'",Integer.class)).isOne();long id=jdbc.queryForObject("SELECT id FROM plt_tenant WHERE tenant_code='BROWSER-CREATE'",Long.class);assertThat(count("iam_organization",id)).isOne();assertThat(count("iam_organization_closure",id)).isOne();assertThat(count("iam_user",id)).isOne();assertThat(count("iam_user_organization",id)).isOne();long site=jdbc.queryForObject("SELECT default_site_id FROM plt_tenant WHERE id=?",Long.class,id);assertThat(jdbc.queryForObject("SELECT organization_type FROM iam_organization WHERE id=? AND parent_id IS NULL",String.class,site)).isEqualTo("SITE");assertThat(jdbc.queryForObject("SELECT operation_mode FROM plt_tenant WHERE id=?",String.class,id)).isEqualTo("SINGLE_SITE");assertThat(jdbc.queryForObject("SELECT current_organization_id FROM iam_user_context WHERE tenant_id=?",Long.class,id)).isEqualTo(site);assertThat(jdbc.queryForObject("SELECT status FROM plt_tenant WHERE id=?",String.class,id)).isEqualTo("ACTIVE");assertThat(count("plt_subscription",id)).isOne();assertThat(jdbc.queryForObject("SELECT package_version_id FROM plt_subscription WHERE tenant_id=?",Long.class,id)).isEqualTo(880001L);
        var user=jdbc.queryForMap("SELECT password_hash,force_password_change FROM iam_user WHERE tenant_id=?",id);assertThat(((Number)user.get("force_password_change")).intValue()).isZero();assertThat(passwords.matches("Changed#Pass1234",(String)user.get("password_hash"))).isTrue();assertThat(passwords.matches("Site#Initial123",(String)user.get("password_hash"))).isFalse();for(String action:new String[]{"TENANT_CREATE","TENANT_INITIALIZE"})assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE object_id=? AND action=?",Integer.class,id,action)).isOne();
    }
}

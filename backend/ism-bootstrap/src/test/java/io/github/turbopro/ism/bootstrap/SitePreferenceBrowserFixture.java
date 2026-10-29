package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class SitePreferenceBrowserFixture extends SitePreferenceSupport {
    @LocalServerPort int port;
    @Test void serveAndVerifyPersonalScope()throws Exception{
        Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();var f=fixture();
        var before=jdbc.queryForList("SELECT * FROM iam_user_context WHERE tenant_id=? ORDER BY user_id",f.tenant());
        Files.writeString(control.resolve("fixture.json"),"{\"code\":\""+f.code()+"\",\"first\":\""+f.first()+"\",\"second\":\""+f.second()+"\"}");
        Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
        long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();
        while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(250);}
        assertThat(Files.exists(control.resolve("finish"))).isTrue();
        var row=jdbc.queryForMap("SELECT preferred_site_id,version FROM iam_user_site_preference WHERE tenant_id=? AND user_id=?",f.tenant(),f.admin());
        assertThat(row.get("preferred_site_id")).isNull();assertThat(row.get("version")).isEqualTo(3);
        assertThat(count("iam_user_site_preference",f.tenant())).isOne();
        assertThat(jdbc.queryForList("SELECT * FROM iam_user_context WHERE tenant_id=? ORDER BY user_id",f.tenant())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SITE_PREFERENCE_CHANGE'",Integer.class,f.tenant())).isEqualTo(3);
    }
}

package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class SiteContextBrowserFixture extends SitePreferenceSupport {
    @LocalServerPort int port;
    @Test void serveAndRevokeOnlyIsolatedFixtureScope()throws Exception{
        Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();var f=fixture();
        var before=jdbc.queryForList("SELECT * FROM iam_user_context WHERE tenant_id=? ORDER BY user_id",f.tenant());
        Files.writeString(control.resolve("fixture.json"),"{\"code\":\""+f.code()+"\",\"headquarters\":\""+f.headquarters()+"\"}");
        Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
        long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();boolean revoked=false;
        while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){
            assertThat(Files.exists(control.resolve("abort"))).isFalse();
            if(!revoked&&Files.exists(control.resolve("revoke"))){narrow(f,"NONE",0);revoked=true;Files.writeString(control.resolve("revoked"),"done");}
            Thread.sleep(100);
        }
        assertThat(Files.exists(control.resolve("finish"))).isTrue();assertThat(revoked).isTrue();
        assertThat(count("iam_user_site_preference",f.tenant())).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM iam_user_context WHERE tenant_id=? ORDER BY user_id",f.tenant())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SITE_CONTEXT_ISSUE'",Integer.class,f.tenant())).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT after_summary FROM sys_audit_event WHERE tenant_id=? AND action='SITE_CONTEXT_ISSUE'",String.class,f.tenant()).toString()).doesNotContain("contextToken","ath");
    }
}

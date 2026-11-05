package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class PlatformBrandingBrowserFixture extends PlatformBrandingIT {
 @LocalServerPort int port;
 @Test void servePlatformBranding()throws Exception{Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();consoleUsers();var f=fixture();tenantBrand(f);Files.writeString(control.resolve("fixture.json"),"{}");Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}assertThat(Files.exists(control.resolve("finish"))).isTrue();assertThat(jdbc.queryForObject("SELECT system_name FROM plt_branding WHERE id=1",String.class)).isEqualTo("制造化工协同平台");assertThat(jdbc.queryForObject("SELECT version FROM plt_branding WHERE id=1",Integer.class)).isOne();assertThat(jdbc.queryForObject("SELECT setting_value FROM cfg_tenant_setting WHERE tenant_id=?",String.class,f.tenant())).isEqualTo("租户自己的品牌");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE action='PLATFORM_BRANDING_UPDATE'",Integer.class)).isOne();Files.writeString(Path.of("../../output/playwright/platform-branding/browser-database.txt"),"Real Console UI/API save and exact replay: platform version1, one audit; tenant brand unchanged. Anonymous branding after logout verified. Controlled local image fixture only; no external image-site validation.\n");}
}

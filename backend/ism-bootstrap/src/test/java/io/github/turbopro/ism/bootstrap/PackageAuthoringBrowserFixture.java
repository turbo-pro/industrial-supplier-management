package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class PackageAuthoringBrowserFixture extends SiteInitializationSupport {
 @LocalServerPort int port;
 @Test void servePackageAuthoringAndVerifyPersistedPublication()throws Exception{
  Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();consoleUsers();Files.writeString(control.resolve("fixture.json"),"{}");Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
  long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}assertThat(Files.exists(control.resolve("finish"))).isTrue();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_package WHERE package_code='BROWSER_PLAN'",Integer.class)).isOne();long p=jdbc.queryForObject("SELECT id FROM plt_package WHERE package_code='BROWSER_PLAN'",Long.class);assertThat(jdbc.queryForObject("SELECT status FROM plt_package WHERE id=?",String.class,p)).isEqualTo("ACTIVE");long v=jdbc.queryForObject("SELECT id FROM plt_package_version WHERE package_id=?",Long.class,p);assertThat(jdbc.queryForObject("SELECT status FROM plt_package_version WHERE id=?",String.class,v)).isEqualTo("PUBLISHED");assertThat(jdbc.queryForObject("SELECT version FROM plt_package_version WHERE id=?",Integer.class,v)).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT JSON_EXTRACT(pm.quota_json,'$.SUPPLIER_COUNT') FROM plt_package_module pm JOIN plt_module m ON m.id=pm.module_id WHERE pm.package_version_id=? AND m.module_code='SUPPLIER' AND enabled=1",String.class,v)).isEqualTo("150");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_tenant WHERE tenant_code='PLAN-TENANT' AND package_version_id=?",Integer.class,v)).isOne();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_subscription s JOIN plt_tenant t ON t.id=s.tenant_id WHERE t.tenant_code='PLAN-TENANT' AND s.package_version_id=?",Integer.class,v)).isOne();
  for(String action:new String[]{"PACKAGE_CREATE","PACKAGE_VERSION_CREATE","PACKAGE_VERSION_UPDATE","PACKAGE_VERSION_PUBLISH"})assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE object_id=? AND action=?",Integer.class,action.equals("PACKAGE_CREATE")?p:v,action)).isOne();
  Files.writeString(Path.of("../../output/playwright/package-authoring/browser-database.txt"),"One package, one version, one edited grant, one tenant subscription and one audit per action verified in isolated MySQL.\n");
 }
}

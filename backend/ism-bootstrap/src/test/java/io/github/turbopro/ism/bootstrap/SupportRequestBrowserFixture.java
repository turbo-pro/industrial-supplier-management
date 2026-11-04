package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class SupportRequestBrowserFixture extends SiteProjectSupport {
 @LocalServerPort int port;
 @Test void serveIndependentReviewAndVerifyNoTenantBusinessGrants()throws Exception{
  Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();var f=fixture();consoleUsers();long users=count("iam_user",f.tenant()),roles=count("iam_user_role",f.tenant());
  Files.writeString(control.resolve("fixture.json"),"{\"tenantCode\":\""+f.code()+"\",\"tenantId\":\""+f.tenant()+"\"}");Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
  long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}assertThat(Files.exists(control.resolve("finish"))).isTrue();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_support_request WHERE target_tenant_id=?",Integer.class,f.tenant())).isOne();long id=jdbc.queryForObject("SELECT id FROM plt_support_request WHERE target_tenant_id=?",Long.class,f.tenant());assertThat(jdbc.queryForObject("SELECT status FROM plt_support_request WHERE id=?",String.class,id)).isEqualTo("CANCELLED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_support_approval WHERE request_id=? AND operator_id=790002 AND reviewer_id=790001 AND decision='APPROVE'",Integer.class,id)).isOne();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_support_request_event WHERE request_id=?",Integer.class,id)).isEqualTo(4);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE object_id=? AND action LIKE 'SUPPORT_REQUEST_%'",Integer.class,id)).isEqualTo(4);assertThat(count("iam_user",f.tenant())).isEqualTo(users);assertThat(count("iam_user_role",f.tenant())).isEqualTo(roles);assertThat(count("sup_supplier",f.tenant())).isZero();
  Files.writeString(Path.of("../../output/playwright/support-requests/browser-database.txt"),"One request, one independent platform approval, four ordered events/audits and owner cancellation. Tenant users/role grants unchanged; no supplier or tenant access token created by this feature.\n");
 }
}

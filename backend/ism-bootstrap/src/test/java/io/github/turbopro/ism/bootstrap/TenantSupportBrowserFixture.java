package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TenantSupportBrowserFixture extends TenantSupportIT {
 @LocalServerPort int port;
 // Failsafe runs declared/inherited tests too; driver uses method filter for this fixture.
 @Test void serveTenantConsentAndVerifyNoBusinessSession()throws Exception{
  Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();var f=fixture();String id=approved(f);long users=count("iam_user",f.tenant()),grants=count("iam_user_role",f.tenant());
  Files.writeString(control.resolve("fixture.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("tenantCode",f.code(),"id",id)));Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
  long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}assertThat(Files.exists(control.resolve("finish"))).isTrue();
  assertThat(jdbc.queryForObject("SELECT status FROM plt_support_tenant_decision WHERE request_id=?",String.class,id)).isEqualTo("REVOKED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE object_id=? AND tenant_id=? AND action IN ('TENANT_SUPPORT_APPROVE','TENANT_SUPPORT_REVOKE')",Integer.class,id,f.tenant())).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT enabled FROM plt_tenant_support_policy WHERE tenant_id=?",Boolean.class,f.tenant())).isTrue();assertThat(count("iam_user",f.tenant())).isEqualTo(users);assertThat(count("iam_user_role",f.tenant())).isEqualTo(grants);assertThat(count("sup_supplier",f.tenant())).isZero();
  Files.writeString(Path.of("../../output/playwright/tenant-support/browser-database.txt"),"Tenant policy, explicit approval, fixed-request replay after dropped response, Console bounded intersection with session disabled, tenant revoke and Console denial verified. Tenant audit, single durable decision, unchanged users/role grants and zero suppliers verified.\n");
 }
}

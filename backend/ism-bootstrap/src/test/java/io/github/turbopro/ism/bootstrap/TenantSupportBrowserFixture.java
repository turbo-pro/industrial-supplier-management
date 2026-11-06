package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class TenantSupportBrowserFixture extends TenantSupportIT {
 @LocalServerPort int port;
 boolean withSession(){return false;}
 boolean withWorkspace(){return false;}
 // Failsafe runs declared/inherited tests too; driver uses method filter for this fixture.
 @Test void serveTenantConsentAndVerifyNoBusinessSession()throws Exception{
  Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();var f=fixture();String id=approved(f);long users=count("iam_user",f.tenant()),grants=count("iam_user_role",f.tenant());
  long supplierId=f.tenant()+96000;
  if(withSession())jdbc.update("""
    INSERT INTO sup_supplier (id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,remark,created_by,updated_by)
    VALUES (?,?,?,'SUP-BROWSER','浏览器验收供应商','MANUFACTURER','PRIVATE',?,?)
    """,supplierId,f.tenant(),f.first(),f.admin(),f.admin());
  Files.writeString(control.resolve("fixture.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("tenantCode",f.code(),"id",id,"workspace",withWorkspace(),"supplierId",withSession()?Long.toString(supplierId):"")));Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
  long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}assertThat(Files.exists(control.resolve("finish"))).isTrue();
  assertThat(jdbc.queryForObject("SELECT status FROM plt_support_tenant_decision WHERE request_id=?",String.class,id)).isEqualTo("REVOKED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE object_id=? AND tenant_id=? AND action IN ('TENANT_SUPPORT_APPROVE','TENANT_SUPPORT_REVOKE')",Integer.class,id,f.tenant())).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT enabled FROM plt_tenant_support_policy WHERE tenant_id=?",Boolean.class,f.tenant())).isTrue();assertThat(count("iam_user",f.tenant())).isEqualTo(users);assertThat(count("iam_user_role",f.tenant())).isEqualTo(grants);assertThat(count("sup_supplier",f.tenant())).isEqualTo(withSession()?1:0);
  if(withSession()){
   int expected=withWorkspace()?2:1;
   assertThat(jdbc.queryForList("SELECT status FROM plt_support_session WHERE request_id=?",String.class,id))
     .hasSize(expected).containsOnly("REVOKED");
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE action='SUPPORT_SESSION_REVOKE' AND object_id IN (SELECT id FROM plt_support_session WHERE request_id=?)",Integer.class,id)).isEqualTo(expected);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE action='SUPPORT_SUPPLIER_READ' AND object_id=?",Integer.class,supplierId)).isEqualTo(expected);
   if(withWorkspace()){
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_support_session WHERE request_id=? AND access_digest IS NOT NULL",Integer.class,id)).isZero();
    assertThat(jdbc.queryForObject("""
     SELECT COUNT(*) FROM sys_audit_event WHERE action='SUPPORT_SUPPLIER_READ' AND object_id=?
       AND JSON_UNQUOTE(JSON_EXTRACT(after_summary,'$.surface'))='SUPPORT_WORKSPACE'
     """,Integer.class,supplierId)).isEqualTo(expected);
   }
   return;
  }
  Files.writeString(Path.of("../../output/playwright/tenant-support/browser-database.txt"),"Tenant policy, explicit approval, fixed-request replay after dropped response, Console bounded intersection with session disabled, tenant revoke and Console denial verified. Tenant audit, single durable decision, unchanged users/role grants and zero suppliers verified.\n");
 }
}

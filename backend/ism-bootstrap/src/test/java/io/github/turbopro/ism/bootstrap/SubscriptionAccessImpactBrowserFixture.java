package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SubscriptionAccessImpactBrowserFixture extends SubscriptionAccessImpactIT {
 @LocalServerPort int port;
 @Test void serveMenuDowngradeAndRestoration()throws Exception{
  Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();var f=fixture();limit(f,10);consoleUsers();String down=target(f,2,false),up=target(f,3,true);
  var menus=jdbc.queryForList("SELECT * FROM iam_role_menu WHERE tenant_id=? ORDER BY role_id,menu_id",f.tenant());var permissions=jdbc.queryForList("SELECT * FROM iam_role_permission WHERE tenant_id=? ORDER BY role_id,permission_id",f.tenant());int users=count("iam_user",f.tenant()),orgs=count("iam_organization",f.tenant());
  Files.writeString(control.resolve("fixture.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("tenantCode",f.code(),"tenantId",Long.toString(f.tenant()),"down",down,"up",up)));Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
  long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}assertThat(Files.exists(control.resolve("finish"))).isTrue();
  assertThat(jdbc.queryForList("SELECT * FROM iam_role_menu WHERE tenant_id=? ORDER BY role_id,menu_id",f.tenant())).isEqualTo(menus);assertThat(jdbc.queryForList("SELECT * FROM iam_role_permission WHERE tenant_id=? ORDER BY role_id,permission_id",f.tenant())).isEqualTo(permissions);assertThat(count("iam_user",f.tenant())).isEqualTo(users);assertThat(count("iam_organization",f.tenant())).isEqualTo(orgs);assertThat(plans.subscriptions(f.tenant(),0,20).currentPackageVersionId()).isEqualTo(up);assertThat(count("plt_subscription",f.tenant())).isEqualTo(3);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE object_id=? AND action='SUBSCRIPTION_ASSIGN'",Integer.class,f.tenant())).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='25'",Integer.class)).isEqualTo(1394665689);
  Files.writeString(Path.of("../../output/playwright/subscription-access/browser-database.txt"),"Real Console catalog preview/downgrade/restore and Admin menu disappearance/restoration with same JWT passed. Tenant business API denied while module disabled; readonly role never gains permission. Two assignments, three subscription rows, unchanged role-menu/role-permission rows, users and organizations, and V25 checksum verified. No claim of retained supplier data: fixture has no supplier records; quota downgrade policy remains unchanged.\n");
 }
}

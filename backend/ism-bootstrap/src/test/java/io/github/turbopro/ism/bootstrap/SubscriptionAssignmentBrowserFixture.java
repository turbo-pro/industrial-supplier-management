package io.github.turbopro.ism.bootstrap;
import io.github.turbopro.ism.platform.packageplan.PackagePlanModels;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SubscriptionAssignmentBrowserFixture extends SupplierQuotaSupport {
 @LocalServerPort int port;
 @Test void serveAssignmentRecoverySchedulingAndCancellation()throws Exception{
  Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();var f=fixture();limit(f,1);ok(create(f,tenantLogin(f,"pref-admin"),"ASSIGN-FIRST"));consoleUsers();
  long p=jdbc.queryForObject("SELECT package_id FROM plt_package_version v JOIN plt_tenant t ON t.package_version_id=v.id WHERE t.id=?",Long.class,f.tenant());var target=plans.createVersion(p,new PackagePlanModels.CreateVersion(2,"分配恢复",null,List.of(new PackagePlanModels.ModuleGrant("SUPPLIER",true,Map.of("SUPPLIER_COUNT",2L)))));plans.publish(Long.parseLong(target.id()),0,790001);
  Files.writeString(control.resolve("fixture.json"),"{\"tenantId\":\""+f.tenant()+"\",\"tenantCode\":\""+f.code()+"\",\"targetId\":\""+target.id()+"\",\"organizationId\":\""+f.headquarters()+"\"}");Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
  long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}assertThat(Files.exists(control.resolve("finish"))).isTrue();
  assertThat(count("sup_supplier",f.tenant())).isEqualTo(2);assertThat(count("plt_subscription",f.tenant())).isEqualTo(4);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_subscription WHERE tenant_id=? AND status='TERMINATED'",Integer.class,f.tenant())).isOne();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_subscription WHERE tenant_id=? AND status='CANCELLED'",Integer.class,f.tenant())).isOne();assertThat(plans.subscriptions(f.tenant(),0,20).currentPackageVersionId()).isEqualTo(target.id());assertThat(usage(f).path("limit").asInt()).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE object_id=? AND action='SUBSCRIPTION_ASSIGN'",Integer.class,f.tenant())).isEqualTo(3);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=0 AND action='SUBSCRIPTION_CANCEL' AND JSON_UNQUOTE(JSON_EXTRACT(before_summary,'$.tenantId'))=?",Integer.class,Long.toString(f.tenant()))).isOne();
  Files.writeString(Path.of("../../output/playwright/subscription-assignment/browser-database.txt"),"Four subscription rows: initial, competing future replaced, UI immediate assigned once after unknown replay, UI future cancelled. Two suppliers, current target limit=2, three assignment audits verified.\n");
 }
}

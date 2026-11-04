package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SupplierQuotaBrowserFixture extends SupplierQuotaSupport {
    @LocalServerPort int port;
    @Test void serveQuotaInspectionAndVerifyCapacityAndCounter()throws Exception{
        Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();var f=fixture();limit(f,2);ok(create(f,tenantLogin(f,"pref-admin"),"BROWSER-FIRST"));consoleUsers();
        Files.writeString(control.resolve("fixture.json"),"{\"tenantCode\":\""+f.code()+"\",\"organizationId\":\""+f.headquarters()+"\",\"tenantId\":\""+f.tenant()+"\"}");Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
        long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}assertThat(Files.exists(control.resolve("finish"))).isTrue();
        assertThat(count("sup_supplier",f.tenant())).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT used_value FROM plt_quota_usage WHERE tenant_id=? AND quota_code='SUPPLIER_COUNT'",Long.class,f.tenant())).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_CREATE'",Integer.class,f.tenant())).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_supplier WHERE tenant_id=? AND supplier_code='BROWSER-OVER'",Integer.class,f.tenant())).isZero();
        // Restore capacity through the actual Console APIs, then verify creation resumes.
        String platform=login(true);long packageId=jdbc.queryForObject("SELECT package_id FROM plt_package_version v JOIN plt_tenant t ON t.package_version_id=v.id WHERE t.id=?",Long.class,f.tenant());
        var version=ok(request("POST","/api/console/packages/"+packageId+"/versions",platform,UUID.randomUUID().toString(),Map.of("versionNo",2,"name","容量恢复版本","modules",List.of(Map.of("moduleCode","SUPPLIER","enabled",true,"quotas",Map.of("SUPPLIER_COUNT",3))))));String versionId=version.path("id").asText();
        ok(request("POST","/api/console/package-versions/"+versionId+"/publish",platform,UUID.randomUUID().toString(),Map.of("version",0)));
        ok(request("POST","/api/console/tenants/"+f.tenant()+"/subscription",platform,UUID.randomUUID().toString(),Map.of("packageVersionId",versionId,"effectiveFrom",LocalDateTime.now(ZoneOffset.UTC).toString(),"exceptions",Map.of())));
        ok(create(f,tenantLogin(f,"pref-admin"),"BROWSER-AFTER-UPGRADE"));assertThat(count("sup_supplier",f.tenant())).isEqualTo(3);assertThat(usage(f).path("limit").asLong()).isEqualTo(3);assertThat(usage(f).path("used").asLong()).isEqualTo(3);assertThat(jdbc.queryForObject("SELECT used_value FROM plt_quota_usage WHERE tenant_id=?",Long.class,f.tenant())).isEqualTo(3);
        Files.writeString(Path.of("../../output/playwright/supplier-quota/browser-database.txt"),"Browser stage: two actual HTTP-created suppliers, usage=2, two audits and no over-quota supplier. Then actual Console create-version/publish/assign restored capacity to 3 and a third supplier was created; final live and ledger usage=3, isolated MySQL verified.\n");
    }
}

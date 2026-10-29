package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class SiteProjectBrowserFixture extends SiteProjectSupport {
    @LocalServerPort int port;
    @Test void serveProjectWorkspaceAndAssertFinalSiteOwnership()throws Exception{
        Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();var f=fixture();long supplier=f.tenant()+90000;
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,status,created_by,updated_by) VALUES(?,?,?,'SITE-SUP','场站供应商','SERVICE_PROVIDER','ACTIVE',?,?)",supplier,f.tenant(),f.headquarters(),f.admin(),f.admin());
        Files.writeString(control.resolve("fixture.json"),"{\"code\":\""+f.code()+"\",\"first\":\""+f.first()+"\",\"second\":\""+f.second()+"\"}");Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
        long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}
        assertThat(Files.exists(control.resolve("finish"))).isTrue();assertThat(count("prj_project",f.tenant())).isOne();
        var row=jdbc.queryForMap("SELECT organization_id,project_name,status,version FROM prj_project WHERE tenant_id=?",f.tenant());assertThat(((Number)row.get("organization_id")).longValue()).isEqualTo(f.first());assertThat(row.get("project_name")).isEqualTo("浏览器检修更新");assertThat(row.get("status")).isEqualTo("ACTIVE");assertThat(((Number)row.get("version")).intValue()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT organization_id FROM sup_supplier WHERE id=?",Long.class,supplier)).isEqualTo(f.headquarters());assertThat(count("iam_user_site_preference",f.tenant())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action IN ('PROJECT_CREATE','PROJECT_STATUS')",Integer.class,f.tenant())).isEqualTo(2);
    }
}

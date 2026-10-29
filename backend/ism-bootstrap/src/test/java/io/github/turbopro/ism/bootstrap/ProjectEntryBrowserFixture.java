package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class ProjectEntryBrowserFixture extends SiteProjectSupport {
    @LocalServerPort int port;
    @Test void serveReadOnlyOrganizationAndRevokeDetailPermission()throws Exception{
        Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();var f=fixture();long supplier=f.tenant()+90000,history=supplier+1,siteProject=supplier+2;
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,status,created_by,updated_by) VALUES(?,?,?,'ENTRY-SUP','入口供应商','SERVICE_PROVIDER','ACTIVE',?,?)",supplier,f.tenant(),f.headquarters(),f.admin(),f.admin());
        for(long id:new long[]{history,siteProject})jdbc.update("INSERT INTO prj_project(id,tenant_id,organization_id,supplier_id,project_code,project_name,project_type,planned_start_date,planned_end_date,manager_id,created_by,updated_by) VALUES(?,?,?,?,?,?,'MAINTENANCE','2026-01-01','2026-12-31',?,?,?)",id,f.tenant(),id==history?f.headquarters():f.first(),supplier,id==history?"LEGACY-ENTRY":"SITE-ENTRY",id==history?"保留的历史项目":"场站现有项目",f.admin(),f.admin(),f.admin());
        var before=jdbc.queryForList("SELECT * FROM prj_project WHERE tenant_id=? ORDER BY id",f.tenant());
        Files.writeString(control.resolve("fixture.json"),"{\"code\":\""+f.code()+"\",\"history\":\""+history+"\",\"supplier\":\""+supplier+"\",\"admin\":\""+f.admin()+"\"}");Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
        long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();boolean revoked=false;
        while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();if(!revoked&&Files.exists(control.resolve("revoke"))){jdbc.update("DELETE FROM iam_role_permission WHERE tenant_id=? AND role_id=? AND permission_id IN(6315,3070)",f.tenant(),f.role());revoked=true;Files.writeString(control.resolve("revoked"),"done");}Thread.sleep(100);}
        assertThat(Files.exists(control.resolve("finish"))).isTrue();assertThat(revoked).isTrue();assertThat(jdbc.queryForList("SELECT * FROM prj_project WHERE tenant_id=? ORDER BY id",f.tenant())).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action LIKE 'PROJECT_%'",Integer.class,f.tenant())).isZero();assertThat(jdbc.queryForObject("SELECT operation_mode FROM plt_tenant WHERE id=?",String.class,f.tenant())).isEqualTo("MULTI_SITE");
    }
}

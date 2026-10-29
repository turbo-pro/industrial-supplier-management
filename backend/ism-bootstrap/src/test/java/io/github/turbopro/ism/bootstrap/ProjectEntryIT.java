package io.github.turbopro.ism.bootstrap;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.platform.tenant.TenantModels;
import io.github.turbopro.ism.project.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
/** Inherits the six actual site project lifecycle tests in the same isolated fixture context. */
class ProjectEntryIT extends SiteProjectIT {
    @Autowired ProjectService projects;
    void permission(Fixture f,String code,boolean enabled){if(enabled)jdbc.update("INSERT IGNORE INTO iam_role_permission(tenant_id,role_id,permission_id) SELECT ?,?,id FROM sys_permission WHERE permission_code=?",f.tenant(),f.role(),code);else jdbc.update("DELETE rp FROM iam_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.tenant_id=? AND rp.role_id=? AND p.permission_code=?",f.tenant(),f.role(),code);}
    @Test void legacyHttpAndApplicationWritesCannotBypassSiteContext(){
        var f=fixture();long s=supplier(f);String t=tenantLogin(f,"pref-admin");var body=project(f,s,"CANONICAL",0);
        assertThat(request("POST","/api/projects",t,null,body).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(count("prj_project",f.tenant())).isZero();
        try(var tenant=TenantContext.open(f.tenant(),f.admin())){assertThatThrownBy(()->projects.createProject(new ProjectModels.SaveProject("DIRECT","direct",Long.toString(s),null,ProjectModels.ProjectType.OTHER,null,java.time.LocalDate.now(),java.time.LocalDate.now(),Long.toString(f.admin()),null,null,0))).isInstanceOf(ApiException.class);}
        String a=context(t,f.first()),id=ok(call("POST","/api/site-projects",t,a,body)).path("id").asText();
        var before=jdbc.queryForMap("SELECT * FROM prj_project WHERE id=?",id);long audits=jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=?",Long.class,f.tenant());
        assertThat(request("PUT","/api/projects/"+id,t,null,body).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(request("POST","/api/projects/"+id+"/status",t,null,Map.of("status","ACTIVE","version",0)).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForMap("SELECT * FROM prj_project WHERE id=?",id)).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=?",Long.class,f.tenant())).isEqualTo(audits);
    }
    @Test void organizationSummaryDetailAndSiteRightsAreIndependent(){
        var f=fixture();long s=supplier(f);String t=tenantLogin(f,"pref-admin"),a=context(t,f.first()),id=ok(call("POST","/api/site-projects",t,a,project(f,s,"READ",0))).path("id").asText();
        permission(f,"project:overview:detail",false);assertThat(ok(request("GET","/api/projects",t,null,null)).path("total").asInt()).isOne();assertThat(request("GET","/api/projects/"+id,t,null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var access=ok(request("GET","/api/projects/access-mode",t,null,null));assertThat(access.path("siteContextRequired").asBoolean()).isTrue();assertThat(access.path("legacyWritesAllowed").asBoolean()).isFalse();assertThat(access.path("canViewOrganizationProjects").asBoolean()).isTrue();assertThat(access.path("canViewOrganizationProjectDetails").asBoolean()).isFalse();
        permission(f,"project:overview:detail",true);permission(f,"project:overview:view",false);assertThat(request("GET","/api/projects",t,null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);assertThat(request("GET","/api/projects/"+id,t,null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ok(call("GET","/api/site-projects/"+id,t,a,null)).path("id").asText()).isEqualTo(id);
        var query=Map.of("keyword","READ","types",List.of("PROJECT"),"statuses",List.of(),"page",0,"size",20);var hidden=ok(request("POST","/api/search",t,null,query));assertThat(hidden.path("searchedTypes").isArray()).isTrue();assertThat(hidden.path("searchedTypes").isEmpty()).isTrue();assertThat(hidden.path("items").isEmpty()).isTrue();
        permission(f,"project:overview:view",true);assertThat(ok(request("POST","/api/search",t,null,query)).path("items").size()).isOne();
        permission(f,"project:overview:view",true);scope(f,"NONE");assertThat(ok(request("GET","/api/projects",t,null,null)).path("total").asInt()).isZero();assertThat(request("GET","/api/projects/"+id,t,null,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    @Test void unclassifiedLegacyRemainsCompatibleWithoutRewritingHistory(){
        var f=fixture();long s=supplier(f);String t=tenantLogin(f,"pref-admin");jdbc.update("UPDATE plt_tenant SET operation_mode=NULL WHERE id=?",f.tenant());permission(f,"project:overview:view",false);permission(f,"project:overview:detail",false);
        var body=project(f,s,"LEGACY-HQ",0);String id=ok(request("POST","/api/projects",t,null,body)).path("id").asText();assertThat(ok(request("GET","/api/projects/"+id,t,null,null)).path("organizationId").asText()).isEqualTo(Long.toString(f.headquarters()));
        ok(request("PUT","/api/projects/"+id,t,null,body));ok(request("POST","/api/projects/"+id+"/status",t,null,Map.of("status","ACTIVE","version",1)));
        assertThat(ok(request("GET","/api/projects/access-mode",t,null,null)).path("legacyWritesAllowed").asBoolean()).isTrue();var before=jdbc.queryForMap("SELECT * FROM prj_project WHERE id=?",id);
        jdbc.update("UPDATE plt_tenant SET operation_mode='MULTI_SITE' WHERE id=?",f.tenant());assertThat(request("PUT","/api/projects/"+id,t,null,body).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(jdbc.queryForMap("SELECT * FROM prj_project WHERE id=?",id)).isEqualTo(before);
    }
    @Test void singleSiteInitializationUsesTheSameWriteBoundary(){
        long tenant=pending();tenants.initialize(tenant,new TenantModels.InitializeTenant("pref-admin","单场站管理员","Site#Initial123","","SINGLE_SITE","工厂","单场站工厂"));
        long admin=jdbc.queryForObject("SELECT id FROM iam_user WHERE tenant_id=?",Long.class,tenant),site=jdbc.queryForObject("SELECT default_site_id FROM plt_tenant WHERE id=?",Long.class,tenant),role=jdbc.queryForObject("SELECT id FROM iam_role WHERE tenant_id=? AND role_code='TENANT_ADMIN'",Long.class,tenant);
        jdbc.update("UPDATE iam_user SET force_password_change=0 WHERE tenant_id=?",tenant);var f=new Fixture(tenant,jdbc.queryForObject("SELECT tenant_code FROM plt_tenant WHERE id=?",String.class,tenant),admin,site,site,site,site,role,"");long s=supplier(f);String t=tenantLogin(f,"pref-admin");
        assertThat(request("POST","/api/projects",t,null,project(f,s,"SINGLE-OLD",0)).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(ok(call("POST","/api/site-projects",t,context(t,site),project(f,s,"SINGLE-SITE",0))).path("organizationId").asText()).isEqualTo(Long.toString(site));
    }
    @Test void unknownModeNeverFallsBackToLegacyWrites(){
        var f=fixture();long s=supplier(f);String t=tenantLogin(f,"pref-admin"),a=context(t,f.first());jdbc.update("UPDATE plt_tenant SET operation_mode='UNKNOWN' WHERE id=?",f.tenant());
        assertThat(request("GET","/api/projects/access-mode",t,null,null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(request("POST","/api/projects",t,null,project(f,s,"UNKNOWN-OLD",0)).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(call("POST","/api/site-projects",t,a,project(f,s,"UNKNOWN-SITE",0)).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(count("prj_project",f.tenant())).isZero();
    }
}

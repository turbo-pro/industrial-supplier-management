package io.github.turbopro.ism.bootstrap;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SiteProjectIT extends SiteProjectSupport {
    long supplier(Fixture f){long id=f.tenant()+90000;jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,status,created_by,updated_by) VALUES(?,?,?,'SITE-SUP','场站供应商','SERVICE_PROVIDER','ACTIVE',?,?)",id,f.tenant(),f.headquarters(),f.admin(),f.admin());return id;}
    Map<String,Object> project(Fixture f,long supplier,String code,int version){return new HashMap<>(Map.of("projectCode",code,"name","现场检修","supplierId",Long.toString(supplier),"type","MAINTENANCE","plannedStartDate","2026-10-01","plannedEndDate","2026-12-01","managerId",Long.toString(f.admin()),"version",version));}
    String context(String bearer,long site){var r=request("POST","/api/site-context",bearer,null,Map.of("siteId",Long.toString(site)));assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody().path("data").path("contextToken").asText();}
    ResponseEntity<JsonNode> call(String method,String path,String bearer,String context,Object body){var h=new HttpHeaders();h.setBearerAuth(bearer);if(context!=null)h.set("X-Site-Context",context);return http.exchange(path,HttpMethod.valueOf(method),new HttpEntity<>(body,h),JsonNode.class);}
    JsonNode ok(ResponseEntity<JsonNode> r){assertThat(r.getStatusCode()).withFailMessage("%s",r.getBody()).isEqualTo(HttpStatus.OK);return r.getBody().path("data");}
    void scope(Fixture f,String type){jdbc.update("UPDATE iam_role_data_scope SET scope_type=? WHERE tenant_id=? AND role_id=? AND resource_code='project'",type,f.tenant(),f.role());}
    @Test void fullLifecycleHasSqlSiteIntersectionAndPreservesLegacyOwnership(){
        var f=fixture();long supplier=supplier(f);String t=tenantLogin(f,"pref-admin"),a=context(t,f.first()),b=context(t,f.second());
        assertThat(ok(request("GET","/api/suppliers?status=ACTIVE",t,null,null)).path("items").get(0).path("type").asText()).isEqualTo("SERVICE_PROVIDER");
        var p=ok(call("POST","/api/site-projects",t,a,project(f,supplier,"SITE-A",0)));String id=p.path("id").asText();assertThat(p.path("organizationId").asText()).isEqualTo(Long.toString(f.first()));
        ok(call("POST","/api/site-projects",t,b,project(f,supplier,"SITE-B",0)));
        assertThat(ok(call("GET","/api/site-projects?size=1&keyword=SITE-",t,a,null)).path("total").asInt()).isOne();
        assertThat(ok(call("GET","/api/site-projects?page=1&size=1",t,a,null)).path("items").isEmpty()).isTrue();
        assertThat(ok(call("GET","/api/site-projects?keyword=SITE-B",t,a,null)).path("total").asInt()).isZero();
        assertThat(call("GET","/api/site-projects/"+id,t,b,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call("PUT","/api/site-projects/"+id,t,b,project(f,supplier,"SITE-A",0)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call("POST","/api/site-projects/"+id+"/status",t,b,Map.of("status","ACTIVE","version",0)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var edited=project(f,supplier,"SITE-A",0);edited.put("name","检修更新");assertThat(ok(call("PUT","/api/site-projects/"+id,t,a,edited)).path("version").asInt()).isOne();
        assertThat(call("PUT","/api/site-projects/"+id,t,a,edited).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ok(call("POST","/api/site-projects/"+id+"/status",t,a,Map.of("status","ACTIVE","version",1))).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(call("POST","/api/site-projects/"+id+"/status",t,a,Map.of("status","SUSPENDED","version",2)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ok(call("POST","/api/site-projects/"+id+"/status",t,a,Map.of("status","COMPLETED","version",2)));
        assertThat(jdbc.queryForObject("SELECT organization_id FROM sup_supplier WHERE id=?",Long.class,supplier)).isEqualTo(f.headquarters());
        assertThat(ok(request("GET","/api/projects",t,null,null)).path("total").asInt()).isEqualTo(2);
        assertThat(call("GET","/api/projects",t,a,null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
    @Test void projectScopesAndActionsRemainAnIndependentIntersection(){
        var f=fixture();long s=supplier(f);String t=tenantLogin(f,"pref-admin"),a=context(t,f.first()),b=context(t,f.second());String id=ok(call("POST","/api/site-projects",t,a,project(f,s,"PERMISSION",0))).path("id").asText();
        scope(f,"NONE");assertThat(ok(call("GET","/api/site-projects",t,a,null)).path("total").asInt()).isZero();assertThat(call("GET","/api/site-projects/"+id,t,a,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);assertThat(call("POST","/api/site-projects",t,a,project(f,s,"DENIED",0)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        scope(f,"ORGANIZATION_SET");jdbc.update("INSERT INTO iam_role_data_scope_organization(tenant_id,role_id,resource_code,organization_id) VALUES(?,?,'project',?)",f.tenant(),f.role(),f.second());assertThat(ok(call("GET","/api/site-projects",t,a,null)).path("total").asInt()).isZero();
        scope(f,"OWNED");assertThat(ok(call("GET","/api/site-projects",t,a,null)).path("total").asInt()).isOne();assertThat(ok(call("GET","/api/site-projects",t,b,null)).path("total").asInt()).isZero();
        scope(f,"CREATED");assertThat(ok(call("GET","/api/site-projects",t,a,null)).path("total").asInt()).isOne();
        scope(f,"TENANT_ALL");jdbc.update("DELETE rp FROM iam_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.tenant_id=? AND rp.role_id=? AND p.permission_code='project:manage'",f.tenant(),f.role());
        assertThat(ok(call("GET","/api/site-projects/access",t,a,null)).path("canManage").asBoolean()).isFalse();assertThat(call("POST","/api/site-projects",t,a,project(f,s,"NO-ACTION",0)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void missingForgedCrossLoginTenantAndRevokedContextsCannotWrite(){
        var f=fixture();long s=supplier(f);String t=tenantLogin(f,"pref-admin"),a=context(t,f.first());
        for(String method:List.of("GET","POST")){assertThat(call(method,"/api/site-projects",t,null,"POST".equals(method)?project(f,s,"MISSING",0):null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);}
        assertThat(call("POST","/api/site-projects",t,"forged",project(f,s,"FORGED",0)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("GET","/api/site-projects",tenantLogin(f,"pref-admin"),a,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var foreign=fixture();assertThat(call("POST","/api/site-projects",tenantLogin(foreign,"pref-admin"),a,project(f,s,"FOREIGN",0)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        narrow(f,"NONE",0);assertThat(call("POST","/api/site-projects",t,a,project(f,s,"REVOKED",0)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);assertThat(count("prj_project",f.tenant())).isZero();
    }
    @Test void auditFailureRollsBackAndSupplierRestrictionsStillApply()throws Exception{
        var f=fixture();long s=supplier(f);String t=tenantLogin(f,"pref-admin"),a=context(t,f.first());
        trigger("CREATE TRIGGER site_project_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.action='PROJECT_CREATE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic audit failure'; END IF; END");
        try{assertThat(call("POST","/api/site-projects",t,a,project(f,s,"ROLLBACK",0)).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);assertThat(count("prj_project",f.tenant())).isZero();}finally{trigger("DROP TRIGGER site_project_audit_failure");}
        jdbc.update("UPDATE sup_supplier SET status='DISABLED' WHERE id=?",s);assertThat(call("POST","/api/site-projects",t,a,project(f,s,"INACTIVE",0)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void supplierVisibilityAndSubscriptionCannotBeReplacedBySiteAuthorization(){
        var f=fixture();long s=supplier(f);String t=tenantLogin(f,"pref-admin"),a=context(t,f.first());
        jdbc.update("UPDATE iam_role_data_scope SET scope_type='NONE' WHERE tenant_id=? AND role_id=? AND resource_code='supplier:master'",f.tenant(),f.role());
        assertThat(call("POST","/api/site-projects",t,a,project(f,s,"HIDDEN-SUP",0)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        jdbc.update("UPDATE iam_role_data_scope SET scope_type='TENANT_ALL' WHERE tenant_id=? AND role_id=? AND resource_code='supplier:master'",f.tenant(),f.role());
        jdbc.update("UPDATE plt_package_module pm JOIN plt_tenant t ON t.package_version_id=pm.package_version_id JOIN plt_module m ON m.id=pm.module_id SET pm.enabled=0 WHERE t.id=? AND m.module_code='PROJECT'",f.tenant());
        assertThat(call("GET","/api/site-projects",t,a,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);assertThat(count("prj_project",f.tenant())).isZero();
    }
    @Test void ownedScopeCannotTransferOwnershipAndContractLookupRespectsItsOwnScope(){
        var f=fixture();long s=supplier(f);String t=tenantLogin(f,"pref-admin"),a=context(t,f.first());scope(f,"OWNED");
        var body=project(f,s,"OWNER",0);body.put("managerId",f.reader());assertThat(call("POST","/api/site-projects",t,a,body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        body.put("managerId",Long.toString(f.admin()));String id=ok(call("POST","/api/site-projects",t,a,body)).path("id").asText();body.put("managerId",f.reader());assertThat(call("PUT","/api/site-projects/"+id,t,a,body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        scope(f,"TENANT_ALL");long contract=s+1;jdbc.update("INSERT INTO prj_contract(id,tenant_id,organization_id,supplier_id,contract_no,contract_name,contract_type,amount,currency,start_date,end_date,owner_id,file_id,status,created_by,updated_by) VALUES(?,?,?,?,'SITE-CONTRACT','合同','SERVICE',1,'CNY','2026-01-01','2026-12-31',?,1,'ACTIVE',?,?)",contract,f.tenant(),f.headquarters(),s,f.admin(),f.admin(),f.admin());
        var linked=project(f,s,"LINKED",0);linked.put("contractId",Long.toString(contract));jdbc.update("UPDATE iam_role_data_scope SET scope_type='NONE' WHERE tenant_id=? AND role_id=? AND resource_code='contract'",f.tenant(),f.role());assertThat(call("POST","/api/site-projects",t,a,linked).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        jdbc.update("UPDATE iam_role_data_scope SET scope_type='TENANT_ALL' WHERE tenant_id=? AND role_id=? AND resource_code='contract'",f.tenant(),f.role());assertThat(ok(call("POST","/api/site-projects",t,a,linked)).path("contractNo").asText()).isEqualTo("SITE-CONTRACT");
    }

}

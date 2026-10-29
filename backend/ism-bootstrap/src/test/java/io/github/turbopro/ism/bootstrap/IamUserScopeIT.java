package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.iam.access.AccessService;
import io.github.turbopro.ism.iam.organization.*;
import io.github.turbopro.ism.platform.tenant.TenantService;
import io.github.turbopro.ism.platform.packageplan.PackagePlanService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false"})
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class IamUserScopeIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("user_scope").withUsername("ism").withPassword("ism");
    static final Path STORAGE=Path.of(System.getProperty("java.io.tmpdir"),"ism-user-scope-"+UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("ism.storage.local-root",STORAGE::toString);}
    @AfterAll static void cleanup()throws Exception{if(Files.exists(STORAGE))try(var paths=Files.walk(STORAGE)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    @Autowired JdbcTemplate jdbc;@Autowired TestRestTemplate http;@Autowired TenantService tenants;@Autowired PackagePlanService plans;@Autowired AccessService access;@Autowired OrganizationService organizations;
    record Fixture(IamBatchRoleData.Fixture f,long orgA,String orgB,long adminRole){}
    Fixture fixture(){
        var f=IamBatchRoleData.seed(jdbc,tenants,plans,access);
        long root=jdbc.queryForObject("SELECT organization_id FROM iam_user_organization WHERE tenant_id=? AND user_id=? AND is_primary=1",Long.class,f.tenant(),f.admin());
        long adminRole=jdbc.queryForObject("SELECT id FROM iam_role WHERE tenant_id=? AND role_code='TENANT_ADMIN'",Long.class,f.tenant());
        String orgB;long orgA;
        try(var context=TenantContext.open(f.tenant(),f.admin())){
            orgA=Long.parseLong(organizations.create(new OrganizationModels.CreateOrganization(Long.toString(root),"SITE-A","第一场站","SITE",0)).id());
            orgB=organizations.create(new OrganizationModels.CreateOrganization(Long.toString(root),"SITE-B","第二场站","SITE",0)).id();
        }
        for(String user:List.of(Long.toString(f.admin()),f.first(),f.unchanged()))move(f,user,Long.toString(orgA));
        move(f,f.second(),orgB);
        return new Fixture(f,orgA,orgB,adminRole);
    }
    void move(IamBatchRoleData.Fixture f,String user,String org){
        jdbc.update("UPDATE iam_user_organization SET organization_id=? WHERE tenant_id=? AND user_id=? AND is_primary=1",Long.parseLong(org),f.tenant(),Long.parseLong(user));
        jdbc.update("UPDATE iam_user_context SET current_organization_id=? WHERE tenant_id=? AND user_id=?",Long.parseLong(org),f.tenant(),Long.parseLong(user));
    }
    void narrow(Fixture v){
        jdbc.update("UPDATE iam_role_data_scope SET scope_type='ORGANIZATION_SET' WHERE tenant_id=? AND role_id=? AND resource_code='iam:user'",v.f().tenant(),v.adminRole());
        jdbc.update("INSERT IGNORE INTO iam_role_data_scope_organization(tenant_id,role_id,resource_code,organization_id) VALUES(?,?,'iam:user',?)",v.f().tenant(),v.adminRole(),v.orgA());
    }
    String login(Fixture v,String username){var r=http.postForEntity("/api/auth/login",Map.of("tenantCode",v.f().code(),"username",username,"password",IamBatchRoleData.PASSWORD,"deviceId","user-scope"),JsonNode.class);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody().path("data").path("accessToken").asText();}
    ResponseEntity<JsonNode> send(String method,String path,String token,String key,Object body){var h=new HttpHeaders();h.setBearerAuth(token);if(key!=null)h.set("Idempotency-Key",key);return http.exchange(path,HttpMethod.valueOf(method),new HttpEntity<>(body,h),JsonNode.class);}
    Map<String,Object> target(String id,int version){return Map.of("id",id,"version",version);}
    int version(String id){return jdbc.queryForObject("SELECT version FROM iam_user WHERE id=?",Integer.class,Long.parseLong(id));}
    long businessAudits(long tenant){return jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action LIKE 'USER_%'",Long.class,tenant);}
    Map<String,Object> createBody(Fixture v,String org){return Map.of("username","created-scope","displayName","范围账号","initialPassword","Created#Pass123","primaryOrganizationId",org,"roleIds",List.of(v.f().baseRole()));}

    @Test void listUsesScopeAndNeverReturnsForeignOrUnassignedAccounts(){
        var v=fixture();var foreign=fixture();String token=login(v,"batch-admin");
        assertThat(send("GET","/api/access/users",token,null,null).getBody().path("data").size()).isEqualTo(4);
        narrow(v);
        var list=send("GET","/api/access/users",token,null,null);assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().path("data").size()).isEqualTo(3);
        assertThat(list.getBody().toString()).doesNotContain(v.f().second(),"batch-two",foreign.f().first());
        jdbc.update("DELETE FROM iam_user_organization WHERE tenant_id=? AND user_id=? AND is_primary=1",v.f().tenant(),Long.parseLong(v.f().unchanged()));
        assertThat(send("GET","/api/access/users",token,null,null).getBody().path("data").size()).isEqualTo(2);
        jdbc.update("UPDATE iam_role_data_scope SET scope_type='NONE' WHERE tenant_id=? AND role_id=? AND resource_code='iam:user'",v.f().tenant(),v.adminRole());
        assertThat(send("GET","/api/access/users",token,null,null).getBody().path("data").size()).isZero();
    }
    @Test void singleAccountOperationsRejectOutOfScopeBeforeWriting(){
        var v=fixture();String token=login(v,"batch-admin");narrow(v);String id=v.f().second();String root="/api/access/users/"+id;
        assertThat(send("GET",root+"/role-impact?roleIds="+v.f().targetRole()+"&version=0",token,null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        Map<String,Object> operations=Map.of("roles",Map.of("roleIds",List.of(v.f().targetRole()),"version",0),"status",Map.of("status","DISABLED","version",0),"password-reset",Map.of("temporaryPassword","Reset#Scope123","version",0),"login-lock",Map.of("locked",true,"reason","scope-test","version",0));
        for(var entry:operations.entrySet())assertThat(send("PUT",root+"/"+entry.getKey(),token,"deny-"+entry.getKey(),entry.getValue()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(version(id)).isZero();assertThat(jdbc.queryForObject("SELECT token_version FROM iam_user WHERE id=?",Integer.class,Long.parseLong(id))).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM iam_user WHERE id=?",String.class,Long.parseLong(id))).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT manual_locked FROM iam_user WHERE id=?",Integer.class,Long.parseLong(id))).isZero();
        assertThat(businessAudits(v.f().tenant())).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_idempotency_record WHERE tenant_id=?",Long.class,v.f().tenant())).isZero();
        var allowed=send("PUT","/api/access/users/"+v.f().first()+"/roles",token,"allowed-role",Map.of("roleIds",List.of(v.f().targetRole()),"version",0));
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(version(v.f().first())).isOne();
    }
    @Test void creationAndBatchesCannotBypassOrganizationScope(){
        var v=fixture();String token=login(v,"batch-admin");narrow(v);
        assertThat(send("POST","/api/access/users",token,"foreign-organization",createBody(v,v.orgB())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var targets=List.of(target(v.f().first(),0),target(v.f().second(),0));
        assertThat(send("PUT","/api/access/users/batch-status",token,"batch-status-denied",Map.of("status","DISABLED","targets",targets)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send("PUT","/api/access/users/batch-roles",token,"batch-roles-denied",Map.of("roleIds",List.of(v.f().targetRole()),"targets",targets)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(version(v.f().first())).isZero();assertThat(version(v.f().second())).isZero();assertThat(businessAudits(v.f().tenant())).isZero();
        var created=send("POST","/api/access/users",token,"allowed-create",createBody(v,Long.toString(v.orgA())));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send("GET","/api/access/users",token,null,null).getBody().toString()).contains("created-scope");
    }
    @Test void narrowingScopeBlocksOldSingleAndBatchReceiptsWithoutChangingHistory(){
        var v=fixture();String token=login(v,"batch-admin");
        var role=Map.of("roleIds",List.of(v.f().targetRole()),"version",0);
        String path="/api/access/users/"+v.f().second()+"/roles";
        assertThat(send("PUT",path,token,"role-receipt",role).getStatusCode()).isEqualTo(HttpStatus.OK);
        var targets=List.of(target(v.f().second(),1));var state=Map.of("status","DISABLED","targets",targets);
        assertThat(send("PUT","/api/access/users/batch-status",token,"state-receipt",state).getStatusCode()).isEqualTo(HttpStatus.OK);
        narrow(v);
        assertThat(send("PUT",path,token,"role-receipt",role).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send("PUT","/api/access/users/batch-status",token,"state-receipt",state).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(version(v.f().second())).isEqualTo(2);assertThat(businessAudits(v.f().tenant())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_idempotency_record WHERE tenant_id=? AND status='COMPLETED'",Long.class,v.f().tenant())).isEqualTo(2);
    }
    @Test void creationReceiptIsCheckedAgainstCurrentAccountLocation(){
        var v=fixture();String token=login(v,"batch-admin");var body=createBody(v,Long.toString(v.orgA()));
        var created=send("POST","/api/access/users",token,"creation-receipt",body);assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        String id=created.getBody().path("data").path("id").asText();move(v.f(),id,v.orgB());narrow(v);
        var replay=send("POST","/api/access/users",token,"creation-receipt",body);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);assertThat(replay.getBody().toString()).doesNotContain(id,"created-scope");
        assertThat(version(id)).isZero();assertThat(businessAudits(v.f().tenant())).isOne();
    }
}

package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.iam.access.AccessService;
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
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false"})
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class IamBatchRolesIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("batch_roles").withUsername("ism").withPassword("ism");
    static final Path STORAGE=Path.of(System.getProperty("java.io.tmpdir"),"ism-batch-roles-"+UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("ism.storage.local-root",STORAGE::toString);}
    @AfterAll static void cleanup()throws Exception{if(Files.exists(STORAGE))try(var paths=Files.walk(STORAGE)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    @Autowired JdbcTemplate jdbc; @Autowired TestRestTemplate http; @Autowired TenantService tenants; @Autowired PackagePlanService plans; @Autowired AccessService access;
    IamBatchRoleData.Fixture fixture(){return IamBatchRoleData.seed(jdbc,tenants,plans,access);}
    JsonNode login(IamBatchRoleData.Fixture f,String user){var r=http.postForEntity("/api/auth/login",Map.of("tenantCode",f.code(),"username",user,"password",IamBatchRoleData.PASSWORD,"deviceId",UUID.randomUUID().toString()),JsonNode.class);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody().path("data");}
    Map<String,Object> target(String id,int version){return Map.of("id",id,"version",version);}
    Map<String,Object> command(IamBatchRoleData.Fixture f){return Map.of("roleIds",List.of(f.targetRole()),"targets",List.of(target(f.first(),0),target(f.second(),0),target(f.unchanged(),0)));}
    ResponseEntity<JsonNode> send(String method,String path,String token,String key,Object body){var h=new HttpHeaders();if(token!=null)h.setBearerAuth(token);if(key!=null)h.set("Idempotency-Key",key);return http.exchange(path,HttpMethod.valueOf(method),new HttpEntity<>(body,h),JsonNode.class);}
    ResponseEntity<JsonNode> apply(String token,String key,Object body){return send("PUT","/api/access/users/batch-roles",token,key,body);}
    long audits(long tenant){return jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='USER_BATCH_ROLE_ASSIGN'",Long.class,tenant);}
    int version(String id){return jdbc.queryForObject("SELECT version FROM iam_user WHERE id=?",Integer.class,Long.parseLong(id));}
    int tokenVersion(String id){return jdbc.queryForObject("SELECT token_version FROM iam_user WHERE id=?",Integer.class,Long.parseLong(id));}

    @Test void previewsReplacesAtomicallyReplaysAndPreservesUnchangedSessions(){
        var f=fixture();String admin=login(f,"batch-admin").path("accessToken").asText();
        var first=login(f,"batch-one");var unchanged=login(f,"batch-same");var body=command(f);
        var preview=send("POST","/api/access/users/batch-role-impact",admin,null,body);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(preview.getBody().path("data").path("changeCount").asInt()).isEqualTo(2);
        assertThat(preview.getBody().path("data").path("canApply").asBoolean()).isTrue();assertThat(version(f.first())).isZero();
        var result=apply(admin,"batch-success",body);assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().path("data").path("changes").size()).isEqualTo(2);
        assertThat(result.getBody().path("data").path("unchangedUserIds").get(0).asText()).isEqualTo(f.unchanged());
        assertThat(version(f.first())).isOne();assertThat(version(f.second())).isOne();assertThat(version(f.unchanged())).isZero();assertThat(tokenVersion(f.first())).isOne();assertThat(tokenVersion(f.unchanged())).isZero();
        assertThat(audits(f.tenant())).isEqualTo(2);
        assertThat(apply(admin,"batch-success",body).getBody().path("data")).isEqualTo(result.getBody().path("data"));assertThat(audits(f.tenant())).isEqualTo(2);
        assertThat(send("GET","/api/auth/me",first.path("accessToken").asText(),null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(send("GET","/api/auth/me",unchanged.path("accessToken").asText(),null,null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.postForEntity("/api/auth/refresh",Map.of("refreshToken",first.path("refreshToken").asText(),"deviceId","irrelevant"),JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var other=Map.of("roleIds",List.of(f.baseRole()),"targets",List.of(target(f.first(),1)));
        assertThat(apply(admin,"batch-success",other).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void staleTargetPreventsEveryWriteAndAuditFailureRollsEverythingBack()throws Exception{
        var f=fixture();String admin=login(f,"batch-admin").path("accessToken").asText();var body=command(f);
        jdbc.update("UPDATE iam_user SET version=1 WHERE id=?",Long.parseLong(f.second()));
        assertThat(apply(admin,"stale-target",body).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(version(f.first())).isZero();assertThat(tokenVersion(f.first())).isZero();assertThat(audits(f.tenant())).isZero();
        jdbc.update("UPDATE iam_user SET version=0 WHERE id=?",Long.parseLong(f.second()));
        trigger("CREATE TRIGGER batch_roles_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.action='USER_BATCH_ROLE_ASSIGN' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic audit failure'; END IF; END");
        try{assertThat(apply(admin,"audit-retry",body).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);}finally{trigger("DROP TRIGGER batch_roles_audit_failure");}
        assertThat(version(f.first())).isZero();assertThat(version(f.second())).isZero();assertThat(tokenVersion(f.first())).isZero();assertThat(audits(f.tenant())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_idempotency_record WHERE tenant_id=? AND idempotency_key='audit-retry'",Long.class,f.tenant())).isZero();
        assertThat(apply(admin,"audit-retry",body).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void rejectsCrossTenantDuplicateOversizedSelfAndUnauthorizedRequests(){
        var f=fixture();var foreign=fixture();String admin=login(f,"batch-admin").path("accessToken").asText();var body=command(f);
        assertThat(apply(null,"unauthenticated",body).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var member=login(f,"batch-one").path("accessToken").asText();
        assertThat(apply(member,"unauthorized",body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send("POST","/api/access/users/batch-role-impact",member,null,body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(apply(admin,"foreign-user",Map.of("roleIds",List.of(f.targetRole()),"targets",List.of(target(f.first(),0),target(foreign.first(),0)))).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(apply(admin,"foreign-role",Map.of("roleIds",List.of(foreign.targetRole()),"targets",List.of(target(f.first(),0)))).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(apply(admin,"duplicate",Map.of("roleIds",List.of(f.targetRole()),"targets",List.of(target(f.first(),0),target(f.first(),0)))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(apply(admin,"oversized",Map.of("roleIds",List.of(f.targetRole()),"targets",Collections.nCopies(101,target(f.first(),0)))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(apply(admin,"empty-roles",Map.of("roleIds",List.of(),"targets",List.of(target(f.first(),0)))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var self=Map.of("roleIds",List.of(f.targetRole()),"targets",List.of(target(Long.toString(f.admin()),0)));
        assertThat(send("POST","/api/access/users/batch-role-impact",admin,null,self).getBody().path("data").path("blockers").toString()).contains("SELF_TARGET");
        assertThat(apply(admin,"self",self).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(version(f.first())).isZero();assertThat(audits(f.tenant())).isZero();
    }
    void trigger(String sql)throws Exception{try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.createStatement()){statement.execute(sql);}}
    @Test void delegatedUserManagerCannotRemoveAdministratorRoles(){
        var f=fixture();
        jdbc.update("INSERT INTO iam_role_permission(tenant_id,role_id,permission_id) SELECT ?,?,id FROM sys_permission WHERE permission_code='iam:user:manage'",f.tenant(),Long.parseLong(f.baseRole()));
        jdbc.update("INSERT INTO iam_role_data_scope(tenant_id,role_id,resource_code,scope_type) VALUES(?,?,'iam:user','TENANT_ALL')",f.tenant(),Long.parseLong(f.baseRole()));
        String manager=login(f,"batch-one").path("accessToken").asText();
        var body=Map.of("roleIds",List.of(f.targetRole()),"targets",List.of(target(Long.toString(f.admin()),0),target(f.second(),0)));
        var preview=send("POST","/api/access/users/batch-role-impact",manager,null,body);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getBody().path("data").path("blockers").toString()).contains("ADMIN_GRANT_REQUIRES_ADMIN");
        assertThat(apply(manager,"delegated-manager",body).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(version(f.second())).isZero();assertThat(tokenVersion(f.second())).isZero();assertThat(audits(f.tenant())).isZero();
    }
    @Test void managementDataScopeIsRequiredAndPrimaryOrganizationLimitsAreEnforced(){
        var f=fixture();
        jdbc.update("INSERT INTO iam_role_permission(tenant_id,role_id,permission_id) SELECT ?,?,id FROM sys_permission WHERE permission_code='iam:user:manage'",f.tenant(),Long.parseLong(f.baseRole()));
        String manager=login(f,"batch-one").path("accessToken").asText();
        var body=Map.of("roleIds",List.of(f.targetRole()),"targets",List.of(target(f.second(),0)));
        assertThat(send("POST","/api/access/users/batch-role-impact",manager,null,body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(apply(manager,"no-data-scope",body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        long org=jdbc.queryForObject("SELECT organization_id FROM iam_user_organization WHERE tenant_id=? AND user_id=? AND is_primary=1",Long.class,f.tenant(),Long.parseLong(f.first()));
        jdbc.update("INSERT INTO iam_role_data_scope(tenant_id,role_id,resource_code,scope_type) VALUES(?,?,'iam:user','ORGANIZATION_SET')",f.tenant(),Long.parseLong(f.baseRole()));
        jdbc.update("INSERT INTO iam_role_data_scope_organization(tenant_id,role_id,resource_code,organization_id) VALUES(?,?,'iam:user',?)",f.tenant(),Long.parseLong(f.baseRole()),org);
        assertThat(send("POST","/api/access/users/batch-role-impact",manager,null,body).getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM iam_user_organization WHERE tenant_id=? AND user_id=? AND is_primary=1",f.tenant(),Long.parseLong(f.second()));
        assertThat(send("POST","/api/access/users/batch-role-impact",manager,null,body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(apply(manager,"outside-organization",body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(version(f.second())).isZero();assertThat(audits(f.tenant())).isZero();
    }
    @Test void concurrentBatchesHaveOneWinner()throws Exception{
        var f=fixture();String admin=login(f,"batch-admin").path("accessToken").asText();var body=command(f);var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try{var futures=new ArrayList<Future<Integer>>();for(int i=0;i<2;i++){String key="race-"+i;futures.add(pool.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException();return apply(admin,key,body).getStatusCode().value();}));}
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();var statuses=new ArrayList<Integer>();for(var future:futures)statuses.add(future.get(30,TimeUnit.SECONDS));assertThat(statuses).containsExactlyInAnyOrder(200,409);assertThat(version(f.first())).isOne();assertThat(version(f.second())).isOne();assertThat(audits(f.tenant())).isEqualTo(2);
        }finally{start.countDown();pool.shutdownNow();}
    }
}

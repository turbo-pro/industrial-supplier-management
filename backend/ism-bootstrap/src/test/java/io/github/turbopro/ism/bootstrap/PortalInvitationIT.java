package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.supplier.*;
import io.github.turbopro.ism.iam.auth.*;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PortalInvitationIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("portal_invitation").withUsername("ism").withPassword("ism");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PortalInvitationService service;
    @Autowired MockMvc mvc;
    @MockBean JwtTokenService tokens;
    @MockBean AuthService auth;
    @MockBean AuthorizationGrantLoader grants;
    static final AtomicLong IDS=new AtomicLong(20700);
    record Fixture(long tenant,long supplier,long org) {}
    Fixture fixture(){
        long t=IDS.incrementAndGet(),s=t*100,o=s+1;
        jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,'ACTIVE')",t,"INV-"+t,"邀请测试");
        jdbc.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,'HQ','总部','HEADQUARTERS','ACTIVE')",o,t);
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(?,?,?,'SUP','测试供应商','MANUFACTURER',9,9)",s,t,o);
        return new Fixture(t,s,o);
    }
    PermissionSnapshot permissions(DataScope scope){return new PermissionSnapshot(Set.of("supplier:master:view","supplier:master:update"),Map.of("supplier:master",scope),Set.of());}
    PortalInvitationModels.Issued issue(Fixture f,int generation){try(var t=TenantContext.open(f.tenant(),9);var a=AuthorizationContext.open(permissions(DataScope.all()))){return service.issue(f.supplier(),new PortalInvitationModels.Issue(generation,"联系人","contact@example.test",24));}}

    @Test void issuesRotatesRevokesAndNeverPersistsPlaintext(){
        var f=fixture();var first=issue(f,0);assertThat(first.token()).matches("[A-Za-z0-9_-]{43}");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM sup_portal_invitation WHERE id=?",String.class,Long.parseLong(first.invitation().id()))).hasSize(64).isNotEqualTo(first.token());
        var second=issue(f,1);assertThat(second.token()).isNotEqualTo(first.token());
        try(var t=TenantContext.open(f.tenant(),9);var a=AuthorizationContext.open(permissions(DataScope.all()))){
            assertThat(service.history(f.supplier()).items()).extracting(PortalInvitationModels.Invitation::status).containsExactly("PENDING","REPLACED");
            assertThatThrownBy(()->service.issue(f.supplier(),new PortalInvitationModels.Issue(1,"重复","contact@example.test",24))).isInstanceOf(ApiException.class);
            assertThat(service.revoke(f.supplier(),Long.parseLong(second.invitation().id()),new PortalInvitationModels.Revoke(0,"联系人变更")).status()).isEqualTo("REVOKED");
            assertThatThrownBy(()->service.revoke(f.supplier(),Long.parseLong(second.invitation().id()),new PortalInvitationModels.Revoke(0,"重复"))).isInstanceOf(ApiException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action LIKE 'SUPPLIER_PORTAL_INVITATION_%'",Long.class,f.tenant())).isEqualTo(3);
    }
    @Test void enforcesScopeExpirationAndSupplierState(){
        var f=fixture();var foreign=fixture();var issued=issue(f,0);
        jdbc.update("UPDATE sup_portal_invitation SET expires_at=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?",Long.parseLong(issued.invitation().id()));
        try(var t=TenantContext.open(f.tenant(),9);var a=AuthorizationContext.open(permissions(DataScope.all()))){
            assertThat(service.history(f.supplier()).items().get(0).status()).isEqualTo("EXPIRED");
            assertThatThrownBy(()->service.revoke(f.supplier(),Long.parseLong(issued.invitation().id()),new PortalInvitationModels.Revoke(0,"过期"))).isInstanceOf(ApiException.class);
            assertThatThrownBy(()->service.history(foreign.supplier())).isInstanceOf(ApiException.class);
        }
        try(var t=TenantContext.open(f.tenant(),9);var a=AuthorizationContext.open(permissions(DataScope.none()))){assertThatThrownBy(()->service.history(f.supplier())).isInstanceOf(ApiException.class);}
        for(String state:List.of("SUSPENDED","EXITED")){
            jdbc.update("UPDATE sup_supplier SET status=? WHERE id=?",state,f.supplier());
            assertThatThrownBy(()->issue(f,1)).isInstanceOf(ApiException.class);
        }
    }
    @Test void serializesConcurrentIssuanceWithSameGeneration() throws Exception {
        var f=fixture();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var futures=new ArrayList<Future<Boolean>>();
            for(int i=0;i<2;i++) futures.add(pool.submit(()->{start.await();try{issue(f,0);return true;}catch(ApiException conflict){assertThat(conflict.getMessage()).contains("邀请已发生变化");return false;}}));
            start.countDown();assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=?",Long.class,f.tenant())).isEqualTo(1);
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void rollsBackReplacementWhenAuditCannotBeWritten() throws Exception {
        var f=fixture();var first=issue(f,0);
        String trigger="CREATE TRIGGER invitation_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='SUPPLIER_PORTAL_INVITATION_ISSUE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected invitation audit failure'; END IF; END";
        var result=MYSQL.execInContainer("mysql","--delimiter=//","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e",trigger+"//");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        try {
            assertThatThrownBy(()->issue(f,1)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=?",Long.class,f.tenant())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM sup_portal_invitation WHERE id=?",String.class,Long.parseLong(first.invitation().id()))).isEqualTo("PENDING");
        } finally {
            var drop=MYSQL.execInContainer("mysql","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e","DROP TRIGGER invitation_audit_failure");
            assertThat(drop.getExitCode()).as(drop.getStderr()).isZero();
        }
        assertThat(issue(f,1).invitation().generation()).isEqualTo(2);
    }
    @Test void protectsHttpPermissionsValidationAndSecretResponses() throws Exception {
        var f=fixture();String path="/api/suppliers/"+f.supplier()+"/portal-invitations";
        when(tokens.decode("writer")).thenReturn(Jwt.withTokenValue("writer").header("alg","HS256").subject("9").claim("tokenVersion",0).claim("tokenFamilyId","inv-family").build());
        when(auth.requireActiveSession(9,0,"inv-family")).thenReturn(new AuthModels.AuthUser(9,f.tenant(),"admin","管理员","unused","ACTIVE",false,null,false,0,0,null));
        when(grants.load(f.tenant(),9)).thenReturn(permissions(DataScope.all()));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization","Bearer writer").contentType("application/json").content("{\"recipientName\":\"联系人\",\"recipientEmail\":\"a@example.test\",\"validHours\":24}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization","Bearer writer").contentType("application/json").content("{\"expectedGeneration\":0,\"recipientName\":\"联系人\",\"recipientEmail\":\"bad\",\"validHours\":169}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization","Bearer writer").contentType("application/json").content("{\"expectedGeneration\":0,\"recipientName\":\"联系人\",\"recipientEmail\":\"a@example.test\",\"validHours\":24}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.token").isNotEmpty());
        mvc.perform(get(path).header("Authorization","Bearer writer")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].token").doesNotExist()).andExpect(jsonPath("$.data.items[0].tokenHash").doesNotExist());
        when(grants.load(f.tenant(),9)).thenReturn(new PermissionSnapshot(Set.of("supplier:master:view"),Map.of("supplier:master",DataScope.all()),Set.of()));
        mvc.perform(post(path).header("Authorization","Bearer writer").contentType("application/json").content("{\"expectedGeneration\":1,\"recipientName\":\"联系人\",\"recipientEmail\":\"a@example.test\",\"validHours\":24}")).andExpect(status().isForbidden());
    }
}

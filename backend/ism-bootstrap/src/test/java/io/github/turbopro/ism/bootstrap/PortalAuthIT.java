package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.supplier.*;
import io.github.turbopro.ism.iam.portal.*;
import io.github.turbopro.ism.iam.auth.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"ism.portal.registration-enabled=true","ism.portal.authentication-enabled=true","ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false"})
@AutoConfigureMockMvc @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PortalAuthIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("portal_auth").withUsername("ism").withPassword("ism");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
    @Autowired JdbcTemplate jdbc;@Autowired PortalInvitationService invites;@Autowired PortalRegistrationService registrations;
    @Autowired PortalAuthService auth;@Autowired MockMvc mvc;@Autowired JwtTokenService internalTokens;
    static final AtomicLong IDS=new AtomicLong(20900);
    record Fixture(long tenant,long supplier,long account) {String code(){return "AUTH-"+tenant;}}
    Fixture fixture(){
        long t=IDS.incrementAndGet(),s=t*100;
        jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,'ACTIVE')",t,"AUTH-"+t,"门户登录测试");
        jdbc.update("INSERT INTO plt_package(id,package_code,package_name,status) VALUES(?,?,?,'ACTIVE')",s+2,"AUTH-"+t,"门户套餐");
        jdbc.update("INSERT INTO plt_package_version(id,package_id,version_no,version_name,status) VALUES(?,?,1,'门户版本','PUBLISHED')",s+3,s+2);
        jdbc.update("INSERT INTO plt_package_module(package_version_id,module_id,enabled,quota_json) SELECT ?,id,1,JSON_OBJECT() FROM plt_module WHERE module_code='SUPPLIER'",s+3);
        jdbc.update("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status,package_version_id) VALUES(?,?,?,'ACTIVE','READY',?)",t,"AUTH-"+t,"门户测试",s+3);
        jdbc.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,'HQ','总部','HEADQUARTERS','ACTIVE')",s+1,t);
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(?,?,?,'SUP','供应商','MANUFACTURER',9,9)",s,t,s+1);
        PortalInvitationModels.Issued invite;
        try(var tenant=TenantContext.open(t,9);var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:update"),Map.of("supplier:master",DataScope.all()),Set.of()))){invite=invites.issue(s,new PortalInvitationModels.Issue(0,"联系人","portal@example.test",24));}
        var registered=registrations.register(new PortalRegistrationModels.Register(invite.token(),"Portal.User","Portal#Pass123"));
        return new Fixture(t,s,Long.parseLong(registered.accountId()));
    }
    PortalAuthModels.Session login(Fixture f){return auth.login(new PortalAuthModels.Login(f.code(),"Portal.User","Portal#Pass123"));}
    void me(String token,int expected)throws Exception{mvc.perform(get("/api/portal/auth/me").header("Authorization","Bearer "+token)).andExpect(status().is(expected));}

    @Test void httpLoginReturnsBoundIdentityAndCannotCrossInternalRealms()throws Exception{
        var f=fixture();var response=mvc.perform(post("/api/portal/auth/login").contentType("application/json").content("{\"tenantCode\":\""+f.code()+"\",\"username\":\"PORTAL.USER\",\"password\":\"Portal#Pass123\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.user.accountId").value(Long.toString(f.account()))).andReturn();
        String token=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("data").path("accessToken").asText();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        mvc.perform(get("/api/portal/auth/me").header("Authorization","Bearer "+token).header("X-Tenant-Id","999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(Long.toString(f.tenant()))).andExpect(jsonPath("$.data.supplierId").value(Long.toString(f.supplier()))).andExpect(jsonPath("$.data.permissions").doesNotExist());
        mvc.perform(get("/api/suppliers").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/console/tenants").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        String internal=internalTokens.createAccessToken(new AuthModels.AuthUser(9,f.tenant(),"employee","内部员工","unused","ACTIVE",false,null,false,0,0,null),"not-a-portal-session");
        me(internal,401);mvc.perform(get("/api/portal/auth/me")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT token_hash FROM iam_portal_session WHERE account_id=?",String.class,f.account())).isNotEqualTo(token).hasSize(64);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_refresh_token WHERE tenant_id=?",Long.class,f.tenant())).isZero();
        assertThat(TenantContext.current()).isEmpty();
    }
    @Test void logoutOnlyRevokesCurrentSessionAndRejectsReplay()throws Exception{
        var f=fixture();var first=login(f);var second=login(f);
        mvc.perform(post("/api/portal/auth/logout").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isOk());
        me(first.accessToken(),401);me(second.accessToken(),200);
        mvc.perform(post("/api/portal/auth/logout").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE account_id=? AND revoked_at IS NOT NULL",Long.class,f.account())).isEqualTo(1);
        var principal=auth.authenticate(second.accessToken());auth.logout(principal);auth.logout(principal);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='PORTAL_LOGOUT'",Long.class,f.tenant())).isEqualTo(2);
        assertThat(first.toString()).doesNotContain(first.accessToken());
    }
    @Test void accountVersionAndDomainChangesAreRecheckedOnEveryRequest()throws Exception{
        var f=fixture();String token=login(f).accessToken();
        jdbc.update("UPDATE iam_portal_account SET status='DISABLED' WHERE id=?",f.account());me(token,401);
        jdbc.update("UPDATE iam_portal_account SET status='ACTIVE' WHERE id=?",f.account());me(token,200);
        jdbc.update("UPDATE sup_supplier SET status='SUSPENDED' WHERE id=?",f.supplier());me(token,401);
        jdbc.update("UPDATE sup_supplier SET status='DRAFT' WHERE id=?",f.supplier());me(token,200);
        jdbc.update("UPDATE plt_tenant SET status='SUSPENDED' WHERE id=?",f.tenant());me(token,401);
        jdbc.update("UPDATE plt_tenant SET status='ACTIVE' WHERE id=?",f.tenant());
        jdbc.update("UPDATE iam_tenant SET status='SUSPENDED' WHERE id=?",f.tenant());me(token,401);
        jdbc.update("UPDATE iam_tenant SET status='ACTIVE' WHERE id=?",f.tenant());
        jdbc.update("UPDATE plt_package_module SET enabled=0 WHERE package_version_id=?",f.supplier()+3);me(token,401);
        jdbc.update("UPDATE plt_package_module SET enabled=1 WHERE package_version_id=?",f.supplier()+3);me(token,200);
        jdbc.update("UPDATE iam_portal_account SET token_version=token_version+1 WHERE id=?",f.account());me(token,401);
    }
    @Test void badPasswordsPersistLockoutAndExpiredLockAllowsRecovery(){
        var f=fixture();var wrong=new PortalAuthModels.Login(f.code(),"portal.user","Incorrect#Pass123");
        for(int i=0;i<5;i++)assertThatThrownBy(()->auth.login(wrong)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT failed_count FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isEqualTo(5);
        assertThatThrownBy(()->login(f)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE account_id=?",Long.class,f.account())).isZero();
        jdbc.update("UPDATE iam_portal_account SET locked_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?",f.account());
        login(f);assertThat(jdbc.queryForObject("SELECT failed_count FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
    }
    @Test void expiredRevokedAndUnknownTokensCannotAuthenticate()throws Exception{
        var f=fixture();String token=login(f).accessToken();
        jdbc.update("UPDATE iam_portal_session SET expires_at=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE account_id=?",f.account());me(token,401);me("Z".repeat(43),401);
        String another=login(f).accessToken();jdbc.update("UPDATE iam_portal_session SET revoked_at=UTC_TIMESTAMP(3) WHERE account_id=?",f.account());me(another,401);
    }
    @Test void auditFailureRollsBackLoginAndLogout()throws Exception{
        var f=fixture();String trigger="CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action IN ('PORTAL_LOGIN','PORTAL_LOGOUT') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected portal auth audit failure'; END IF; END";
        install(trigger);
        try{assertThatThrownBy(()->login(f)).isInstanceOf(RuntimeException.class);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE account_id=?",Long.class,f.account())).isZero();}
        finally{drop();}
        String token=login(f).accessToken();install(trigger);
        try{mvc.perform(post("/api/portal/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isInternalServerError());me(token,200);}
        finally{drop();}
        mvc.perform(post("/api/portal/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk());me(token,401);
    }
    void install(String trigger)throws Exception{var result=MYSQL.execInContainer("mysql","--delimiter=//","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e",trigger+"//");assertThat(result.getExitCode()).as(result.getStderr()).isZero();}
    void drop()throws Exception{var result=MYSQL.execInContainer("mysql","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e","DROP TRIGGER portal_auth_audit_failure");assertThat(result.getExitCode()).as(result.getStderr()).isZero();}
}

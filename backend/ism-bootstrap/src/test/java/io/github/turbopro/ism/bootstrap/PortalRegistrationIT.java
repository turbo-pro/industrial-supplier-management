package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.supplier.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.api.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"ism.portal.registration-enabled=true","ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false"})
@AutoConfigureMockMvc @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PortalRegistrationIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("portal_registration").withUsername("ism").withPassword("ism");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
    @Autowired JdbcTemplate jdbc;@Autowired PortalInvitationService invitations;@Autowired PortalRegistrationService registrations;
    @Autowired PortalRegistrationRateGuard rate;@Autowired PasswordEncoder passwords;@Autowired MockMvc mvc;
    static final AtomicLong IDS=new AtomicLong(20800);
    record Fixture(long tenant,long supplier,PortalInvitationModels.Issued invite) {}
    Fixture fixture(){
        long t=IDS.incrementAndGet(),s=t*100;
        jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,'ACTIVE')",t,"REG-"+t,"注册测试");
        jdbc.update("INSERT INTO plt_package(id,package_code,package_name,status) VALUES(?,?,?,'ACTIVE')",s+2,"REG-"+t,"门户测试套餐");
        jdbc.update("INSERT INTO plt_package_version(id,package_id,version_no,version_name,status) VALUES(?,?,1,'门户测试版本','PUBLISHED')",s+3,s+2);
        jdbc.update("INSERT INTO plt_package_module(package_version_id,module_id,enabled,quota_json) SELECT ?,id,1,JSON_OBJECT() FROM plt_module WHERE module_code='SUPPLIER'",s+3);
        jdbc.update("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status,package_version_id) VALUES(?,?,?,'ACTIVE','READY',?)",t,"REG-"+t,"注册测试",s+3);
        jdbc.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,'HQ','总部','HEADQUARTERS','ACTIVE')",s+1,t);
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(?,?,?,'SUP','供应商','MANUFACTURER',9,9)",s,t,s+1);
        try(var tenant=TenantContext.open(t,9);var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:update"),Map.of("supplier:master",DataScope.all()),Set.of()))){
            return new Fixture(t,s,invitations.issue(s,new PortalInvitationModels.Issue(0,"供应商联系人","supplier@example.test",24)));
        }
    }
    PortalRegistrationModels.Register command(Fixture f){return new PortalRegistrationModels.Register(f.invite().token(),"Portal.User","Portal#Pass123");}
    String inviteState(Fixture f){return jdbc.queryForObject("SELECT status FROM sup_portal_invitation WHERE id=?",String.class,Long.parseLong(f.invite().invitation().id()));}
    @Test void redeemsOnceIntoIndependentIdentityAndRestoresContext(){
        var f=fixture();
        try(var original=TenantContext.open(999,7)){
            var result=registrations.register(command(f));
            assertThat(result.tenantId()).isEqualTo(Long.toString(f.tenant()));assertThat(result.supplierId()).isEqualTo(Long.toString(f.supplier()));assertThat(result.username()).isEqualTo("portal.user");
            assertThat(TenantContext.require().tenantId()).isEqualTo(999);
            assertThat(passwords.matches("Portal#Pass123",jdbc.queryForObject("SELECT password_hash FROM iam_portal_account WHERE id=?",String.class,Long.parseLong(result.accountId())))).isTrue();
            assertThatThrownBy(()->registrations.register(command(f))).isInstanceOf(ApiException.class);
            assertThat(TenantContext.require().tenantId()).isEqualTo(999);
        }
        assertThat(inviteState(f)).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user WHERE tenant_id=?",Long.class,f.tenant())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user_role WHERE tenant_id=?",Long.class,f.tenant())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_refresh_token WHERE tenant_id=?",Long.class,f.tenant())).isZero();
    }
    @Test void rejectsExpiredReplacedRevokedSuspendedAndUnavailableTenantInvites(){
        var expired=fixture();jdbc.update("UPDATE sup_portal_invitation SET expires_at=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?",Long.parseLong(expired.invite().invitation().id()));
        assertThatThrownBy(()->registrations.register(command(expired))).isInstanceOf(ApiException.class);
        for(String state:List.of("REPLACED","REVOKED")) {var f=fixture();jdbc.update("UPDATE sup_portal_invitation SET status=? WHERE id=?",state,Long.parseLong(f.invite().invitation().id()));assertThatThrownBy(()->registrations.register(command(f))).isInstanceOf(ApiException.class);}
        var suspended=fixture();jdbc.update("UPDATE sup_supplier SET status='SUSPENDED' WHERE id=?",suspended.supplier());assertThatThrownBy(()->registrations.register(command(suspended))).isInstanceOf(ApiException.class);
        var tenant=fixture();jdbc.update("UPDATE plt_tenant SET status='SUSPENDED' WHERE id=?",tenant.tenant());assertThatThrownBy(()->registrations.register(command(tenant))).isInstanceOf(ApiException.class);
        var noPackage=fixture();jdbc.update("UPDATE plt_tenant SET package_version_id=NULL WHERE id=?",noPackage.tenant());assertThatThrownBy(()->registrations.register(command(noPackage))).isInstanceOf(ApiException.class);
    }
    @Test void serializesTwoConcurrentRedemptions() throws Exception {
        var f=fixture();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {var results=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)results.add(pool.submit(()->{start.await();try{registrations.register(command(f));return true;}catch(ApiException e){return false;}}));
            start.countDown();assertThat(List.of(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_account WHERE tenant_id=?",Long.class,f.tenant())).isEqualTo(1);
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void duplicateUsernameCannotConsumeAnotherInvitationOrRebindExistingAccount(){
        var f=fixture();var existing=registrations.register(command(f));
        PortalInvitationModels.Issued second;
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:update"),Map.of("supplier:master",DataScope.all()),Set.of()))){
            second=invitations.issue(f.supplier(),new PortalInvitationModels.Issue(1,"另一联系人","other@example.test",24));
        }
        assertThatThrownBy(()->registrations.register(new PortalRegistrationModels.Register(second.token(),"PORTAL.USER","Portal#Pass123"))).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM sup_portal_invitation WHERE id=?",String.class,Long.parseLong(second.invitation().id()))).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT email FROM iam_portal_account WHERE id=?",String.class,Long.parseLong(existing.accountId()))).isEqualTo("supplier@example.test");
        assertThat(registrations.register(new PortalRegistrationModels.Register(second.token(),"Other.User","Portal#Pass123")).username()).isEqualTo("other.user");
    }
    @Test void weakPasswordAndAuditFailureDoNotConsumeInvitation() throws Exception {
        var f=fixture();assertThatThrownBy(()->registrations.register(new PortalRegistrationModels.Register(f.invite().token(),"Portal.User","weakpassword123"))).isInstanceOf(ApiException.class);assertThat(inviteState(f)).isEqualTo("PENDING");
        String trigger="CREATE TRIGGER portal_register_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='SUPPLIER_PORTAL_REGISTER' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected registration failure'; END IF; END";
        var install=MYSQL.execInContainer("mysql","--delimiter=//","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e",trigger+"//");assertThat(install.getExitCode()).as(install.getStderr()).isZero();
        try {assertThatThrownBy(()->registrations.register(command(f))).isInstanceOf(RuntimeException.class);assertThat(inviteState(f)).isEqualTo("PENDING");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_account WHERE tenant_id=?",Long.class,f.tenant())).isZero();}
        finally {var drop=MYSQL.execInContainer("mysql","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e","DROP TRIGGER portal_register_audit_failure");assertThat(drop.getExitCode()).isZero();}
        registrations.register(command(f));assertThat(inviteState(f)).isEqualTo("ACCEPTED");
    }
    @Test void anonymousHttpBindsOnlyToInvitationAndLimitsTransportPeer() throws Exception {
        var f=fixture();
        mvc.perform(post("/api/portal/auth/register").header("X-Tenant-Id","999").contentType("application/json").content("{\"invitationToken\":\""+f.invite().token()+"\",\"username\":\"Portal.User\",\"password\":\"Portal#Pass123\",\"supplierId\":\"999\",\"email\":\"attacker@example.test\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.tenantId").value(Long.toString(f.tenant()))).andExpect(jsonPath("$.data.supplierId").value(Long.toString(f.supplier()))).andExpect(jsonPath("$.data.accessToken").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT email FROM iam_portal_account WHERE tenant_id=?",String.class,f.tenant())).isEqualTo("supplier@example.test");
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"tenantCode\":\"REG-"+f.tenant()+"\",\"username\":\"portal.user\",\"password\":\"Portal#Pass123\",\"deviceId\":\"portal-cannot-use-admin\"}"))
                .andExpect(status().isUnauthorized());
        for(int i=0;i<20;i++)rate.check("rate-guard-shared-peer");
        assertThatThrownBy(()->rate.check("rate-guard-shared-peer")).isInstanceOf(ApiException.class).hasMessageContaining("频繁");
        mvc.perform(get("/api/portal/auth/register")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/suppliers")).andExpect(status().isUnauthorized());
    }
}

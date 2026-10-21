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

@SpringBootTest(properties={"ism.portal.registration-enabled=true","ism.portal.authentication-enabled=true","ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false","ism.integration.finance.mode=MANUAL"})
@AutoConfigureMockMvc @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PortalAuthIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("portal_auth").withUsername("ism").withPassword("ism");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
    @Autowired JdbcTemplate jdbc;@Autowired PortalInvitationService invites;@Autowired PortalRegistrationService registrations;
    @Autowired PortalAuthService auth;@Autowired MockMvc mvc;@Autowired JwtTokenService internalTokens;
    @Autowired PortalAccountManagementService administration;
    @Autowired ExitApplicationService exits;
    @Autowired ExitArchiveService archives;
    @Autowired io.github.turbopro.ism.integration.finance.ManualClearanceMapper finance;
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
    io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Account manage(Fixture f,int version,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action action){
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view","supplier:master:update"),Map.of("supplier:master",DataScope.all()),Set.of()))){
            return administration.change(f.supplier(),f.account(),version,action,"账号安全维护");
        }
    }
    @Test void administrationDisablesRestoresAndPermanentlyRevokesAllDevices()throws Exception{
        var f=fixture();var first=login(f);var second=login(f);
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view"),Map.of("supplier:master",DataScope.all()),Set.of()))){
            var list=administration.list(f.supplier(),1,20);
            assertThat(list.canManage()).isFalse();assertThat(list.accounts().total()).isEqualTo(1);
            assertThat(list.accounts().items().get(0).liveSessions()).isEqualTo(2);
        }
        var disabled=manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE);
        assertThat(disabled.status()).isEqualTo("DISABLED");assertThat(disabled.version()).isEqualTo(1);assertThat(disabled.liveSessions()).isZero();
        me(first.accessToken(),401);me(second.accessToken(),401);assertThatThrownBy(()->login(f)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        manage(f,1,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.ENABLE);
        me(first.accessToken(),401);var third=login(f);me(third.accessToken(),200);
        manage(f,2,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.REVOKE_SESSIONS);
        me(third.accessToken(),401);me(login(f).accessToken(),200);
        assertThatThrownBy(()->manage(f,2,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action LIKE 'SUPPLIER_PORTAL_ACCOUNT_%'",Long.class,f.tenant())).isEqualTo(3);
    }
    @Test void administrationRequiresActionScopeAndCorrectTenantAndSupplier(){
        var f=fixture();var other=fixture();
        try(var tenant=TenantContext.open(f.tenant(),9)){
            assertThatThrownBy(()->administration.list(f.supplier(),1,20)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            try(var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view"),Map.of("supplier:master",DataScope.all()),Set.of()))){
                assertThatThrownBy(()->administration.change(f.supplier(),f.account(),0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE,"越权")).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                assertThatThrownBy(()->administration.list(other.supplier(),1,20)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                assertThatThrownBy(()->administration.list(f.supplier(),0,20)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            }
            try(var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view"),Map.of("supplier:master",DataScope.none()),Set.of()))){
                assertThatThrownBy(()->administration.list(f.supplier(),1,20)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            }
            try(var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:update"),Map.of("supplier:master",DataScope.all()),Set.of()))){
                assertThatThrownBy(()->administration.change(f.supplier(),other.account(),0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE,"错绑")).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            }
        }
    }
    @Test void administrationAuditFailureRollsBackStatusVersionAndSessionRevocation()throws Exception{
        var f=fixture();var session=login(f);
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action LIKE 'SUPPLIER_PORTAL_ACCOUNT_%' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected account audit failure'; END IF; END");
        try{assertThatThrownBy(()->manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE)).isInstanceOf(RuntimeException.class);}
        finally{drop();}
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_account WHERE id=?",String.class,f.account())).isEqualTo("ACTIVE");me(session.accessToken(),200);
        assertThat(manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE).status()).isEqualTo("DISABLED");
    }
    @Test void concurrentAdministrationWithSameVersionHasOnlyOneWinner()throws Exception{
        var f=fixture();var token=login(f).accessToken();
        var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var first=pool.submit(()->{start.await();try{manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE);return true;}catch(io.github.turbopro.ism.common.api.error.ApiException conflict){assertThat(conflict.errorCode()).isEqualTo(io.github.turbopro.ism.common.api.error.CommonErrorCode.CONFLICT);return false;}});
            var second=pool.submit(()->{start.await();try{manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.REVOKE_SESSIONS);return true;}catch(io.github.turbopro.ism.common.api.error.ApiException conflict){assertThat(conflict.errorCode()).isEqualTo(io.github.turbopro.ism.common.api.error.CommonErrorCode.CONFLICT);return false;}});
            start.countDown();assertThat(List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isEqualTo(1);me(token,401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action LIKE 'SUPPLIER_PORTAL_ACCOUNT_%'",Long.class,f.tenant())).isEqualTo(1);
    }
    @Test void unavailableSupplierStillAllowsDisableButNeverEnable(){
        var f=fixture();jdbc.update("UPDATE sup_supplier SET status='SUSPENDED' WHERE id=?",f.supplier());
        manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE);
        assertThatThrownBy(()->manage(f,1,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.ENABLE)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        manage(f,1,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.REVOKE_SESSIONS);
    }
    AuthorizationContext.Scope exitGrants(){return AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view","supplier:master:update","supplier:exit:create","supplier:exit:review"),Map.of("supplier:master",DataScope.all()),Set.of()));}
    ExitModels.View prepareExit(Fixture f){
        long file=f.supplier()+50;
        jdbc.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(?,?,9,'exit.pdf','application/pdf',1,?,'LOCAL','test/exit','ACTIVE')",file,f.tenant(),"e".repeat(64));
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=exitGrants()){
            finance.insert(f.tenant(),f.supplier(),file,"隔离测试人工财务证据",9);finance.review(f.tenant(),f.supplier(),"APPROVED","独立复核",10,0);
            return exits.create(f.supplier(),new ExitModels.Create(ExitModels.Type.NORMAL,"合作结束",Long.toString(file)));
        }
    }
    ExitModels.View approveExit(Fixture f,ExitModels.View application){
        try(var tenant=TenantContext.open(f.tenant(),10);var grants=exitGrants()){
            return exits.review(f.supplier(),Long.parseLong(application.id()),new ExitModels.Review(ExitModels.Decision.APPROVE,"独立核验通过",application.version()));
        }
    }
    String pendingInvite(Fixture f){
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=exitGrants()){
            return invites.issue(f.supplier(),new PortalInvitationModels.Issue(1,"新联系人","next@example.test",24)).token();
        }
    }
    @Test void approvedExitRecoversEveryLocalAccountAndInvitationWithoutClaimingExternalSuccess()throws Exception{
        var f=fixture();String token=login(f).accessToken();login(f);String invitation=pendingInvite(f);
        // Force a second keyset page, including already-disabled accounts.
        for(int n=0;n<100;n++)jdbc.update("INSERT INTO iam_portal_account(id,tenant_id,supplier_id,username,display_name,email,password_hash,status) SELECT ?,tenant_id,supplier_id,?,'批量账号',?,password_hash,? FROM iam_portal_account WHERE id=?",900000000L+f.supplier()*1000+n,"batch."+n,"batch"+n+"@example.test",n%2==0?"ACTIVE":"DISABLED",f.account());
        long otherSupplier=f.supplier()+20,otherAccount=f.supplier()+21;
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(?,?,?,'OTHER','其他供应商','MANUFACTURER',9,9)",otherSupplier,f.tenant(),f.supplier()+1);
        jdbc.update("INSERT INTO iam_portal_account(id,tenant_id,supplier_id,username,display_name,email,password_hash) SELECT ?,tenant_id,?,'other.user','其他联系人','other@example.test',password_hash FROM iam_portal_account WHERE id=?",otherAccount,otherSupplier,f.account());
        var other=auth.login(new PortalAuthModels.Login(f.code(),"other.user","Portal#Pass123"));
        var application=prepareExit(f);var closed=approveExit(f,application);
        assertThat(closed.status()).isEqualTo("BUSINESS_CLOSED");assertThat(closed.result().accessRecoveryStatus()).isEqualTo("NOT_VERIFIED");
        assertThat(closed.events()).anySatisfy(event->{assertThat(event.action()).isEqualTo("LOCAL_PORTAL_RECOVERY");assertThat(event.comment()).contains("处理账号 101","撤销会话记录 2","撤销有效未消费邀请 1");});
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_account WHERE tenant_id=? AND supplier_id=? AND status='DISABLED' AND version=1 AND token_version=1",Long.class,f.tenant(),f.supplier())).isEqualTo(101);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_PORTAL_ACCOUNT_EXIT_RECOVERY'",Long.class,f.tenant())).isEqualTo(101);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=? AND supplier_id=? AND status='REVOKED' AND revoked_by=10",Long.class,f.tenant(),f.supplier())).isEqualTo(1);
        me(token,401);me(other.accessToken(),200);
        try(var tenant=TenantContext.open(f.tenant(),10);var grants=exitGrants()){
            assertThat(archives.get(f.supplier(),Long.parseLong(application.id())).integrityVerified()).isTrue();
        }
        // Counterfactual isolated-DB restore proves permanent revocation, not just the EXITED gate.
        jdbc.update("UPDATE sup_supplier SET status='DRAFT' WHERE id=?",f.supplier());me(token,401);
        assertThatThrownBy(()->registrations.register(new PortalRegistrationModels.Register(invitation,"Cannot.Rejoin","Portal#Pass123"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThatThrownBy(()->approveExit(f,application)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
    }
    @Test void exitAuditFailureRollsBackAccountsInvitationsApprovalAndArchive()throws Exception{
        var f=fixture();String token=login(f).accessToken();pendingInvite(f);var application=prepareExit(f);
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='SUPPLIER_EXIT_BUSINESS_CLOSE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected exit final audit failure'; END IF; END");
        try{assertThatThrownBy(()->approveExit(f,application)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("injected exit final audit failure");}finally{drop();}
        assertThat(jdbc.queryForObject("SELECT status FROM sup_supplier WHERE id=?",String.class,f.supplier())).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT status FROM sup_exit_application WHERE id=?",String.class,Long.parseLong(application.id()))).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE account_id=? AND revoked_at IS NULL",Long.class,f.account())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=? AND supplier_id=? AND status='PENDING'",Long.class,f.tenant(),f.supplier())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_result WHERE application_id=?",Long.class,Long.parseLong(application.id()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_archive WHERE application_id=?",Long.class,Long.parseLong(application.id()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_access_recovery_task WHERE application_id=?",Long.class,Long.parseLong(application.id()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_event WHERE application_id=? AND action='LOCAL_PORTAL_RECOVERY'",Long.class,Long.parseLong(application.id()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_PORTAL_ACCOUNT_EXIT_RECOVERY'",Long.class,f.tenant())).isZero();
        approveExit(f,application);me(token,401);
    }
    @Test void cancellingOrRejectingExitNeverPermanentlyRecoversLocalAccounts()throws Exception{
        for(boolean reject:List.of(false,true)){
            var f=fixture();String token=login(f).accessToken();pendingInvite(f);var application=prepareExit(f);
            assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
            try(var tenant=TenantContext.open(f.tenant(),reject?10:9);var grants=exitGrants()){
                if(reject)exits.review(f.supplier(),Long.parseLong(application.id()),new ExitModels.Review(ExitModels.Decision.REJECT,"继续合作",application.version()));
                else exits.cancel(f.supplier(),Long.parseLong(application.id()),new ExitModels.Version(application.version()));
            }
            me(token,200);
            assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_account WHERE id=?",String.class,f.account())).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=? AND supplier_id=? AND status='PENDING'",Long.class,f.tenant(),f.supplier())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_PORTAL_ACCOUNT_EXIT_RECOVERY'",Long.class,f.tenant())).isZero();
        }
    }
    void install(String trigger)throws Exception{var result=MYSQL.execInContainer("mysql","--delimiter=//","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e",trigger+"//");assertThat(result.getExitCode()).as(result.getStderr()).isZero();}
    void drop()throws Exception{var result=MYSQL.execInContainer("mysql","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e","DROP TRIGGER portal_auth_audit_failure");assertThat(result.getExitCode()).as(result.getStderr()).isZero();}
}

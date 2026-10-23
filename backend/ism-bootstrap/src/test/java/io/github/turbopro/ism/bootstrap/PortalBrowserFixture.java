package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.supplier.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit browser fixture only, never discovered by the normal *IT suite. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "ism.portal.registration-enabled=true", "ism.portal.authentication-enabled=true",
        "ism.print.worker-enabled=false", "ism.subscription-reminders.enabled=false", "server.address=127.0.0.1"})
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PortalBrowserFixture {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("portal_browser").withUsername("ism").withPassword("ism");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("ism.integration.finance.mode",()->Boolean.getBoolean("ism.browser.portal-exit")?"MANUAL":"ERP");
        r.add("ism.portal.password-recovery-enabled",()->Boolean.getBoolean("ism.browser.portal-recovery"));
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PortalInvitationService invites;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired io.github.turbopro.ism.integration.finance.ManualClearanceMapper finance;

    @Test void serveUntilBrowserFinishesThenVerifyPersistentEffects() throws Exception {
        Path control = Path.of(Objects.requireNonNull(System.getProperty("ism.browser.fixture-dir"))).toRealPath();
        try (var files = Files.list(control)) { assertThat(files.count()).isZero(); }
        jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(21001,'PORTAL-BROWSER','门户浏览器租户','ACTIVE')");
        jdbc.update("INSERT INTO plt_package(id,package_code,package_name,status) VALUES(21002,'PORTAL-BROWSER','门户套餐','ACTIVE')");
        jdbc.update("INSERT INTO plt_package_version(id,package_id,version_no,version_name,status) VALUES(21003,21002,1,'门户版本','PUBLISHED')");
        jdbc.update("INSERT INTO plt_package_module(package_version_id,module_id,enabled,quota_json) SELECT 21003,id,1,JSON_OBJECT() FROM plt_module WHERE module_code='SUPPLIER'");
        jdbc.update("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status,package_version_id) VALUES(21001,'PORTAL-BROWSER','门户浏览器租户','ACTIVE','READY',21003)");
        jdbc.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(21004,21001,'HQ','总部','HEADQUARTERS','ACTIVE')");
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(21005,21001,21004,'SUP','浏览器供应商','MANUFACTURER',9,9)");
        jdbc.update("UPDATE sup_supplier SET industry='化工制造',address='示例工业园区一号',remark='PRIVATE_INTERNAL_NOTE' WHERE id=21005");
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(21020,21001,21004,'OTHER','PRIVATE_OTHER_ENTERPRISE','TRADER',9,9)");
        jdbc.update("INSERT INTO sup_purchase_category(id,tenant_id,category_code,category_name,status) VALUES(21021,21001,'CHEMICAL','化工原料','ACTIVE'),(21022,21001,'OLD','PRIVATE_OLD_CATEGORY','INACTIVE')");
        jdbc.update("INSERT INTO sup_supplier_purchase_category(tenant_id,supplier_id,category_id) VALUES(21001,21005,21021),(21001,21005,21022)");
        boolean management=Boolean.getBoolean("ism.browser.portal-management");
        boolean exit=Boolean.getBoolean("ism.browser.portal-exit");
        boolean password=Boolean.getBoolean("ism.browser.portal-password");
        boolean recovery=Boolean.getBoolean("ism.browser.portal-recovery");
        if(management){
            jdbc.update("INSERT INTO iam_organization_closure(tenant_id,ancestor_id,descendant_id,depth) VALUES(21001,21004,21004,0)");
            for(int i=0;i<2;i++){
                jdbc.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(?,21001,?,?,?,'ACTIVE')",21006+i,i==0?"browser-admin":"browser-reader",i==0?"联调管理员":"联调只读用户",passwords.encode("Employee#Pass123"));
                jdbc.update("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(?,21001,?,?,0,'ACTIVE')",21008+i,"BROWSER-"+i,"联调角色"+i);
                jdbc.update("INSERT INTO iam_user_role(tenant_id,user_id,role_id) VALUES(21001,?,?)",21006+i,21008+i);
                jdbc.update("INSERT INTO iam_role_data_scope(tenant_id,role_id,resource_code,scope_type) VALUES(21001,?,'supplier:master','TENANT_ALL')",21008+i);
                jdbc.update("INSERT INTO iam_user_organization(tenant_id,user_id,organization_id,is_primary) VALUES(21001,?,21004,1)",21006+i);
                jdbc.update("INSERT INTO iam_user_context(tenant_id,user_id,current_organization_id) VALUES(21001,?,21004)",21006+i);
                jdbc.update("INSERT INTO iam_role_menu(tenant_id,role_id,menu_id) SELECT 21001,?,id FROM sys_menu WHERE menu_code='SUPPLIER_MASTER'",21008+i);
            }
            jdbc.update("INSERT INTO iam_role_permission(tenant_id,role_id,permission_id) SELECT 21001,21008,id FROM sys_permission WHERE status='ACTIVE' AND module_code IN ('SYSTEM','COMMON','SUPPLIER')");
            jdbc.update("INSERT INTO iam_role_permission(tenant_id,role_id,permission_id) SELECT 21001,21009,id FROM sys_permission WHERE status='ACTIVE' AND permission_code IN ('iam:organization:view','iam:menu:view','supplier:master:view','table:view:manage')");
        }
        if(exit){
            jdbc.update("INSERT INTO plt_package_module(package_version_id,module_id,enabled,quota_json) SELECT 21003,id,1,JSON_OBJECT() FROM plt_module WHERE module_code='RESTRICTION'");
            jdbc.update("INSERT INTO iam_role_permission(tenant_id,role_id,permission_id) SELECT 21001,21008,id FROM sys_permission WHERE status='ACTIVE' AND module_code='RESTRICTION'");
            jdbc.update("INSERT INTO iam_role_permission(tenant_id,role_id,permission_id) SELECT 21001,21009,id FROM sys_permission WHERE status='ACTIVE' AND permission_code IN ('supplier:exit:view','supplier:exit:review')");
            jdbc.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(21010,21001,21006,'exit.pdf','application/pdf',1,?,'LOCAL','test/exit','ACTIVE')","e".repeat(64));
            try(var tenant=TenantContext.open(21001,21006)){
                finance.insert(21001,21005,21010,"隔离浏览器验收人工财务证据",21006);
                finance.review(21001,21005,"APPROVED","独立人工复核",21007,0);
            }
        }
        PortalInvitationModels.Issued invitation;
        try(var tenant=TenantContext.open(21001,9); var grants=AuthorizationContext.open(new PermissionSnapshot(
                Set.of("supplier:master:update"), Map.of("supplier:master", DataScope.all()), Set.of()))) {
            invitation = invites.issue(21005,new PortalInvitationModels.Issue(0,"浏览器联系人","browser@example.test",24));
        }
        Path secret = control.resolve("invitation");
        Files.createFile(secret, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(secret,invitation.token());
        Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
        long deadline=System.nanoTime()+Duration.ofMinutes(5).toNanos();
        while(!Files.exists(control.resolve("finish")) && System.nanoTime()<deadline) {
            assertThat(Files.exists(control.resolve("abort"))).as("Browser runner aborted").isFalse();
            Thread.sleep(250);
        }
        assertThat(Files.exists(control.resolve("finish"))).as("Browser completion signal").isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_account WHERE tenant_id=21001 AND supplier_id=21005 AND username='browser.user'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=21001 AND status='ACCEPTED'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE tenant_id=21001",Long.class)).isEqualTo(recovery||password?4:exit?2:management?4:1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE tenant_id=21001 AND revoked_at IS NOT NULL",Long.class)).isEqualTo(recovery||password?4:exit?2:management?4:1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=21001 AND action='PORTAL_LOGIN'",Long.class)).isEqualTo(recovery||password?4:exit?2:management?4:1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=21001 AND action='PORTAL_LOGOUT'",Long.class)).isEqualTo(recovery||password||!exit&&management?2:1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user WHERE tenant_id=21001",Long.class)).isEqualTo(management?2:0);
        if(!management)assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_refresh_token WHERE tenant_id=21001",Long.class)).isZero();
        if(recovery){
            assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE tenant_id=21001",Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT token_version FROM iam_portal_account WHERE tenant_id=21001",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_password_recovery WHERE tenant_id=21001",Long.class)).isEqualTo(3);
            for(String state:List.of("REPLACED","REVOKED","CONSUMED"))assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_password_recovery WHERE tenant_id=21001 AND status=?",Long.class,state)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=21001 AND action='PORTAL_RECOVERY_ISSUE' AND operator_id=21006",Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=21001 AND action='PORTAL_RECOVERY_REVOKE' AND operator_id=21006",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=21001 AND action='PORTAL_RECOVERY_RESET' AND operator_id=(SELECT id FROM iam_portal_account WHERE tenant_id=21001)",Long.class)).isEqualTo(1);
            assertThat(passwords.matches("Recovered#Pass456",jdbc.queryForObject("SELECT password_hash FROM iam_portal_account WHERE tenant_id=21001",String.class))).isTrue();
        }else if(password){
            assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE tenant_id=21001",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT token_version FROM iam_portal_account WHERE tenant_id=21001",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=21001 AND action='PORTAL_PASSWORD_CHANGE' AND operator_id=(SELECT id FROM iam_portal_account WHERE tenant_id=21001)",Long.class)).isEqualTo(1);
            assertThat(passwords.matches("Changed#Pass456",jdbc.queryForObject("SELECT password_hash FROM iam_portal_account WHERE tenant_id=21001",String.class))).isTrue();
        }else if(exit){
            assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE tenant_id=21001",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT token_version FROM iam_portal_account WHERE tenant_id=21001",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_account WHERE tenant_id=21001",String.class)).isEqualTo("DISABLED");
            assertThat(jdbc.queryForObject("SELECT status FROM sup_supplier WHERE id=21005",String.class)).isEqualTo("EXITED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_application WHERE tenant_id=21001 AND status='BUSINESS_CLOSED' AND created_by=21006 AND reviewed_by=21007",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_archive WHERE tenant_id=21001 AND event_count=3 AND sealed_by=21007",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_result WHERE tenant_id=21001 AND access_recovery_status='NOT_VERIFIED'",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_access_recovery_task WHERE tenant_id=21001 AND status='DISCOVERY_REQUIRED'",Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=21001 AND status='REVOKED' AND revoked_by=21007",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=21001 AND action='SUPPLIER_PORTAL_ACCOUNT_EXIT_RECOVERY' AND operator_id=21007",Long.class)).isEqualTo(1);
        }else if(management){
            assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE tenant_id=21001",Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT token_version FROM iam_portal_account WHERE tenant_id=21001",Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_account WHERE tenant_id=21001",String.class)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=21001 AND action LIKE 'SUPPLIER_PORTAL_ACCOUNT_%' AND operator_id=21006",Long.class)).isEqualTo(3);
        }
        assertThat(jdbc.queryForObject("SELECT failed_count FROM iam_portal_account WHERE tenant_id=21001",Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT token_hash FROM iam_portal_session WHERE tenant_id=21001",String.class)).allMatch(hash->hash.matches("[a-f0-9]{64}"));
    }
}

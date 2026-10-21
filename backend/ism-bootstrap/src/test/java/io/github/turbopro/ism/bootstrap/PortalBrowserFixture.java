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
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PortalInvitationService invites;

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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE tenant_id=21001",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE tenant_id=21001 AND revoked_at IS NOT NULL",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=21001 AND action='PORTAL_LOGIN'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=21001 AND action='PORTAL_LOGOUT'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_refresh_token WHERE tenant_id=21001",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user WHERE tenant_id=21001",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT failed_count FROM iam_portal_account WHERE tenant_id=21001",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM iam_portal_session WHERE tenant_id=21001",String.class)).matches("[a-f0-9]{64}");
    }
}

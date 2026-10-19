package io.github.turbopro.ism.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit -Dit.test=ConsoleReminderBrowserFixture only; not part of normal *IT discovery. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false"})
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ConsoleReminderBrowserFixture {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("console_browser").withUsername("ism").withPassword("ism");
    private static final Path STORAGE=Path.of(System.getProperty("java.io.tmpdir"),"ism-browser-"+UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
        registry.add("ism.storage.local-root",STORAGE::toString);
        registry.add("server.address",()->"127.0.0.1");
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test void serveIsolatedBackendUntilBrowserFinishes() throws Exception {
        String directory=System.getProperty("ism.browser.fixture-dir");
        assertThat(directory).as("Pass an existing empty temporary directory with -Dism.browser.fixture-dir").isNotBlank();
        Path control=Path.of(directory).toRealPath();
        try(var files=Files.list(control)) { assertThat(files.count()).isZero(); }
        boolean recovery=Boolean.getBoolean("ism.browser.recovery");
        try {
            jdbc.update("INSERT INTO plt_package(id,package_code,package_name,status) VALUES(20600,'BROWSER','浏览器验收套餐','ACTIVE')");
            jdbc.update("INSERT INTO plt_package_version(id,package_id,version_no,version_name,status) VALUES(20601,20600,1,'联调版本','PUBLISHED')");
            jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(20602,'BROWSER','浏览器验收租户','ACTIVE')");
            jdbc.update("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status,package_version_id) VALUES(20602,'BROWSER','浏览器验收租户','ACTIVE','READY',20601)");
            jdbc.update("INSERT INTO plt_subscription(id,tenant_id,package_version_id,status,effective_from,effective_to,exception_json,assignment_sequence) VALUES(20603,20602,20601,'ACTIVE',UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)+INTERVAL 6 DAY,JSON_OBJECT(),1)");
            jdbc.update("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(20604,20602,'TENANT_ADMIN','租户管理员',1,'ACTIVE')");
            for(int i=0;i<2;i++) {
                jdbc.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(?,20602,?,?,'not-a-login-hash','ACTIVE')",20610+i,"recipient-"+i,"提醒收件人"+i);
                jdbc.update("INSERT INTO iam_user_role(tenant_id,user_id,role_id) VALUES(20602,?,20604)",20610+i);
                jdbc.update("INSERT INTO plt_user(id,username,display_name,password_hash,status) VALUES(?,?,?,?,'ACTIVE')",20620+i,i==0?"browser-admin":"browser-reader",i==0?"联调管理员":"联调只读人员",passwords.encode("Console#Pass123"));
                jdbc.update("INSERT INTO plt_user_role(user_id,role_id) VALUES(?,?)",20620+i,i==0?1001:1002);
            }
            if(recovery) jdbc.update("UPDATE iam_user SET status='DISABLED' WHERE tenant_id=20602");
            Files.writeString(control.resolve("scenario"),recovery?"failure-recovery":"success");
            Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
            long deadline=System.nanoTime()+Duration.ofMinutes(10).toNanos();
            boolean restored=false;
            while(!Files.exists(control.resolve("finish")) && System.nanoTime()<deadline) {
                assertThat(Files.exists(control.resolve("abort"))).as("Browser runner aborted").isFalse();
                if(recovery && !restored && Files.exists(control.resolve("restore"))) {
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM msg_delivery WHERE tenant_id=20602",Long.class)).isZero();
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=20602",Long.class)).isZero();
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_subscription_reminder WHERE subscription_id=20603 AND status='FAILED' AND attempt_count=1",Long.class)).isEqualTo(1);
                    jdbc.update("UPDATE iam_user SET status='ACTIVE' WHERE tenant_id=20602");
                    restored=true;
                    Files.writeString(control.resolve("restored"),"ready");
                }
                Thread.sleep(250);
            }
            assertThat(Files.exists(control.resolve("finish"))).as("Browser must signal finish within ten minutes").isTrue();
            if(recovery) assertThat(restored).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM msg_delivery WHERE tenant_id=20602 AND business_type='TENANT_SUBSCRIPTION'",Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=20602",Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_subscription_reminder WHERE subscription_id=20603 AND status='SENT' AND attempt_count=? AND recipient_count=2 AND error_code IS NULL",Long.class,recovery?2:1)).isEqualTo(1);
        } finally {
            if(Files.exists(STORAGE)) try(var files=Files.walk(STORAGE)) {
                for(var path:files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}

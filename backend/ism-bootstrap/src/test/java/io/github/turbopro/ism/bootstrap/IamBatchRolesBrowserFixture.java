package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.iam.access.AccessService;
import io.github.turbopro.ism.platform.tenant.TenantService;
import io.github.turbopro.ism.platform.packageplan.PackagePlanService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Explicitly selected by the browser runner, never part of the ordinary *IT suite. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false","server.address=127.0.0.1"})
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class IamBatchRolesBrowserFixture {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("batch_roles_browser").withUsername("ism").withPassword("ism");
    static final Path STORAGE=Path.of(System.getProperty("java.io.tmpdir"),"ism-batch-browser-"+UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("ism.storage.local-root",STORAGE::toString);}
    @AfterAll static void cleanup()throws Exception{if(Files.exists(STORAGE))try(var paths=Files.walk(STORAGE)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    @Autowired JdbcTemplate jdbc;@Autowired TenantService tenants;@Autowired PackagePlanService plans;@Autowired AccessService access;@LocalServerPort int port;
    @Test void realBrowserWorkflowAndFinalDatabaseAssertions()throws Exception{
        Path control=Path.of(Objects.requireNonNull(System.getProperty("ism.browser.fixture-dir"))).toRealPath();
        var f=IamBatchRoleData.seed(jdbc,tenants,plans,access);
        Files.writeString(control.resolve("fixture.json"),new ObjectMapper().writeValueAsString(Map.of("code",f.code(),"first",f.first(),"second",f.second(),"unchanged",f.unchanged(),"baseRole",f.baseRole())));
        Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
        long deadline=System.nanoTime()+Duration.ofMinutes(5).toNanos();
        while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(100);}
        assertThat(Files.exists(control.resolve("finish"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT version FROM iam_user WHERE id=?",Integer.class,Long.parseLong(f.first()))).isOne();
        assertThat(jdbc.queryForObject("SELECT version FROM iam_user WHERE id=?",Integer.class,Long.parseLong(f.second()))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT version FROM iam_user WHERE id=?",Integer.class,Long.parseLong(f.unchanged()))).isZero();
        assertThat(jdbc.queryForObject("SELECT token_version FROM iam_user WHERE id=?",Integer.class,Long.parseLong(f.second()))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='USER_BATCH_ROLE_ASSIGN'",Long.class,f.tenant())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_idempotency_record WHERE tenant_id=? AND operation_code='USER_BATCH_ROLE_ASSIGN'",Long.class,f.tenant())).isOne();
        for(String user:List.of(f.first(),f.second(),f.unchanged()))assertThat(jdbc.queryForObject("SELECT role_id FROM iam_user_role WHERE tenant_id=? AND user_id=?",Long.class,f.tenant(),Long.parseLong(user))).isEqualTo(Long.parseLong(f.targetRole()));
    }
}

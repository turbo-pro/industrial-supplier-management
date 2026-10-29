package io.github.turbopro.ism.bootstrap;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.platform.tenant.*;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.springframework.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false"})
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
abstract class SiteInitializationSupport {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("site_initialization").withUsername("ism").withPassword("ism");
    static final Path STORAGE=Path.of(System.getProperty("java.io.tmpdir"),"ism-site-init-"+UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("ism.storage.local-root",STORAGE::toString);}
    @AfterAll static void cleanup()throws Exception{if(Files.exists(STORAGE))try(var paths=Files.walk(STORAGE)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    @Autowired JdbcTemplate jdbc;@Autowired TestRestTemplate http;@Autowired PasswordEncoder passwords;@Autowired TenantService tenants;
    static final AtomicInteger IDS=new AtomicInteger();
    long pending(){int n=IDS.incrementAndGet();long pkg=700000+n*10L; jdbc.update("INSERT INTO plt_package(id,package_code,package_name,status) VALUES(?,?,'场站验收套餐','ACTIVE')",pkg,"SITE_"+n);jdbc.update("INSERT INTO plt_package_version(id,package_id,version_no,version_name,status) VALUES(?,?,1,'验收版','PUBLISHED')",pkg+1,pkg);return Long.parseLong(tenants.create(new TenantModels.CreateTenant("SITE-"+n,"场站验收"+n,"Asia/Shanghai","zh-CN",Long.toString(pkg+1))).id());}
    void consoleUsers(){if(jdbc.queryForObject("SELECT COUNT(*) FROM plt_user WHERE id=790001",Integer.class)>0)return;for(int i=0;i<2;i++){jdbc.update("INSERT INTO plt_user(id,username,display_name,password_hash,status,force_password_change) VALUES(?,?,?,?,'ACTIVE',0)",790001+i,"site-console-"+i,"场站开通验收"+i,passwords.encode("Console#Pass123"));jdbc.update("INSERT INTO plt_user_role(user_id,role_id) VALUES(?,?)",790001+i,i==0?1001:1002);}}
    String login(boolean admin){consoleUsers();var r=http.postForEntity("/api/console/auth/login",Map.of("username",admin?"site-console-0":"site-console-1","password","Console#Pass123","deviceId","site-tests"),JsonNode.class);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody().path("data").path("accessToken").asText();}
    Map<String,Object> body(String mode){var b=new HashMap<String,Object>();b.put("adminUsername","site-admin");b.put("adminDisplayName","首管理员");b.put("initialPassword","Site#Initial123");b.put("operationMode",mode);b.put("siteLabel","工厂");b.put("siteName","首个工厂");b.put("headquartersName","集团总部");return b;}
    ResponseEntity<JsonNode> initialize(long id,String token,String key,Map<String,Object> b){var h=new HttpHeaders();h.setBearerAuth(token);h.set("Idempotency-Key",key);return http.exchange("/api/console/tenants/"+id+"/initialize",HttpMethod.POST,new HttpEntity<>(b,h),JsonNode.class);}
    int count(String table,long tenant){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=?",Integer.class,tenant);}
    void assertStructure(long tenant,boolean single){
        assertThat(count("iam_organization",tenant)).isEqualTo(single?1:2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_organization WHERE tenant_id=? AND parent_id IS NULL AND organization_type=?",Integer.class,tenant,single?"SITE":"HEADQUARTERS")).isOne();
        long site=jdbc.queryForObject("SELECT default_site_id FROM plt_tenant WHERE id=?",Long.class,tenant);
        assertThat(jdbc.queryForObject("SELECT organization_type FROM iam_organization WHERE tenant_id=? AND id=?",String.class,tenant,site)).isEqualTo("SITE");
        assertThat(count("iam_organization_closure",tenant)).isEqualTo(single?1:3);
        assertThat(count("iam_user",tenant)).isOne();assertThat(count("iam_user_organization",tenant)).isOne();assertThat(count("iam_user_context",tenant)).isOne();
        long primary=jdbc.queryForObject("SELECT organization_id FROM iam_user_organization WHERE tenant_id=? AND is_primary=1",Long.class,tenant);
        assertThat(jdbc.queryForObject("SELECT current_organization_id FROM iam_user_context WHERE tenant_id=?",Long.class,tenant)).isEqualTo(primary);
        assertThat(jdbc.queryForObject("SELECT organization_type FROM iam_organization WHERE id=? AND tenant_id=?",String.class,primary,tenant)).isEqualTo(single?"SITE":"HEADQUARTERS");
        assertThat(jdbc.queryForObject("SELECT force_password_change FROM iam_user WHERE tenant_id=?",Integer.class,tenant)).isOne();
    }
}

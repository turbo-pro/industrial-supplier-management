package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.IndustrialSupplierManagementApplication;
import io.github.turbopro.ism.platform.tenant.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real sockets and independent application contexts/pools in one JVM, not a multi-machine load test. */
@Testcontainers
class SharedDatabaseAuthenticationIT {
 @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("shared_auth").withUsername("ism").withPassword("ism");
 private final RestTemplate http=httpClient();
 private static RestTemplate httpClient(){var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build());factory.setReadTimeout(java.time.Duration.ofSeconds(20));return new RestTemplate(factory);}
 private final String secret="isolated-foundation-key-"+UUID.randomUUID();
 SharedDatabaseAuthenticationIT(){http.setErrorHandler(new DefaultResponseErrorHandler(){@Override public boolean hasError(org.springframework.http.client.ClientHttpResponse response){return false;}});}
 ConfigurableApplicationContext node(Path storage){return new SpringApplicationBuilder(IndustrialSupplierManagementApplication.class).web(WebApplicationType.SERVLET).registerShutdownHook(false).run(
  "--server.port=0","--spring.datasource.url="+MYSQL.getJdbcUrl(),"--spring.datasource.username="+MYSQL.getUsername(),"--spring.datasource.password="+MYSQL.getPassword(),"--ism.storage.local-root="+storage,
  "--ism.security.jwt.secret="+secret,"--ism.print.worker-enabled=false","--ism.subscription-reminders.enabled=false","--ism.portal.session-cleanup.enabled=false","--spring.jmx.enabled=false");}
 String url(ConfigurableApplicationContext c){return "http://127.0.0.1:"+((WebServerApplicationContext)c).getWebServer().getPort();}
 ResponseEntity<JsonNode> call(String method,String url,String token,Object body){var h=new HttpHeaders();if(token!=null)h.setBearerAuth(token);return http.exchange(url,HttpMethod.valueOf(method),new HttpEntity<>(body,h),JsonNode.class);}
 JsonNode ok(ResponseEntity<JsonNode> r){assertThat(r.getStatusCode().value()).isEqualTo(200);assertThat(r.getBody()).isNotNull();return r.getBody().path("data");}
 JsonNode login(String node,String password){return ok(call("POST",node+"/api/auth/login",null,Map.of("tenantCode","SHARED-AUTH","username","shared-admin","password",password,"deviceId","shared-device")));}
 void me(String node,String token,int status){var r=call("GET",node+"/api/auth/me",token,null);assertThat(r.getStatusCode().value()).isEqualTo(status);if(status==200)assertThat(r.getBody().path("data").path("username").asText()).isEqualTo("shared-admin");}
 void readiness(String node){assertThat(call("GET",node+"/actuator/health/readiness",null,null).getStatusCode().value()).isEqualTo(200);}
 @Test @org.junit.jupiter.api.Timeout(300) void repeatBootstrapAndCrossNodeEmployeeConsoleRotationAndRevocation()throws Exception{
  Path storage=Files.createTempDirectory("ism-shared-auth-");ConfigurableApplicationContext a=null,b=null,c=null;
  try{
   a=node(storage.resolve("node-a"));String first=url(a);readiness(first);var j=a.getBean(JdbcTemplate.class);
   j.update("INSERT INTO plt_package(id,package_code,package_name,status) VALUES(700001,'SHARED_AUTH','隔离共享验证','ACTIVE')");j.update("INSERT INTO plt_package_version(id,package_id,version_no,version_name,status) VALUES(700002,700001,1,'基础系统','PUBLISHED')");
   var tenants=a.getBean(TenantService.class);long tenant=Long.parseLong(tenants.create(new TenantModels.CreateTenant("SHARED-AUTH","共享认证验收","Asia/Shanghai","zh-CN","700002")).id());tenants.initialize(tenant,new TenantModels.InitializeTenant("shared-admin","共享管理员","Bootstrap#Initial123","总部","SINGLE_SITE","工厂","验收工厂"));
   var initial=login(first,"Bootstrap#Initial123");assertThat(initial.path("user").path("passwordChangeRequired").asBoolean()).isTrue();assertThat(call("GET",first+"/api/configuration/dictionaries",initial.path("accessToken").asText(),null).getStatusCode().value()).isEqualTo(403);
   ok(call("POST",first+"/api/auth/change-password",initial.path("accessToken").asText(),Map.of("oldPassword","Bootstrap#Initial123","newPassword","Bootstrap#Changed123")));me(first,initial.path("accessToken").asText(),401);
   var pair=login(first,"Bootstrap#Changed123");String token=pair.path("accessToken").asText(),refresh=pair.path("refreshToken").asText();
   var history=j.queryForList("SELECT installed_rank,version,checksum,success FROM flyway_schema_history ORDER BY installed_rank");var userRows=j.queryForList("SELECT id,tenant_id,username,token_version FROM iam_user WHERE tenant_id=? ORDER BY id",tenant);
   b=node(storage.resolve("node-b"));String second=url(b);readiness(second);assertThat(b.getBean(JdbcTemplate.class).queryForList("SELECT installed_rank,version,checksum,success FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);me(second,token,200);
   var forged=call("GET",second+"/api/auth/me?tenantId=99999",token,null);assertThat(forged.getBody().path("data").path("tenantId").asText()).isEqualTo(Long.toString(tenant));
   a.close();a=null;me(second,token,200);
   var rotated=ok(call("POST",second+"/api/auth/refresh",null,Map.of("refreshToken",refresh,"deviceId","shared-device")));String next=rotated.path("accessToken").asText();
   c=node(storage.resolve("node-c"));String restarted=url(c);readiness(restarted);me(restarted,next,200);assertThat(c.getBean(JdbcTemplate.class).queryForList("SELECT installed_rank,version,checksum,success FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
   var replay=call("POST",restarted+"/api/auth/refresh",null,Map.of("refreshToken",refresh,"deviceId","shared-device"));assertThat(replay.getStatusCode().value()).isEqualTo(401);assertThat(replay.getBody().path("traceId").asText()).isNotBlank();assertThat(java.util.stream.Stream.of(refresh,next,secret).noneMatch(replay.getBody().toString()::contains)).isTrue();me(second,next,401);
   var independent=login(second,"Bootstrap#Changed123");me(restarted,independent.path("accessToken").asText(),200);ok(call("POST",restarted+"/api/auth/logout",independent.path("accessToken").asText(),Map.of("refreshToken",independent.path("refreshToken").asText())));me(second,independent.path("accessToken").asText(),401);
   var db=b.getBean(JdbcTemplate.class);db.update("INSERT INTO plt_user(id,username,display_name,password_hash,status,force_password_change) VALUES(790001,'shared-console','隔离平台管理员',?,'ACTIVE',0)",b.getBean(PasswordEncoder.class).encode("Console#Pass123"));db.update("INSERT INTO plt_user_role(user_id,role_id) VALUES(790001,1001)");
   var console=ok(call("POST",second+"/api/console/auth/login",null,Map.of("username","shared-console","password","Console#Pass123","deviceId","console-shared")));String consoleAccess=console.path("accessToken").asText();ok(call("GET",restarted+"/api/console/auth/me",consoleAccess,null));me(restarted,consoleAccess,401);
   var consoleRotated=ok(call("POST",restarted+"/api/console/auth/refresh",null,Map.of("refreshToken",console.path("refreshToken").asText(),"deviceId","console-shared")));assertThat(call("POST",second+"/api/console/auth/refresh",null,Map.of("refreshToken",console.path("refreshToken").asText(),"deviceId","console-shared")).getStatusCode().value()).isEqualTo(401);assertThat(call("GET",restarted+"/api/console/auth/me",consoleRotated.path("accessToken").asText(),null).getStatusCode().value()).isEqualTo(401);
   assertThat(db.queryForList("SELECT id,tenant_id,username,token_version FROM iam_user WHERE tenant_id=? ORDER BY id",tenant)).isEqualTo(userRows);assertThat(db.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='25'",Integer.class)).isEqualTo(1394665689);
  }finally{if(c!=null)c.close();if(b!=null)b.close();if(a!=null)a.close();try(var paths=Files.walk(storage)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}

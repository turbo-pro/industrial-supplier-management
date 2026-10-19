package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.iam.authorization.TenantNotificationRecipients;
import io.github.turbopro.ism.iam.console.ConsoleAuthModels;
import io.github.turbopro.ism.iam.console.ConsoleAuthService;
import io.github.turbopro.ism.operation.message.MessageService;
import io.github.turbopro.ism.platform.packageplan.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker=true)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SubscriptionReminderIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("subscription_reminders").withUsername("ism").withPassword("ism");
    private static final Path STORAGE=Path.of(System.getProperty("java.io.tmpdir"),"ism-reminder-it-"+UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
        registry.add("ism.storage.local-root",STORAGE::toString);
    }
    @AfterAll static void cleanup() throws Exception {
        if(Files.exists(STORAGE)) try(var files=Files.walk(STORAGE)) {
            for(var path:files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PackagePlanService plans;
    @Autowired SubscriptionReminderService reminders;
    @Autowired MessageService messages;
    @Autowired TenantNotificationRecipients recipients;
    @Autowired MockMvc mvc;
    @Autowired ConsoleAuthService consoleAuth;
    @Autowired PasswordEncoder passwords;
    @Autowired SubscriptionExpiryReminderWorker worker;
    @Autowired TestRestTemplate http;

    @Test void realHttpLoginDispatchReplayAndReadOnlyPermissions() {
        var f=fixture(20307);
        String path="/api/console/tenants/"+f.tenant()+"/subscriptions/"+f.subscription()+"/reminders";
        for(int i=0;i<2;i++) {
            long user=20500+i;
            String username="reminder-http-"+i;
            jdbc.update("INSERT INTO plt_user(id,username,display_name,password_hash,status) VALUES(?,?,?,?,'ACTIVE')",
                    user,username,"HTTP验收",passwords.encode("Console#Pass123"));
            jdbc.update("INSERT INTO plt_user_role(user_id,role_id) VALUES(?,?)",user,i==0?1001:1002);
            var login=http.postForEntity("/api/console/auth/login",
                    Map.of("username",username,"password","Console#Pass123","deviceId","http-reminder-"+i),JsonNode.class);
            assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
            String token=login.getBody().path("data").path("accessToken").asText();
            assertThat(token).isNotBlank();
            var headers=new HttpHeaders();headers.setBearerAuth(token);
            headers.set("Idempotency-Key","http-reminder-"+i);
            var request=new HttpEntity<Void>(headers);
            var dispatch=http.exchange(path+"/dispatch",HttpMethod.POST,request,JsonNode.class);
            assertThat(dispatch.getStatusCode()).isEqualTo(i==0?HttpStatus.OK:HttpStatus.FORBIDDEN);
            if(i==0) {
                assertThat(dispatch.getBody().path("data").path("status").asText()).isEqualTo("SENT");
                var replay=http.exchange(path+"/dispatch",HttpMethod.POST,request,JsonNode.class);
                assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(replay.getBody().path("data")).isEqualTo(dispatch.getBody().path("data"));
                headers.set("Idempotency-Key","http-reminder-new-check");
                var repeat=http.exchange(path+"/dispatch",HttpMethod.POST,new HttpEntity<Void>(headers),JsonNode.class);
                assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(repeat.getBody().path("data").path("status").asText()).isEqualTo("SKIPPED");
            }
            var history=http.exchange(path,HttpMethod.GET,request,JsonNode.class);
            assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(history.getBody().path("data").get(0).path("recipientCount").asInt()).isEqualTo(2);
            assertThat(http.exchange("/api/console/tenants/20308/subscriptions/"+f.subscription()+"/reminders",
                    HttpMethod.GET,request,JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(http.getForEntity(path,JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deliveries(f)).isEqualTo(2);
        assertThat(reminders.history(f.tenant(),f.subscription())).singleElement().satisfies(row->{
            assertThat(row.status()).isEqualTo("SENT");assertThat(row.attemptCount()).isEqualTo(1);
        });
    }

    record Fixture(long tenant,long subscription,long admin,long otherAdmin) {}
    private Fixture fixture(long tenant) {
        var plan=plans.create(new PackagePlanModels.CreatePackage("REMINDER_"+tenant,"订阅提醒验收"));
        var draft=plans.createVersion(Long.parseLong(plan.id()),new PackagePlanModels.CreateVersion(1,"提醒版本",null,
                List.of(new PackagePlanModels.ModuleGrant("SUPPLIER",true,Map.of()))));
        plans.publish(Long.parseLong(draft.id()),draft.version(),9001L);
        jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,'ACTIVE')",tenant,"REMINDER-"+tenant,"提醒租户");
        jdbc.update("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status,package_version_id) VALUES(?,?,?,'ACTIVE','READY',?)",
                tenant,"REMINDER-"+tenant,"提醒租户",Long.parseLong(draft.id()));
        long role=tenant*100+50,subscription=tenant*100+90;
        jdbc.update("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(?,?,'TENANT_ADMIN','租户管理员',1,'ACTIVE')",role,tenant);
        for(int n=1;n<=3;n++) {
            long user=tenant*100+n;
            jdbc.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(?,?,?,?,'unused',?)",
                    user,tenant,"reminder-"+n,"管理员"+n,n==3?"DISABLED":"ACTIVE");
            jdbc.update("INSERT INTO iam_user_role(tenant_id,user_id,role_id) VALUES(?,?,?)",tenant,user,role);
        }
        jdbc.update("INSERT INTO plt_subscription(id,tenant_id,package_version_id,status,effective_from,effective_to,exception_json,assignment_sequence) VALUES(?,?,?,'ACTIVE',UTC_TIMESTAMP(3)-INTERVAL 10 DAY,UTC_TIMESTAMP(3)+INTERVAL 6 DAY,JSON_OBJECT(),1)",
                subscription,tenant,Long.parseLong(draft.id()));
        return new Fixture(tenant,subscription,tenant*100+1,tenant*100+2);
    }
    private long deliveries(Fixture fixture) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM msg_delivery WHERE tenant_id=? AND business_type='TENANT_SUBSCRIPTION'",Long.class,fixture.tenant());
    }

    @Test void sendsEachStageOnceToActiveAdministratorsAndKeepsTenantsSeparate() {
        var f=fixture(20301);var foreign=fixture(20302);
        worker.poll();assertThat(deliveries(f)).isZero();
        jdbc.update("INSERT INTO msg_template(id,tenant_id,template_code,template_name,channel,title_template,content_template,variable_schema,status) VALUES(?,?,'TENANT_SUBSCRIPTION_EXPIRY','同名邮件模板','EMAIL','邮件模板','不能取代站内模板',JSON_ARRAY(),'ACTIVE')",f.tenant()*100+80,f.tenant());
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("SENT");
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("SKIPPED");
        assertThat(deliveries(f)).isEqualTo(2);assertThat(deliveries(foreign)).isZero();
        jdbc.update("UPDATE plt_subscription SET effective_to=UTC_TIMESTAMP(3)+INTERVAL 12 HOUR WHERE id=?",f.subscription());
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).stage()).isEqualTo("WITHIN_1_DAY");
        jdbc.update("UPDATE plt_subscription SET effective_to=UTC_TIMESTAMP(3)-INTERVAL 1 MINUTE WHERE id=?",f.subscription());
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).stage()).isEqualTo("EXPIRED");
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("SKIPPED");
        assertThat(deliveries(f)).isEqualTo(6);
        assertThat(reminders.history(f.tenant(),f.subscription())).hasSize(3).allSatisfy(row->{
            assertThat(row.status()).isEqualTo("SENT");assertThat(row.attemptCount()).isEqualTo(1);assertThat(row.recipientCount()).isEqualTo(2);
        });
        try(var context=TenantContext.open(f.tenant(),f.admin())) {
            assertThat(messages.inbox()).hasSize(3); assertThat(recipients.activeAdministrators(f.tenant())).containsExactlyInAnyOrder(f.admin(),f.otherAdmin());
            assertThatThrownBy(()->recipients.activeAdministrators(foreign.tenant())).isInstanceOf(RuntimeException.class);
        }
    }

    @Test void rejectsFutureCancelledInactiveAndSupersededCandidates() {
        var f=fixture(20303);
        jdbc.update("UPDATE plt_subscription SET effective_from=UTC_TIMESTAMP(3)+INTERVAL 1 DAY WHERE id=?",f.subscription());
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("SKIPPED");
        jdbc.update("UPDATE plt_subscription SET effective_from=UTC_TIMESTAMP(3)-INTERVAL 1 DAY,status='CANCELLED' WHERE id=?",f.subscription());
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("SKIPPED");
        jdbc.update("UPDATE plt_subscription SET status='ACTIVE' WHERE id=?",f.subscription());
        jdbc.update("UPDATE plt_tenant SET status='SUSPENDED' WHERE id=?",f.tenant());
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("SKIPPED");
        jdbc.update("UPDATE plt_tenant SET status='ACTIVE' WHERE id=?",f.tenant());
        jdbc.update("UPDATE plt_subscription SET effective_to=NULL WHERE id=?",f.subscription());
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("SKIPPED");
        jdbc.update("UPDATE plt_subscription SET effective_to=UTC_TIMESTAMP(3)+INTERVAL 6 DAY WHERE id=?",f.subscription());
        jdbc.update("INSERT INTO plt_subscription(id,tenant_id,package_version_id,status,effective_from,effective_to,exception_json,assignment_sequence) SELECT ?,tenant_id,package_version_id,'ACTIVE',UTC_TIMESTAMP(3)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(3)+INTERVAL 8 DAY,JSON_OBJECT(),2 FROM plt_subscription WHERE id=?",f.subscription()+1,f.subscription());
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("SKIPPED");
        assertThat(deliveries(f)).isZero();
        assertThat(reminders.candidates(0,100)).noneMatch(row->row.tenantId()==f.tenant());
    }

    @Test void rollsBackPartialDeliveryRecordsFailureAndRecoversWithoutDuplication() throws Exception {
        var f=fixture(20304);
        String trigger="CREATE TRIGGER reminder_delivery_failure BEFORE INSERT ON msg_inbox FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+
                " AND (SELECT COUNT(*) FROM msg_delivery WHERE tenant_id="+f.tenant()+" AND business_type='TENANT_SUBSCRIPTION')=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected inbox failure'; END IF; END";
        var install=MYSQL.execInContainer("mysql","--delimiter=//","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e",trigger+"//");
        assertThat(install.getExitCode()).as(install.getStderr()).isZero();
        try {
            assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("FAILED");
            assertThat(deliveries(f)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=?",Long.class,f.tenant())).isZero();
            assertThat(reminders.history(f.tenant(),f.subscription())).singleElement().satisfies(row->{
                assertThat(row.status()).isEqualTo("FAILED");assertThat(row.attemptCount()).isEqualTo(1);assertThat(row.errorCode()).isEqualTo("DELIVERY_FAILED");
            });
            assertThat(reminders.candidates(0,100)).noneMatch(row->row.subscriptionId()==f.subscription());
        } finally {
            assertThat(MYSQL.execInContainer("mysql","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e","DROP TRIGGER reminder_delivery_failure").getExitCode()).isZero();
        }
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("SENT");
        assertThat(deliveries(f)).isEqualTo(2);
        assertThat(reminders.history(f.tenant(),f.subscription())).singleElement().satisfies(row->{
            assertThat(row.status()).isEqualTo("SENT");assertThat(row.attemptCount()).isEqualTo(2);assertThat(row.errorCode()).isNull();
        });
    }

    @Test void concurrentWorkersSendOnceAndConsolePermissionsProtectManualChecks() throws Exception {
        var f=fixture(20305);
        var start=new CountDownLatch(1);var ready=new CountDownLatch(2);var pool=Executors.newFixedThreadPool(2);
        try {
            var results=new ArrayList<Future<SubscriptionReminderModels.Dispatch>>();
            for(int i=0;i<2;i++) results.add(pool.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("barrier");return reminders.dispatch(f.tenant(),f.subscription());}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            var statuses=List.of(results.get(0).get(15,TimeUnit.SECONDS).status(),results.get(1).get(15,TimeUnit.SECONDS).status());
            assertThat(statuses).containsExactlyInAnyOrder("SENT","SKIPPED");assertThat(deliveries(f)).isEqualTo(2);
        } finally {start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        String path="/api/console/tenants/"+f.tenant()+"/subscriptions/"+f.subscription()+"/reminders";
        for(int i=0;i<2;i++) {
            long user=20400+i;
            jdbc.update("INSERT INTO plt_user(id,username,display_name,password_hash,status) VALUES(?,?,?,?,'ACTIVE')",user,"reminder-console-"+i,"平台验收",passwords.encode("Console#Pass123"));
            jdbc.update("INSERT INTO plt_user_role(user_id,role_id) VALUES(?,?)",user,i==0?1001:1002);
            String token="Bearer "+consoleAuth.login(new ConsoleAuthModels.LoginCommand("reminder-console-"+i,"Console#Pass123","reminder-device-"+i),"127.0.0.1").accessToken();
            mvc.perform(get(path).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("SENT"));
            mvc.perform(post(path+"/dispatch").header("Authorization",token).header("Idempotency-Key","reminder-manual-"+i))
                    .andExpect(i==0?status().isOk():status().isForbidden());
            mvc.perform(get("/api/console/tenants/20301/subscriptions/"+f.subscription()+"/reminders").header("Authorization",token)).andExpect(status().isNotFound());
        }
    }

    @Test void missingAdministratorsRemainVisibleAndNewAdministratorCanRecoverDelivery() {
        var f=fixture(20306);
        jdbc.update("UPDATE iam_user SET deleted=1 WHERE id=?",f.admin());
        jdbc.update("UPDATE iam_user SET status='DISABLED' WHERE id=?",f.otherAdmin());
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).status()).isEqualTo("FAILED");
        assertThat(deliveries(f)).isZero();
        assertThat(reminders.history(f.tenant(),f.subscription())).singleElement().satisfies(row->assertThat(row.status()).isEqualTo("FAILED"));
        jdbc.update("UPDATE iam_user SET status='ACTIVE' WHERE id=?",f.otherAdmin());
        assertThat(reminders.dispatch(f.tenant(),f.subscription()).recipientCount()).isEqualTo(1);
        assertThat(deliveries(f)).isEqualTo(1);
        assertThat(reminders.history(f.tenant(),f.subscription())).singleElement().satisfies(row->{
            assertThat(row.status()).isEqualTo("SENT");assertThat(row.attemptCount()).isEqualTo(2);
        });
    }
}

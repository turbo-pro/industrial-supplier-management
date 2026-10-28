package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Duration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"ism.print.worker-enabled=false", "ism.subscription-reminders.enabled=false"})
@Testcontainers(disabledWithoutDocker=true)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class DispatchLeaseIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("dispatch_lease_it").withUsername("ism").withPassword("ism");
    static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"), "ism-lease-it-" + UUID.randomUUID());
    static final long TENANT = 44000;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ism.storage.local-root", STORAGE::toString);
    }
    @AfterAll static void cleanupStorage() throws Exception {
        if (Files.exists(STORAGE)) try (var paths = Files.walk(STORAGE)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired io.github.turbopro.ism.platform.tenant.TenantService tenants;
    @Autowired io.github.turbopro.ism.platform.packageplan.PackagePlanService plans;
    @Autowired AsyncTaskService tasks;
    @Autowired OutboxService outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired io.github.turbopro.ism.operation.task.TaskCenterService center;
    @BeforeEach void cleanupRows() {
        jdbc.update("DELETE FROM ops_async_task WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM sys_outbox_event WHERE tenant_id=?", TENANT);
    }
    long submit() {
        try (var scope = TenantContext.open(TENANT, 44001)) {
            return tasks.submit("LEASE_PROBE", Map.of("tenant", TENANT), 0);
        }
    }
    void enqueue() {
        try (var scope = TenantContext.open(TENANT, 44001)) {
            outbox.enqueue("LeaseProbe", 1, "PROBE", 1, Map.of("safe", true));
        }
    }
    void expireTask(long id) { jdbc.update("UPDATE ops_async_task SET lease_until=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE id=?", id); }
    void expireEvent(long id) { jdbc.update("UPDATE sys_outbox_event SET lease_until=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE id=?", id); }
    Map<String,Object> taskRow(long id) { return jdbc.queryForMap("SELECT * FROM ops_async_task WHERE id=?", id); }
    Map<String,Object> eventRow(long id) { return jdbc.queryForMap("SELECT * FROM sys_outbox_event WHERE id=?", id); }

    @Test void expiredTaskCannotWriteAnyProgressFileFailureCompletionOrRenewal() {
        long id = submit();
        var claim = tasks.claimNext("LEASE_PROBE", "node", Duration.ofMinutes(1)).orElseThrow();
        assertThat(claim.status()).isEqualTo("RUNNING");
        assertThat(claim.version()).isOne();
        assertThat(claim.leaseUntil()).isNotNull();
        assertThat(claim.leaseOwner()).startsWith("node:");
        assertThat(tasks.progress(id, claim.leaseOwner(), 20, "running")).isTrue();
        expireTask(id);
        var before = taskRow(id);
        assertThat(tasks.progress(id, claim.leaseOwner(), 90, "stale")).isFalse();
        assertThat(tasks.attachResult(id, claim.leaseOwner(), 998)).isFalse();
        assertThat(tasks.fail(id, claim.leaseOwner(), "STALE", "must not fail")).isFalse();
        assertThat(tasks.complete(id, claim.leaseOwner())).isFalse();
        assertThat(tasks.renew(id, claim.leaseOwner(), Duration.ofMinutes(1))).isFalse();
        assertThat(taskRow(id)).isEqualTo(before);
    }

    @Test void sameNodeReclaimFencesOriginalAttemptAndReturnsFreshSnapshot() {
        long id = submit();
        var old = tasks.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
        expireTask(id);
        var current = tasks.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
        assertThat(current.id()).isEqualTo(id);
        assertThat(current.version()).isEqualTo(2);
        assertThat(current.leaseOwner()).isNotEqualTo(old.leaseOwner());
        var before = taskRow(id);
        assertThat(tasks.progress(id, old.leaseOwner(), 99, "stale")).isFalse();
        assertThat(tasks.attachResult(id, old.leaseOwner(), 998)).isFalse();
        assertThat(tasks.fail(id, old.leaseOwner(), "STALE", "stale")).isFalse();
        assertThat(tasks.complete(id, old.leaseOwner())).isFalse();
        assertThat(tasks.renew(id, old.leaseOwner(), Duration.ofMinutes(1))).isFalse();
        assertThat(tasks.complete(id, "node")).isFalse();
        assertThat(taskRow(id)).isEqualTo(before);
        assertThat(tasks.renew(id, current.leaseOwner(), Duration.ofMinutes(2))).isTrue();
        assertThat(tasks.progress(id, current.leaseOwner(), 80, "current")).isTrue();
        assertThat(tasks.attachResult(id, current.leaseOwner(), 999)).isTrue();
        assertThat(tasks.complete(id, current.leaseOwner())).isTrue();
        assertThat(taskRow(id)).containsEntry("status", "SUCCEEDED").containsEntry("result_file_id", 999L);
        assertThat(tasks.complete(id, current.leaseOwner())).isFalse();
    }

    @Test void expiredOutboxAndSameNodeReclaimRejectStaleAcknowledgments() {
        enqueue();
        var old = outbox.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
        assertThat(old.status()).isEqualTo("PROCESSING");
        expireEvent(old.id());
        var expired = eventRow(old.id());
        assertThat(outbox.markPublished(old.id(), old.leaseOwner())).isFalse();
        assertThat(outbox.markFailed(old.id(), old.leaseOwner(), 0, 3, Duration.ZERO, "STALE")).isFalse();
        assertThat(eventRow(old.id())).isEqualTo(expired);
        var current = outbox.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
        assertThat(current.leaseOwner()).isNotEqualTo(old.leaseOwner());
        var before = eventRow(old.id());
        assertThat(outbox.markPublished(old.id(), old.leaseOwner())).isFalse();
        assertThat(outbox.markFailed(old.id(), old.leaseOwner(), 0, 3, Duration.ZERO, "STALE")).isFalse();
        // A stale retry count must not select an incorrect terminal status.
        assertThat(outbox.markFailed(current.id(), current.leaseOwner(), 2, 3, Duration.ZERO, "WRONG_COUNT")).isFalse();
        assertThat(eventRow(old.id())).isEqualTo(before);
        assertThat(outbox.markFailed(current.id(), current.leaseOwner(), 0, 2, Duration.ZERO, "RETRY")).isTrue();
        var retry = outbox.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
        assertThat(retry.retryCount()).isOne();
        assertThat(outbox.markFailed(retry.id(), retry.leaseOwner(), 1, 2, Duration.ZERO, "FINAL")).isTrue();
        assertThat(eventRow(old.id())).containsEntry("status", "DEAD").containsEntry("retry_count", 2);
        assertThat(outbox.claimNext("other", Duration.ofMinutes(1))).isEmpty();
    }

    @Test void concurrentNodesOnlyClaimOneTaskAndOneOutboxEvent() throws Exception {
        long id = submit();
        enqueue();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> claimBoth("a", ready, start));
            var b = pool.submit(() -> claimBoth("b", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var first = a.get(20, TimeUnit.SECONDS);
            var second = b.get(20, TimeUnit.SECONDS);
            assertThat(first.get(0) + second.get(0)).isOne();
            assertThat(first.get(1) + second.get(1)).isOne();
            assertThat(taskRow(id)).containsEntry("status", "RUNNING").containsEntry("version", 1);
        } finally { pool.shutdownNow(); }
    }
    List<Integer> claimBoth(String node, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
        return List.of(tasks.claimNext(node, Duration.ofMinutes(1)).isPresent() ? 1 : 0,
                outbox.claimNext(node, Duration.ofMinutes(1)).isPresent() ? 1 : 0);
    }

    io.github.turbopro.ism.operation.task.TaskCenterModels.TaskView task(long id) {
        try (var scope = TenantContext.open(TENANT, 44001)) { return center.task(id); }
    }
    io.github.turbopro.ism.operation.task.TaskCenterModels.TaskView cancel(long id, int version) {
        try (var scope = TenantContext.open(TENANT, 44001)) { return center.cancel(id, version); }
    }
    io.github.turbopro.ism.operation.task.TaskCenterModels.TaskView retry(long id, int version) {
        try (var scope = TenantContext.open(TENANT, 44001)) { return center.retry(id, version); }
    }

    @Test void queuedCancellationHasFinishTimeAndExactlyThreeRetries() {
        long id = submit();
        for (int attempt = 0; attempt <= 3; attempt++) {
            var current = task(id);
            assertThat(current.retryCount()).isEqualTo(attempt);
            var cancelled = cancel(id, current.version());
            assertThat(cancelled.status()).isEqualTo("CANCELLED");
            assertThat(cancelled.finishedAt()).isNotNull();
            assertThat(tasks.claimNext("node", Duration.ofMinutes(1))).isEmpty();
            if (attempt < 3) {
                var queued = retry(id, cancelled.version());
                assertThat(queued.status()).isEqualTo("QUEUED");
                assertThat(queued.retryCount()).isEqualTo(attempt + 1);
                assertThat(queued.finishedAt()).isNull();
                assertThatThrownBy(() -> retry(id, cancelled.version())).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            } else {
                assertThatThrownBy(() -> retry(id, cancelled.version())).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            }
        }
    }

    @Test void everyWorkerCheckpointSettlesRunningCancellationAndNeverPublishesResult() {
        for (int checkpoint = 0; checkpoint < 5; checkpoint++) {
            long id = submit();
            var claim = tasks.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
            assertThat(tasks.attachResult(id, claim.leaseOwner(), 888)).isTrue();
            var cancelling = cancel(id, task(id).version());
            assertThat(cancelling.status()).isEqualTo("RUNNING");
            assertThat(cancelling.cancelRequested()).isTrue();
            boolean written = switch (checkpoint) {
                case 0 -> tasks.progress(id, claim.leaseOwner(), 90, "after cancel");
                case 1 -> tasks.renew(id, claim.leaseOwner(), Duration.ofMinutes(1));
                case 2 -> tasks.attachResult(id, claim.leaseOwner(), 999);
                case 3 -> tasks.fail(id, claim.leaseOwner(), "ERROR", "after cancel");
                default -> tasks.complete(id, claim.leaseOwner());
            };
            assertThat(written).isFalse();
            var terminal = task(id);
            assertThat(terminal.status()).isEqualTo("CANCELLED");
            assertThat(terminal.finishedAt()).isNotNull();
            assertThat(terminal.resultFileId()).isNull();
            assertThat(terminal.retryCount()).isZero();
            assertThat(taskRow(id)).containsEntry("lease_owner", null).containsEntry("lease_until", null);
            var queued = retry(id, terminal.version());
            assertThat(queued.resultFileId()).isNull();
            assertThat(queued.retryCount()).isOne();
            var next = tasks.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
            assertThat(next.id()).isEqualTo(id);
            assertThat(tasks.complete(id, claim.leaseOwner())).isFalse();
            assertThat(tasks.complete(id, next.leaseOwner())).isTrue();
        }
    }

    @Test void failedTaskCountsActualRetriesAndClearsPreviousResult() {
        long id = submit();
        for (int attempt = 0; attempt <= 3; attempt++) {
            var claim = tasks.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
            assertThat(tasks.attachResult(id, claim.leaseOwner(), 888)).isTrue();
            assertThat(tasks.fail(id, claim.leaseOwner(), "SAFE_CODE", "render unavailable")).isTrue();
            var failed = task(id);
            assertThat(failed.status()).isEqualTo("FAILED");
            assertThat(failed.resultFileId()).isNull();
            assertThat(failed.retryCount()).isEqualTo(attempt);
            if (attempt < 3) {
                var queued = retry(id, failed.version());
                assertThat(queued.resultFileId()).isNull();
                assertThat(queued.errorCode()).isNull();
                assertThat(queued.startedAt()).isNull();
            } else {
                assertThatThrownBy(() -> retry(id, failed.version())).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            }
        }
    }

    @Test void supersededLeaseCannotSettleCancellationForTheNewLease() {
        long id = submit();
        var old = tasks.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
        expireTask(id);
        var current = tasks.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
        cancel(id, current.version());
        var before = taskRow(id);
        assertThat(tasks.progress(id, old.leaseOwner(), 50, "old")).isFalse();
        assertThat(taskRow(id)).isEqualTo(before);
        assertThat(tasks.progress(id, current.leaseOwner(), 50, "current")).isFalse();
        assertThat(task(id).status()).isEqualTo("CANCELLED");
    }

    @Test void taskOperationsRejectOtherActorAndOtherTenant() {
        long id = submit();
        for (var identity : List.of(new long[]{TENANT, 44002}, new long[]{TENANT + 1, 44001})) {
            try (var scope = TenantContext.open(identity[0], identity[1])) {
                assertThatThrownBy(() -> center.task(id)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                assertThatThrownBy(() -> center.cancel(id, 0)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                assertThatThrownBy(() -> center.retry(id, 0)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            }
        }
        assertThat(task(id).status()).isEqualTo("QUEUED");
        assertThat(task(id).version()).isZero();
    }

    @Test void cancellationRacingCompletionNeverLeavesSuccessfulCancelledTask() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int iteration = 0; iteration < 5; iteration++) {
                long id = submit();
                var claim = tasks.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                var cancelling = pool.submit(() -> {
                    ready.countDown(); start.await(10, TimeUnit.SECONDS);
                    try { cancel(id, claim.version()); return true; }
                    catch (io.github.turbopro.ism.common.api.error.ApiException conflict) { return false; }
                });
                var completing = pool.submit(() -> {
                    ready.countDown(); start.await(10, TimeUnit.SECONDS);
                    return tasks.complete(id, claim.leaseOwner());
                });
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
                boolean cancelled = cancelling.get(20, TimeUnit.SECONDS);
                boolean completed = completing.get(20, TimeUnit.SECONDS);
                assertThat(cancelled).isNotEqualTo(completed);
                assertThat(task(id).status()).isEqualTo(cancelled ? "CANCELLED" : "SUCCEEDED");
                assertThat(task(id).cancelRequested()).isEqualTo(cancelled);
            }
        } finally { pool.shutdownNow(); }
    }

    @Test void realHttpLoginCancelCheckpointRetryAndPermissionRevocation() {
        var plan = plans.create(new io.github.turbopro.ism.platform.packageplan.PackagePlanModels.CreatePackage("LEASE_HTTP", "任务接口验收"));
        var draft = plans.createVersion(Long.parseLong(plan.id()), new io.github.turbopro.ism.platform.packageplan.PackagePlanModels.CreateVersion(
                1, "验收版", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1),
                List.of(new io.github.turbopro.ism.platform.packageplan.PackagePlanModels.ModuleGrant("RESOURCE", true, Map.of()))));
        var published = plans.publish(Long.parseLong(draft.id()), draft.version(), 9001);
        var tenant = tenants.create(new io.github.turbopro.ism.platform.tenant.TenantModels.CreateTenant("LEASE-HTTP", "任务接口租户", "Asia/Shanghai", "zh-CN", published.id()));
        long tenantId = Long.parseLong(tenant.id());
        tenants.initialize(tenantId, new io.github.turbopro.ism.platform.tenant.TenantModels.InitializeTenant("lease-admin", "验收管理员", "Initial#Lease123", "验收组织"));
        var loginCommand = Map.of("tenantCode", "LEASE-HTTP", "username", "lease-admin", "password", "Initial#Lease123", "deviceId", "lease-http");
        var login = http.postForEntity("/api/auth/login", loginCommand, com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(login.getBody().path("data").path("accessToken").asText());
        var changed = http.postForEntity("/api/auth/change-password", new org.springframework.http.HttpEntity<>(
                Map.of("oldPassword", "Initial#Lease123", "newPassword", "Updated#Lease456"), headers), com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(changed.getStatusCode().value()).isEqualTo(200);
        var relogin = http.postForEntity("/api/auth/login", Map.of("tenantCode", "LEASE-HTTP", "username", "lease-admin", "password", "Updated#Lease456", "deviceId", "lease-http"), com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(relogin.getStatusCode().value()).isEqualTo(200);
        headers.setBearerAuth(relogin.getBody().path("data").path("accessToken").asText());
        long user = jdbc.queryForObject("SELECT id FROM iam_user WHERE tenant_id=? AND username='lease-admin'", Long.class, tenantId);
        long id;
        try (var scope = TenantContext.open(tenantId, user)) { id = tasks.submit("HTTP_LEASE_PROBE", Map.of(), 0); }
        var claim = tasks.claimNext("HTTP_LEASE_PROBE", "worker", Duration.ofMinutes(1)).orElseThrow();
        String path = "/api/tasks/" + id;
        var cancelled = http.postForEntity(path + "/cancel", new org.springframework.http.HttpEntity<>(Map.of("version", claim.version()), headers), com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(cancelled.getStatusCode().value()).isEqualTo(200);
        assertThat(cancelled.getBody().path("data").path("cancelRequested").asBoolean()).isTrue();
        assertThat(tasks.complete(id, claim.leaseOwner())).isFalse();
        var detail = http.exchange(path, org.springframework.http.HttpMethod.GET, new org.springframework.http.HttpEntity<>(headers), com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(detail.getBody().path("data").path("status").asText()).isEqualTo("CANCELLED");
        int version = detail.getBody().path("data").path("version").asInt();
        var retried = http.postForEntity(path + "/retry", new org.springframework.http.HttpEntity<>(Map.of("version", version), headers), com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(retried.getStatusCode().value()).isEqualTo(200);
        assertThat(retried.getBody().path("data").path("retryCount").asInt()).isOne();
        var replay = http.postForEntity(path + "/retry", new org.springframework.http.HttpEntity<>(Map.of("version", version), headers), com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(replay.getStatusCode().value()).isEqualTo(409);
        var next = tasks.claimNext("HTTP_LEASE_PROBE", "worker", Duration.ofMinutes(1)).orElseThrow();
        assertThat(tasks.complete(id, claim.leaseOwner())).isFalse();
        assertThat(tasks.complete(id, next.leaseOwner())).isTrue();
        assertThat(http.getForEntity(path, com.fasterxml.jackson.databind.JsonNode.class).getStatusCode().value()).isEqualTo(401);
        // Permission changes must apply to an already-issued access token.
        jdbc.update("DELETE rp FROM iam_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.tenant_id=? AND p.permission_code='task:center:operate'", tenantId);
        var denied = http.postForEntity(path + "/cancel", new org.springframework.http.HttpEntity<>(Map.of("version", next.version()), headers), com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
    }

    @Test void invalidLeaseAndRetryPoliciesDoNotMutateOrClaimRows() {
        long id = submit();
        enqueue();
        for (Duration duration : List.of(Duration.ZERO, Duration.ofNanos(1), Duration.ofSeconds(-1), Duration.ofDays(2))) {
            assertThatIllegalArgumentException().isThrownBy(() -> tasks.claimNext("node", duration));
            assertThatIllegalArgumentException().isThrownBy(() -> outbox.claimNext("node", duration));
        }
        for (String node : List.of("", " ", "n".repeat(64))) {
            assertThatIllegalArgumentException().isThrownBy(() -> tasks.claimNext(node, Duration.ofMinutes(1)));
            assertThatIllegalArgumentException().isThrownBy(() -> outbox.claimNext(node, Duration.ofMinutes(1)));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> tasks.claimNext("node", null));
        assertThatIllegalArgumentException().isThrownBy(() -> outbox.claimNext(null, Duration.ofMinutes(1)));
        assertThat(taskRow(id)).containsEntry("status", "QUEUED").containsEntry("version", 0);
        var event = outbox.claimNext("node", Duration.ofMinutes(1)).orElseThrow();
        var before = eventRow(event.id());
        assertThatIllegalArgumentException().isThrownBy(() -> outbox.markFailed(event.id(), event.leaseOwner(), -1, 3, Duration.ZERO, "INVALID"));
        assertThatIllegalArgumentException().isThrownBy(() -> outbox.markFailed(event.id(), event.leaseOwner(), 0, 0, Duration.ZERO, "INVALID"));
        assertThatIllegalArgumentException().isThrownBy(() -> outbox.markFailed(event.id(), event.leaseOwner(), 0, 3, Duration.ofSeconds(-1), "INVALID"));
        assertThat(eventRow(event.id())).isEqualTo(before);
        assertThat(outbox.markPublished(event.id(), event.leaseOwner())).isTrue();
    }
}

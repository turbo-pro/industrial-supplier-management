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

@SpringBootTest(properties={"ism.print.worker-enabled=false", "ism.subscription-reminders.enabled=false"})
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
    @Autowired AsyncTaskService tasks;
    @Autowired OutboxService outbox;
    @Autowired JdbcTemplate jdbc;
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

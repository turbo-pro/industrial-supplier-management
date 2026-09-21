package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.bootstrap.schema.SchemaMarker;
import io.github.turbopro.ism.bootstrap.schema.SchemaMarkerMapper;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantIsolationException;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationGrantLoader;
import io.github.turbopro.ism.common.infrastructure.authorization.DataScope;
import io.github.turbopro.ism.common.infrastructure.authorization.DataTarget;
import io.github.turbopro.ism.common.infrastructure.authorization.PermissionGuard;
import io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.authorization.SensitiveDataMasker;
import io.github.turbopro.ism.iam.auth.AuthModels;
import io.github.turbopro.ism.iam.auth.AuthService;
import io.github.turbopro.ism.iam.auth.IamErrorCode;
import io.github.turbopro.ism.iam.auth.JwtTokenService;
import io.github.turbopro.ism.operation.AsyncTaskService;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.IdempotencyService;
import io.github.turbopro.ism.operation.OperationModels;
import io.github.turbopro.ism.operation.OutboxService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({B0InfrastructureIT.TenantProbeController.class, B0InfrastructureIT.PermissionProbeController.class})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class B0InfrastructureIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("industrial_supplier")
            .withUsername("ism")
            .withPassword("ism");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private SchemaMarkerMapper schemaMarkerMapper;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TenantIsolationTestMapper tenantMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthorizationGrantLoader grantLoader;

    @Autowired
    private AuditService auditService;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private AsyncTaskService asyncTaskService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldMigrateDatabaseAndReadWriteThroughMyBatis() {
        String markerCode = "B0_MYBATIS_INTEGRATION";
        schemaMarkerMapper.deleteByCode(markerCode);

        assertThat(schemaMarkerMapper.insert(2L, markerCode)).isOne();

        SchemaMarker marker = schemaMarkerMapper.findByCode(markerCode);
        assertThat(marker).isNotNull();
        assertThat(marker.id()).isEqualTo(2L);
        assertThat(marker.markerCode()).isEqualTo(markerCode);
        assertThat(marker.createdAt()).isNotNull();

        assertThat(schemaMarkerMapper.deleteByCode(markerCode)).isOne();
    }

    @Test
    void shouldDeployStartAndCompleteFlowableProcess() {
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("processes/b0-smoke-process.bpmn20.xml")
                .name("b0-infrastructure-test")
                .deploy();

        try {
            ProcessInstance process = runtimeService.startProcessInstanceByKey("b0SmokeProcess");
            Task task = taskService.createTaskQuery()
                    .processInstanceId(process.getId())
                    .singleResult();

            assertThat(task).isNotNull();
            assertThat(task.getAssignee()).isEqualTo("b0-tester");

            taskService.complete(task.getId());
            assertThat(runtimeService.createProcessInstanceQuery()
                    .processInstanceId(process.getId())
                    .count()).isZero();
        } finally {
            repositoryService.deleteDeployment(deployment.getId(), true);
        }
    }

    @Test
    void shouldProtectLoginRotateRefreshTokenAndInvalidateTokensAfterPasswordChange() {
        long tenantId = 100L;
        long userId = 101L;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantId, "T-A", "测试租户A", "ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status,force_password_change)
                VALUES(?,?,?,?,?,?,?)
                """, userId, tenantId, "admin", "租户管理员", passwordEncoder.encode("Initial#Pass123"),
                "ACTIVE", true);
        try {
            assertThatThrownBy(() -> authService.login(
                    new AuthModels.LoginCommand("T-A", "missing", "Wrong#Pass123", "device-a"), "127.0.0.1"))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(IamErrorCode.INVALID_CREDENTIALS));
            assertThatThrownBy(() -> authService.login(
                    new AuthModels.LoginCommand("T-A", "admin", "Wrong#Pass123", "device-a"), "127.0.0.1"))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(IamErrorCode.INVALID_CREDENTIALS));

            AuthModels.TokenPair first = authService.login(
                    new AuthModels.LoginCommand("T-A", "admin", "Initial#Pass123", "device-a"), "127.0.0.1");
            assertThat(first.user().passwordChangeRequired()).isTrue();
            assertThat(jwtTokenService.decode(first.accessToken()).getSubject()).isEqualTo(Long.toString(userId));

            AuthModels.TokenPair second = authService.refresh(
                    new AuthModels.RefreshCommand(first.refreshToken(), "device-a"), "127.0.0.1");
            assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

            String familyId = jwtTokenService.decode(first.accessToken()).getClaimAsString("tokenFamilyId");
            authService.requireActiveSession(userId, 0, familyId);

            assertThatThrownBy(() -> authService.refresh(
                    new AuthModels.RefreshCommand(first.refreshToken(), "device-a"), "127.0.0.1"))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(IamErrorCode.TOKEN_REUSED));
            assertThatThrownBy(() -> authService.refresh(
                    new AuthModels.RefreshCommand(second.refreshToken(), "device-a"), "127.0.0.1"))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> authService.requireActiveSession(userId, 0, familyId))
                    .isInstanceOf(ApiException.class);

            int oldTokenVersion = ((Number) jwtTokenService.decode(first.accessToken())
                    .getClaim("tokenVersion")).intValue();
            authService.changePassword(userId,
                    new AuthModels.ChangePasswordCommand("Initial#Pass123", "Changed#Pass456"));
            assertThatThrownBy(() -> authService.requireActiveUser(userId, oldTokenVersion))
                    .isInstanceOf(ApiException.class);

            AuthModels.TokenPair afterChange = authService.login(
                    new AuthModels.LoginCommand("T-A", "admin", "Changed#Pass456", "device-a"), "127.0.0.1");
            assertThat(afterChange.user().passwordChangeRequired()).isFalse();
        } finally {
            jdbcTemplate.update("DELETE FROM iam_refresh_token WHERE user_id=?", userId);
            jdbcTemplate.update("DELETE FROM iam_user WHERE id=?", userId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=?", tenantId);
        }
    }

    @Test
    void shouldAllowOnlyOneConcurrentRefreshAndRevokeTheTokenFamilyOnReplay() throws Exception {
        long tenantId = 110L;
        long userId = 111L;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantId, "T-CONCURRENT", "并发测试租户", "ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status,force_password_change)
                VALUES(?,?,?,?,?,?,?)
                """, userId, tenantId, "concurrent-admin", "并发管理员",
                passwordEncoder.encode("Concurrent#Pass123"), "ACTIVE", false);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AuthModels.TokenPair initial = authService.login(new AuthModels.LoginCommand(
                    "T-CONCURRENT", "concurrent-admin", "Concurrent#Pass123", "device-c"), "127.0.0.1");
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> attempts = List.of(
                    executor.submit(() -> refreshAfterSignal(initial.refreshToken(), ready, start)),
                    executor.submit(() -> refreshAfterSignal(initial.refreshToken(), ready, start)));

            ready.await();
            start.countDown();
            List<Object> results = attempts.stream().map(this::getFuture).toList();

            assertThat(results).filteredOn(AuthModels.TokenPair.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(IamErrorCode.TOKEN_REUSED::equals).hasSize(1);
            AuthModels.TokenPair rotated = (AuthModels.TokenPair) results.stream()
                    .filter(AuthModels.TokenPair.class::isInstance).findFirst().orElseThrow();
            assertThatThrownBy(() -> authService.refresh(
                    new AuthModels.RefreshCommand(rotated.refreshToken(), "device-c"), "127.0.0.1"))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(IamErrorCode.TOKEN_INVALID));
        } finally {
            executor.shutdownNow();
            jdbcTemplate.update("DELETE FROM iam_refresh_token WHERE user_id=?", userId);
            jdbcTemplate.update("DELETE FROM iam_user WHERE id=?", userId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=?", tenantId);
        }
    }

    @Test
    void shouldFailClosedAndIsolateTenantReadsWritesAndExports() throws Exception {
        long tenantA = 120L;
        long tenantB = 121L;
        long userA = 122L;
        long userB = 123L;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantA, "T-ISO-A", "隔离租户A", "ACTIVE");
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantB, "T-ISO-B", "隔离租户B", "ACTIVE");
        try {
            try (TenantContext.Scope ignored = TenantContext.open(tenantA, 9001L)) {
                assertThat(tenantMapper.insert(userA, tenantA, "same-admin", "A管理员",
                        passwordEncoder.encode("TenantA#Pass123"))).isOne();
            }
            try (TenantContext.Scope ignored = TenantContext.open(tenantB, 9002L)) {
                assertThat(tenantMapper.insert(userB, tenantB, "same-admin", "B管理员",
                        passwordEncoder.encode("TenantB#Pass123"))).isOne();
            }

            try (TenantContext.Scope ignored = TenantContext.open(tenantA, 9001L)) {
                assertThat(tenantMapper.findAll(tenantA)).extracting(TenantIsolationTestMapper.TenantUserRow::id)
                        .containsExactly(userA);
                assertThat(tenantMapper.exportAll(tenantA)).extracting(TenantIsolationTestMapper.TenantUserRow::id)
                        .containsExactly(userA);
                assertThat(tenantMapper.findById(userB, tenantA)).isNull();
                assertTenantIsolation(() -> tenantMapper.rename(userB, tenantB, "越权修改"));
                assertTenantIsolation(() -> tenantMapper.insert(124L, tenantB,
                        "forged-admin", "伪造租户", "not-used"));
                assertTenantIsolation(() -> tenantMapper.unsafeFindWithoutTenant("same-admin"));
            }

            assertTenantIsolation(() -> tenantMapper.findAll(tenantA));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT display_name FROM iam_user WHERE id=?", String.class, userB)).isEqualTo("B管理员");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM iam_user WHERE id=124", Integer.class)).isZero();

            AuthModels.TokenPair tenantATokens = authService.login(new AuthModels.LoginCommand(
                    "T-ISO-A", "same-admin", "TenantA#Pass123", "tenant-probe"), "127.0.0.1");
            when(grantLoader.load(tenantA, userA)).thenReturn(new PermissionSnapshot(
                    Set.of("sample:contact:view", "sample:contact:export"),
                    Map.of("sample:contact", DataScope.organizations(Set.of(500L))),
                    Set.of()));
            mockMvc.perform(get("/test/tenant-probe")
                            .header("Authorization", "Bearer " + tenantATokens.accessToken())
                            .header("X-Tenant-Id", Long.toString(tenantB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId").value(Long.toString(tenantA)))
                    .andExpect(jsonPath("$.rowCount").value(1));

            String bearer = "Bearer " + tenantATokens.accessToken();
            mockMvc.perform(get("/test/permission-probe/500").header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phone").value("138****5678"));
            mockMvc.perform(get("/test/permission-probe/501").header("Authorization", bearer))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/test/permission-probe/500/export").header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phone").value("138****5678"));
            mockMvc.perform(put("/test/permission-probe/500").header("Authorization", bearer))
                    .andExpect(status().isForbidden());

            when(grantLoader.load(tenantA, userA)).thenReturn(new PermissionSnapshot(
                    Set.of("sample:contact:view"),
                    Map.of("sample:contact", DataScope.organizations(Set.of(500L))),
                    Set.of("sample:contact:phone:view")));
            mockMvc.perform(get("/test/permission-probe/500").header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phone").value("13812345678"));
        } finally {
            jdbcTemplate.update("DELETE FROM iam_refresh_token WHERE user_id IN (?,?)", userA, userB);
            jdbcTemplate.update("DELETE FROM iam_user WHERE id IN (?,?,?)", userA, userB, 124L);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id IN (?,?)", tenantA, tenantB);
        }
    }

    @Test
    void shouldAuditSanitizeRetryIdempotentlyAndRecoverExpiredLeases() throws Exception {
        long tenantId = 130L;
        long actorId = 131L;
        String traceId = "b0-operation-trace";
        AtomicInteger businessExecutions = new AtomicInteger();
        AtomicInteger consumerExecutions = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        jdbcTemplate.update("DELETE FROM sys_event_consumption");
        jdbcTemplate.update("DELETE FROM sys_outbox_event WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_audit_event WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_idempotency_record WHERE tenant_id=?", tenantId);
        jdbcTemplate.update("DELETE FROM ops_async_task WHERE tenant_id=?", tenantId);
        try {
            MDC.put("traceId", traceId);
            String eventId;
            long taskId;
            try (TenantContext.Scope ignored = TenantContext.open(tenantId, actorId)) {
                auditService.append(new AuditService.AuditCommand(
                        "USER_DISABLE", "USER", 900L, null,
                        Map.of("status", "ACTIVE", "password", "Audit#Secret123"),
                        Map.of("status", "DISABLED", "accessToken", "audit-token-secret"),
                        "127.0.0.1", "integration-test"));
                eventId = outboxService.enqueue("UserDisabled", 1, "USER", 900L,
                        Map.of("userId", "900", "bankAccount", "6222020012345678"));
                taskId = asyncTaskService.submit("EXPORT", Map.of(
                        "format", "xlsx", "secret", "task-secret-value"), 10);

                assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    auditService.append(new AuditService.AuditCommand(
                            "ROLLBACK_PROBE", "USER", 901L, null, Map.of(), Map.of(), null, null));
                    outboxService.enqueue("RollbackProbe", 1, "USER", 901L, Map.of());
                    throw new IllegalStateException("force rollback");
                })).isInstanceOf(IllegalStateException.class);
            }

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='ROLLBACK_PROBE'",
                    Integer.class, tenantId)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_outbox_event WHERE tenant_id=? AND event_type='RollbackProbe'",
                    Integer.class, tenantId)).isZero();
            String auditJson = jdbcTemplate.queryForObject(
                    "SELECT CONCAT(before_summary,after_summary) FROM sys_audit_event WHERE tenant_id=?",
                    String.class, tenantId);
            String outboxJson = jdbcTemplate.queryForObject(
                    "SELECT payload FROM sys_outbox_event WHERE event_id=?", String.class, eventId);
            String taskJson = jdbcTemplate.queryForObject(
                    "SELECT request_payload FROM ops_async_task WHERE id=?", String.class, taskId);
            assertThat(auditJson).contains("[REDACTED]").doesNotContain("Audit#Secret123", "audit-token-secret");
            assertThat(outboxJson).contains("[REDACTED]").doesNotContain("6222020012345678");
            assertThat(taskJson).contains("[REDACTED]").doesNotContain("task-secret-value");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT trace_id FROM sys_audit_event WHERE tenant_id=?", String.class, tenantId))
                    .isEqualTo(traceId);

            CountDownLatch ready = new CountDownLatch(10);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> idempotentCalls = java.util.stream.IntStream.range(0, 10)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try (TenantContext.Scope ignored = TenantContext.open(tenantId, actorId)) {
                            return idempotencyService.execute("SAMPLE_CREATE", "idem-key-001", "{\"name\":\"same\"}",
                                    Duration.ofHours(1), () -> {
                                        businessExecutions.incrementAndGet();
                                        return "created";
                                    });
                        }
                    })).toList();
            ready.await();
            start.countDown();
            assertThat(idempotentCalls).allSatisfy(call -> assertThat(call.get()).isEqualTo("created"));
            assertThat(businessExecutions).hasValue(1);
            try (TenantContext.Scope ignored = TenantContext.open(tenantId, actorId)) {
                assertThatThrownBy(() -> idempotencyService.execute(
                        "SAMPLE_CREATE", "idem-key-001", "{\"name\":\"different\"}",
                        Duration.ofHours(1), () -> "must-not-run"))
                        .isInstanceOf(ApiException.class);
            }

            OperationModels.OutboxEvent nodeAEvent = outboxService.claimNext("node-a", Duration.ofMinutes(1))
                    .orElseThrow();
            assertThat(outboxService.claimNext("node-b", Duration.ofMinutes(1))).isEmpty();
            jdbcTemplate.update("UPDATE sys_outbox_event SET lease_until=? WHERE id=?",
                    LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), nodeAEvent.id());
            OperationModels.OutboxEvent nodeBEvent = outboxService.claimNext("node-b", Duration.ofMinutes(1))
                    .orElseThrow();
            assertThat(outboxService.markPublished(nodeAEvent.id(), "node-a")).isFalse();
            assertThat(outboxService.markPublished(nodeBEvent.id(), "node-b")).isTrue();

            String retryEventId;
            try (TenantContext.Scope ignored = TenantContext.open(tenantId, actorId)) {
                retryEventId = outboxService.enqueue("RetryProbe", 1, "USER", 902L, Map.of("safe", "value"));
            }
            OperationModels.OutboxEvent failedAttempt = outboxService.claimNext("node-retry-a", Duration.ofMinutes(1))
                    .orElseThrow();
            assertThat(failedAttempt.eventId()).isEqualTo(retryEventId);
            assertThat(outboxService.markFailed(failedAttempt.id(), "node-retry-a",
                    failedAttempt.retryCount(), 3, Duration.ZERO, "MOCK_UNAVAILABLE")).isTrue();
            OperationModels.OutboxEvent retryAttempt = outboxService.claimNext("node-retry-b", Duration.ofMinutes(1))
                    .orElseThrow();
            assertThat(retryAttempt.retryCount()).isOne();
            assertThat(outboxService.markPublished(retryAttempt.id(), "node-retry-b")).isTrue();

            assertThat(outboxService.consumeOnce(eventId, "sample-consumer", consumerExecutions::incrementAndGet))
                    .isTrue();
            assertThat(outboxService.consumeOnce(eventId, "sample-consumer", consumerExecutions::incrementAndGet))
                    .isFalse();
            assertThat(consumerExecutions).hasValue(1);

            OperationModels.AsyncTask nodeATask = asyncTaskService.claimNext("node-a", Duration.ofMinutes(1))
                    .orElseThrow();
            assertThat(asyncTaskService.claimNext("node-b", Duration.ofMinutes(1))).isEmpty();
            jdbcTemplate.update("UPDATE ops_async_task SET lease_until=? WHERE id=?",
                    LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), nodeATask.id());
            OperationModels.AsyncTask nodeBTask = asyncTaskService.claimNext("node-b", Duration.ofMinutes(1))
                    .orElseThrow();
            assertThat(asyncTaskService.complete(nodeATask.id(), "node-a")).isFalse();
            assertThat(asyncTaskService.complete(nodeBTask.id(), "node-b")).isTrue();
        } finally {
            executor.shutdownNow();
            MDC.remove("traceId");
            jdbcTemplate.update("DELETE FROM sys_event_consumption WHERE event_id IN (SELECT event_id FROM sys_outbox_event WHERE tenant_id=?)", tenantId);
            jdbcTemplate.update("DELETE FROM sys_outbox_event WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sys_audit_event WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sys_idempotency_record WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM ops_async_task WHERE tenant_id=?", tenantId);
        }
    }

    private Object refreshAfterSignal(String refreshToken, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return authService.refresh(new AuthModels.RefreshCommand(refreshToken, "device-c"), "127.0.0.1");
        } catch (ApiException exception) {
            return exception.errorCode();
        }
    }

    private Object getFuture(Future<Object> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("Concurrent refresh failed unexpectedly", exception);
        }
    }

    private void assertTenantIsolation(Runnable action) {
        assertThatThrownBy(action::run).satisfies(error -> {
            Throwable cause = error;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertThat(cause).isInstanceOf(TenantIsolationException.class);
        });
    }

    @RestController
    static class TenantProbeController {
        private final TenantIsolationTestMapper mapper;

        TenantProbeController(TenantIsolationTestMapper mapper) {
            this.mapper = mapper;
        }

        @GetMapping("/test/tenant-probe")
        java.util.Map<String, Object> probe() {
            long tenantId = TenantContext.require().tenantId();
            return java.util.Map.of(
                    "tenantId", Long.toString(tenantId),
                    "rowCount", mapper.findAll(tenantId).size());
        }
    }

    @RestController
    static class PermissionProbeController {
        private final PermissionGuard guard;
        private final SensitiveDataMasker masker;

        PermissionProbeController(PermissionGuard guard, SensitiveDataMasker masker) {
            this.guard = guard;
            this.masker = masker;
        }

        @GetMapping("/test/permission-probe/{organizationId}")
        @RequiresPermission("sample:contact:view")
        Map<String, String> detail(@PathVariable long organizationId) {
            guard.requireData("sample:contact", DataTarget.organization(organizationId));
            return protectedContact();
        }

        @GetMapping("/test/permission-probe/{organizationId}/export")
        @RequiresPermission("sample:contact:export")
        Map<String, String> export(@PathVariable long organizationId) {
            guard.requireData("sample:contact", DataTarget.organization(organizationId));
            return protectedContact();
        }

        @PutMapping("/test/permission-probe/{organizationId}")
        @RequiresPermission("sample:contact:update")
        Map<String, String> update(@PathVariable long organizationId) {
            guard.requireData("sample:contact", DataTarget.organization(organizationId));
            return Map.of("status", "updated");
        }

        private Map<String, String> protectedContact() {
            return Map.of("phone", masker.protect(
                    "sample:contact:phone:view", "13812345678", SensitiveDataMasker.Kind.PHONE));
        }
    }
}

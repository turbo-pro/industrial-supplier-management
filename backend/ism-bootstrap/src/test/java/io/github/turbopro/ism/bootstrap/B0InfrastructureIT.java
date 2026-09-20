package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.bootstrap.schema.SchemaMarker;
import io.github.turbopro.ism.bootstrap.schema.SchemaMarkerMapper;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.iam.auth.AuthModels;
import io.github.turbopro.ism.iam.auth.AuthService;
import io.github.turbopro.ism.iam.auth.IamErrorCode;
import io.github.turbopro.ism.iam.auth.JwtTokenService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
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
}

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
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

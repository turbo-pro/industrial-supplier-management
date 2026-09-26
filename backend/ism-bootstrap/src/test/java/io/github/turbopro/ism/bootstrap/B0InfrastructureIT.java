package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.bootstrap.schema.SchemaMarker;
import io.github.turbopro.ism.bootstrap.schema.SchemaMarkerMapper;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantIsolationException;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationGrantLoader;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
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
import io.github.turbopro.ism.iam.console.ConsoleAuthModels;
import io.github.turbopro.ism.iam.console.ConsoleAuthService;
import io.github.turbopro.ism.iam.organization.OrganizationModels;
import io.github.turbopro.ism.iam.organization.OrganizationService;
import io.github.turbopro.ism.iam.authorization.DatabaseAuthorizationGrantLoader;
import io.github.turbopro.ism.iam.authorization.TenantAuthorizationMapper;
import io.github.turbopro.ism.iam.navigation.NavigationService;
import io.github.turbopro.ism.iam.access.AccessModels;
import io.github.turbopro.ism.iam.access.AccessService;
import io.github.turbopro.ism.iam.configuration.ConfigurationModels;
import io.github.turbopro.ism.iam.configuration.ConfigurationService;
import io.github.turbopro.ism.resource.file.FileModels;
import io.github.turbopro.ism.resource.file.FileService;
import io.github.turbopro.ism.operation.message.MessageModels;
import io.github.turbopro.ism.operation.message.MessageService;
import io.github.turbopro.ism.operation.task.TaskCenterService;
import io.github.turbopro.ism.resource.print.PrintModels;
import io.github.turbopro.ism.resource.print.PrintService;
import io.github.turbopro.ism.integration.search.SearchModels;
import io.github.turbopro.ism.integration.search.SearchService;
import io.github.turbopro.ism.safety.SafetyCredentialMapper;
import io.github.turbopro.ism.quality.QualityNcrMapper;
import io.github.turbopro.ism.performance.PerformanceMapper;
import io.github.turbopro.ism.performance.ImprovementMapper;
import io.github.turbopro.ism.supplier.SupplierReferenceService;
import io.github.turbopro.ism.supplier.SupplierMapper;
import io.github.turbopro.ism.supplier.BlacklistMapper;
import io.github.turbopro.ism.resource.file.FileReferenceService;
import io.github.turbopro.ism.quality.QualityPerformanceFacts;
import io.github.turbopro.ism.safety.SafetyPerformanceFacts;
import io.github.turbopro.ism.resource.supplier.SupplierResourceMapper;
import io.github.turbopro.ism.operation.AsyncTaskService;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.IdempotencyService;
import io.github.turbopro.ism.operation.OperationModels;
import io.github.turbopro.ism.operation.OutboxService;
import io.github.turbopro.ism.platform.packageplan.PackagePlanModels;
import io.github.turbopro.ism.platform.packageplan.PackagePlanService;
import io.github.turbopro.ism.platform.tenant.TenantModels;
import io.github.turbopro.ism.platform.tenant.TenantService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
@Import({B0InfrastructureIT.TenantProbeController.class, B0InfrastructureIT.PermissionProbeController.class,
        B0InfrastructureIT.ConsolePermissionProbeController.class})
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
        registry.add("ism.storage.local-root", () -> System.getProperty("java.io.tmpdir") + "/ism-it-storage");
        registry.add("ism.print.worker-enabled",()->"false");
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
    private ConsoleAuthService consoleAuthService;

    @Autowired
    private PackagePlanService packagePlanService;

    @Autowired
    private TenantService platformTenantService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private TenantAuthorizationMapper tenantAuthorizationMapper;

    @Autowired
    private NavigationService navigationService;

    @Autowired
    private AccessService accessService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private FileService fileService;

    @Autowired
    private MessageService messageService;
    @Autowired private TaskCenterService taskCenterService;
    @Autowired private PrintService printService;
    @Autowired private SearchService searchService;
    @Autowired private SafetyCredentialMapper safetyCredentialMapper;
    @Autowired private io.github.turbopro.ism.safety.SafetyAttendanceMapper safetyAttendanceMapper;
    @Autowired private QualityNcrMapper qualityNcrMapper;
    @Autowired private PerformanceMapper performanceMapper;
    @Autowired private ImprovementMapper improvementMapper;
    @Autowired private SupplierReferenceService supplierReferenceService;
    @Autowired private SupplierMapper supplierMapper;
    @Autowired private io.github.turbopro.ism.project.ContractProjectExitCheck contractProjectExitCheck;
    @Autowired private io.github.turbopro.ism.resource.supplier.ResourceExitCheck resourceExitCheck;
    @Autowired private io.github.turbopro.ism.quality.QualityExitCheck qualityExitCheck;
    @Autowired private io.github.turbopro.ism.safety.SafetyExitCheck safetyExitCheck;
    @Autowired private io.github.turbopro.ism.performance.PerformanceExitCheck performanceExitCheck;
    @Autowired private io.github.turbopro.ism.integration.finance.ManualClearanceMapper manualClearanceMapper;
    @Autowired private BlacklistMapper blacklistMapper;
    @Autowired private org.springframework.transaction.PlatformTransactionManager b7TransactionManager;
    @Autowired private FileReferenceService fileReferenceService;
    @Autowired private QualityPerformanceFacts qualityPerformanceFacts;
    @Autowired private SafetyPerformanceFacts safetyPerformanceFacts;
    @Autowired private SupplierResourceMapper supplierResourceMapper;

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
    void shouldPersistCredentialAndRecalculateEntryEligibility() {
        long tenantId = 9901, orgId = 9902, supplierId = 9903, personId = 9904;
        long fileId = 9905, credentialId = 9906;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantId, "SAFETY_CRED_IT", "Safety Tenant", "ACTIVE");
        try {
            jdbcTemplate.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,?,?,?,?)",
                    orgId, tenantId, "SAFETY_SITE", "Safety Site", "SITE", "ACTIVE");
            jdbcTemplate.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?)",
                    supplierId, tenantId, orgId, "SAFETY_SUP", "Safety Supplier", "SERVICE", "ACTIVE", 1, 1);
            jdbcTemplate.update("INSERT INTO res_supplier_person(id,tenant_id,organization_id,supplier_id,person_code,person_name,id_type,id_number_hash,id_number_masked,special_work_type,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    personId, tenantId, orgId, supplierId, "SAFETY_PERSON", "Safety Worker", "OTHER",
                    "a".repeat(64), "****1234", "WELDING", 1, 1);
            jdbcTemplate.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    fileId, tenantId, 1, "training.pdf", "application/pdf", 1, "b".repeat(64), "LOCAL", "it/credential", "ACTIVE");

            try (TenantContext.Scope ignored = TenantContext.open(tenantId, 1L)) {
            assertThat(supplierResourceMapper.person(tenantId, personId).specialWorkType()).isEqualTo("WELDING");
            assertThat(supplierResourceMapper.insertAsset(9907,tenantId,1,orgId,supplierId,null,"EXIT-ASSET","交接车辆","VEHICLE","IT-9907",null,null,null,null,null)).isOne();
            assertThat(resourceExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isOne());
            try (TenantContext.Scope other = TenantContext.open(tenantId + 100000, 1L)) {
                assertThat(resourceExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isZero());
            }
            jdbcTemplate.update("UPDATE res_supplier_person SET status='EXITED' WHERE id=?", personId);
            assertThat(supplierResourceMapper.assetStatus(tenantId,1,9907,"IN_USE",null,0)).isOne();
            assertThat(supplierResourceMapper.handoverAsset(tenantId,1,9907,"接收单位","交接完成",1)).isZero();
            assertThatThrownBy(() -> supplierResourceMapper.handoverAsset(tenantId+100000,1,9907,"接收单位","交接完成",1))
                    .hasRootCauseInstanceOf(io.github.turbopro.ism.common.infrastructure.tenant.TenantIsolationException.class);
            assertThat(supplierResourceMapper.assetStatus(tenantId,1,9907,"AVAILABLE",null,1)).isOne();
            assertThat(supplierResourceMapper.handoverAsset(tenantId,1,9907,"接收单位","交接完成",1)).isZero();
            assertThat(supplierResourceMapper.handoverAsset(tenantId,1,9907,"接收单位","交接完成",2)).isOne();
            assertThat(supplierResourceMapper.asset(tenantId,9907).handedOverAt()).isNotNull();
            assertThat(supplierResourceMapper.asset(tenantId,9907).handoverRecipient()).isEqualTo("接收单位");
            assertThat(supplierResourceMapper.handoverAsset(tenantId,1,9907,"接收单位","重复交接",3)).isZero();
            assertThat(supplierResourceMapper.assetStatus(tenantId,1,9907,"IN_USE",null,3)).isZero();
            assertThat(resourceExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isZero());
            assertThat(safetyCredentialMapper.person(tenantId, personId).specialWorkType()).isEqualTo("WELDING");
            assertThat(safetyCredentialMapper.activeFile(tenantId, fileId)).isOne();
            assertThat(safetyCredentialMapper.insert(credentialId, tenantId, orgId, supplierId, personId,
                    null, "SAFETY_TRAINING", "TRAINING", null, "入场培训", null, true,
                    java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now().plusDays(30), fileId, 1)).isOne();
            assertThat(safetyCredentialMapper.get(tenantId, credentialId).status()).isEqualTo("PENDING");
            assertThat(safetyCredentialMapper.review(tenantId, credentialId, "PENDING", "VERIFIED", null, 2, 0)).isOne();
            assertThat(safetyCredentialMapper.validTraining(tenantId, personId)).isOne();
            assertThat(supplierResourceMapper.validTraining(tenantId, personId)).isOne();
            assertThat(safetyCredentialMapper.validSpecialWork(tenantId, personId, "WELDING")).isZero();
            assertThat(safetyCredentialMapper.review(tenantId, credentialId, "VERIFIED", "REVOKED", "撤销", 2, 1)).isOne();
            assertThat(supplierResourceMapper.validTraining(tenantId, personId)).isZero();
            }
        } finally {
            jdbcTemplate.update("DELETE FROM saf_person_credential WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM res_supplier_asset WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM res_file_object WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM res_supplier_person WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_supplier WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_organization WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=?", tenantId);
        }
    }

    @Test
    void shouldPersistOneOpenAttendanceAndAllowSecondAfterCheckout() {
        long tenantId = 9911, orgId = 9912, supplierId = 9913, personId = 9914, projectId = 9915;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantId, "ATTEND_IT", "Attendance Tenant", "ACTIVE");
        try {
            jdbcTemplate.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,?,?,?,?)",
                    orgId, tenantId, "ATTEND_SITE", "Attendance Site", "SITE", "ACTIVE");
            jdbcTemplate.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?)",
                    supplierId, tenantId, orgId, "ATTEND_SUP", "Attendance Supplier", "SERVICE", "ACTIVE", 1, 1);
            jdbcTemplate.update("INSERT INTO prj_project(id,tenant_id,organization_id,supplier_id,project_code,project_name,project_type,planned_start_date,planned_end_date,manager_id,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    projectId, tenantId, orgId, supplierId, "ATTEND_PRJ", "Attendance Project", "SERVICE",
                    java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now().plusDays(30), 1, "ACTIVE", 1, 1);
            jdbcTemplate.update("INSERT INTO res_supplier_person(id,tenant_id,organization_id,supplier_id,project_id,person_code,person_name,id_type,id_number_hash,id_number_masked,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    personId, tenantId, orgId, supplierId, projectId, "ATTEND_PERSON", "Attendance Worker", "OTHER",
                    "c".repeat(64), "****5678", "ACTIVE", 1, 1);
            try (TenantContext.Scope ignored = TenantContext.open(tenantId, 1L)) {
                assertThat(safetyAttendanceMapper.activeProject(tenantId, projectId, supplierId)).isOne();
                jdbcTemplate.update("INSERT INTO saf_issue(id,tenant_id,organization_id,project_id,supplier_id,issue_no,title,category,severity,description,discovered_at,deadline,responsible_user_id,created_by,updated_by) VALUES(?,?,?,?,?,'EXIT-IT','退出检查隐患','OTHER','HIGH','测试',NOW(),CURRENT_DATE,1,1,1)", 9918, tenantId, orgId, projectId, supplierId);
                assertThat(safetyExitCheck.blockers(supplierId)).filteredOn(b -> b.code().equals("OPEN_SAFETY")).singleElement().satisfies(b -> assertThat(b.count()).isOne());
                try (TenantContext.Scope other = TenantContext.open(tenantId + 100000, 1L)) {
                    assertThat(safetyExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isZero());
                }
                jdbcTemplate.update("UPDATE saf_issue SET status='CLOSED' WHERE id=9918");
                assertThat(safetyAttendanceMapper.checkIn(9916, tenantId, orgId, projectId, supplierId, personId, "一号厂区", 1)).isOne();
                assertThat(safetyAttendanceMapper.get(tenantId, 9916).personName()).isEqualTo("Attendance Worker");
                assertThat(safetyExitCheck.blockers(supplierId)).filteredOn(b -> b.code().equals("OPEN_ATTENDANCE")).singleElement().satisfies(b -> assertThat(b.count()).isOne());
                assertThat(safetyAttendanceMapper.count(tenantId, personId, true, "TENANT_ALL", java.util.Set.of(), java.util.Set.of(), 1)).isOne();
                assertThatThrownBy(() -> safetyAttendanceMapper.checkIn(9917, tenantId, orgId, projectId, supplierId, personId, "二号厂区", 1))
                        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
                assertThat(safetyAttendanceMapper.checkOut(tenantId, 9916, 1, "离场", 0)).isOne();
                assertThat(safetyExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isZero());
                assertThat(safetyAttendanceMapper.checkOut(tenantId, 9916, 1, "重复签退", 1)).isZero();
                assertThat(safetyAttendanceMapper.checkIn(9917, tenantId, orgId, projectId, supplierId, personId, "二号厂区", 1)).isOne();
                assertThat(safetyAttendanceMapper.list(tenantId, personId, true, "TENANT_ALL", java.util.Set.of(), java.util.Set.of(), 1, 0, 20)).hasSize(1);
            }
        } finally {
            jdbcTemplate.update("DELETE FROM saf_site_attendance WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM saf_issue WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM res_supplier_person WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM prj_project WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_supplier WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_organization WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=?", tenantId);
        }
    }

    @Test
    void shouldPersistQualityCorrectionVerificationAndHistory() {
        long tenantId = 9931, orgId = 9932, supplierId = 9933, projectId = 9934;
        long fileId = 9935, ncrId = 9936;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantId, "QUALITY_IT", "Quality Tenant", "ACTIVE");
        try {
            jdbcTemplate.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,?,?,?,?)",
                    orgId, tenantId, "QUALITY_SITE", "Quality Site", "SITE", "ACTIVE");
            jdbcTemplate.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?)",
                    supplierId, tenantId, orgId, "QUALITY_SUP", "Quality Supplier", "SERVICE", "ACTIVE", 1, 1);
            jdbcTemplate.update("INSERT INTO prj_project(id,tenant_id,organization_id,supplier_id,project_code,project_name,project_type,planned_start_date,planned_end_date,manager_id,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    projectId, tenantId, orgId, supplierId, "QUALITY_PRJ", "Quality Project", "SERVICE",
                    java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now().plusDays(30), 1, "ACTIVE", 1, 1);
            jdbcTemplate.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    fileId, tenantId, 1, "quality.pdf", "application/pdf", 1, "d".repeat(64), "LOCAL", "it/quality", "ACTIVE");
            jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(?,?,?,?,?,?)",
                    9938, tenantId, "quality_it", "Quality Tester", "unused", "ACTIVE");
            try (TenantContext.Scope ignored = TenantContext.open(tenantId, 1L)) {
                assertThat(qualityNcrMapper.activeProject(tenantId, projectId).supplierId()).isEqualTo(supplierId);
                assertThat(qualityNcrMapper.activeFile(tenantId, fileId)).isOne();
                assertThat(qualityNcrMapper.activeUser(tenantId, 9938)).isOne();
                assertThat(qualityNcrMapper.insert(ncrId, tenantId, orgId, projectId, supplierId,
                        "NCR-IT", "焊缝缺陷", "PROCESS", "HIGH", "抽样不合格",
                        java.time.LocalDate.now(), new java.math.BigDecimal("10.000"), new java.math.BigDecimal("2.000"),
                        "件", fileId, java.time.LocalDate.now().plusDays(7), 1, 1)).isOne();
                assertThat(qualityNcrMapper.insertEvent(9937, tenantId, ncrId, "CREATE", null, "OPEN", "抽样不合格", fileId, 1)).isOne();
                assertThat(qualityNcrMapper.get(tenantId, ncrId).status()).isEqualTo("OPEN");
                assertThat(qualityExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isOne());
                try (TenantContext.Scope other = TenantContext.open(tenantId + 100000, 1L)) {
                    assertThat(qualityExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isZero());
                }
                assertThat(qualityNcrMapper.count(tenantId, null, "OPEN", "TENANT_ALL", java.util.Set.of(), java.util.Set.of(), 1)).isOne();
                assertThat(qualityNcrMapper.rectify(tenantId, ncrId, "焊接参数错误", "返工", "校准设备", fileId, 1, 0)).isOne();
                assertThat(qualityNcrMapper.verify(tenantId, ncrId, "REJECT", "仍不合格", 1, 1)).isOne();
                assertThat(qualityNcrMapper.rectify(tenantId, ncrId, "焊接参数错误", "再次返工", "校准设备", fileId, 1, 2)).isOne();
                assertThat(qualityNcrMapper.verify(tenantId, ncrId, "PASS", "合格", 1, 3)).isOne();
                assertThat(qualityNcrMapper.verify(tenantId, ncrId, "REJECT", "重复复验", 1, 4)).isZero();
                assertThat(qualityNcrMapper.get(tenantId, ncrId).status()).isEqualTo("CLOSED");
                assertThat(qualityExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isZero());
                assertThat(qualityNcrMapper.events(tenantId, ncrId)).hasSize(1);
            }
        } finally {
            jdbcTemplate.update("DELETE FROM qua_ncr_event WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM qua_nonconformance WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM res_file_object WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_user WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM prj_project WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_supplier WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_organization WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=?", tenantId);
        }
    }

    @Test
    void shouldPersistPerformanceRuleScorecardAndReview() {
        long tenantId = 9941, orgId = 9942, supplierId = 9943, fileId = 9944, evaluationId = 9945;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantId, "PERFORMANCE_IT", "Performance Tenant", "ACTIVE");
        try {
            jdbcTemplate.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,?,?,?,?)",
                    orgId, tenantId, "PERFORMANCE_ORG", "Performance Organization", "SITE", "ACTIVE");
            jdbcTemplate.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?)",
                    supplierId, tenantId, orgId, "PERFORMANCE_SUP", "Performance Supplier", "SERVICE_PROVIDER", "ACTIVE", 1, 1);
            jdbcTemplate.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    fileId, tenantId, 1, "performance.pdf", "application/pdf", 1, "e".repeat(64), "LOCAL", "it/performance", "ACTIVE");
            try (TenantContext.Scope ignored = TenantContext.open(tenantId, 1L)) {
                assertThat(performanceMapper.insertRule(tenantId, 35, 25, 25, 15, 1)).isOne();
                assertThat(manualClearanceMapper.insert(tenantId,supplierId,fileId,"核验证据",1)).isOne();
                assertThat(manualClearanceMapper.get(tenantId,supplierId).status()).isEqualTo("SUBMITTED");
                var manualGateway=new io.github.turbopro.ism.integration.finance.ManualClearanceGateway(manualClearanceMapper,fileReferenceService);
                assertThat(manualGateway.check(tenantId,supplierId).status()).isEqualTo(io.github.turbopro.ism.integration.finance.FinancialClearanceGateway.Status.UNKNOWN);
                assertThat(manualClearanceMapper.review(tenantId,supplierId,"APPROVED","确认",2,0)).isOne();
                assertThat(manualGateway.check(tenantId,supplierId).status()).isEqualTo(io.github.turbopro.ism.integration.finance.FinancialClearanceGateway.Status.CLEAR);
                var manualExitCheck=new io.github.turbopro.ism.integration.finance.FinancialExitCheck(java.util.List.of(manualGateway),java.time.Duration.ofMinutes(15));
                assertThat(manualExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isZero());
                try (TenantContext.Scope other = TenantContext.open(tenantId+100000,1L)) {
                    assertThat(manualClearanceMapper.get(tenantId+100000,supplierId)).isNull();
                }
                assertThat(manualClearanceMapper.review(tenantId,supplierId,"REVOKED","撤销",2,0)).isZero();
                assertThat(manualClearanceMapper.review(tenantId,supplierId,"REVOKED","撤销",2,1)).isOne();
                assertThat(manualExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isOne());
                assertThat(performanceMapper.rule(tenantId).qualityWeight()).isEqualTo(35);
                assertThat(performanceMapper.updateRule(tenantId, 40, 20, 25, 15, 1, 0)).isOne();
                assertThat(performanceMapper.updateRule(tenantId, 35, 25, 25, 15, 1, 0)).isZero();
                assertThat(supplierReferenceService.active(supplierId).name()).isEqualTo("Performance Supplier");
                long blacklistId = 9950;
                assertThat(blacklistMapper.insert(blacklistId, tenantId, supplierId, orgId,
                        "PERFORMANCE_SUP", "Performance Supplier", "BLACKLIST", null, null, "重大违约", "CASE-1", 1)).isOne();
                assertThat(blacklistMapper.get(tenantId, blacklistId).restrictionType())
                        .isEqualTo(io.github.turbopro.ism.supplier.BlacklistModels.RestrictionType.BLACKLIST);
                assertThat(blacklistMapper.lockSupplier(tenantId, supplierId)).isEqualTo(supplierId);
                assertThat(blacklistMapper.submit(tenantId, blacklistId, 1, 0)).isOne();
                assertThat(blacklistMapper.review(tenantId, blacklistId, "APPROVE", "证据充分", 2, 1)).isOne();
                assertThat(blacklistMapper.active(tenantId, supplierId, java.time.LocalDate.now())).isOne();
                assertThat(supplierReferenceService.active(supplierId)).isNull();
                assertThat(supplierReferenceService.activeForNewBusiness(supplierId)).isNull();
                assertThat(blacklistMapper.review(tenantId, blacklistId, "REJECT", "过期", 2, 1)).isZero();
                assertThat(blacklistMapper.revoke(tenantId, blacklistId, "复核解除", 2, 2)).isOne();
                assertThat(blacklistMapper.active(tenantId, supplierId, java.time.LocalDate.now())).isZero();
                assertThat(supplierReferenceService.active(supplierId)).isNotNull();
                assertThat(supplierReferenceService.activeForNewBusiness(supplierId)).isNotNull();
                long temporaryId = 9951;
                assertThat(blacklistMapper.insert(temporaryId, tenantId, supplierId, orgId,
                        "PERFORMANCE_SUP", "Performance Supplier", "TEMPORARY", java.time.LocalDate.now(),
                        java.time.LocalDate.now().plusDays(2), "限期限制", "CASE-2", 1)).isOne();
                assertThat(blacklistMapper.get(tenantId, temporaryId).restrictionType())
                        .isEqualTo(io.github.turbopro.ism.supplier.BlacklistModels.RestrictionType.TEMPORARY);
                assertThat(blacklistMapper.submit(tenantId, temporaryId, 1, 0)).isOne();
                assertThat(blacklistMapper.review(tenantId, temporaryId, "APPROVE", "批准", 2, 1)).isOne();
                assertThat(blacklistMapper.active(tenantId, supplierId, java.time.LocalDate.now())).isOne();
                jdbcTemplate.update("UPDATE sup_blacklist_case SET effective_until=? WHERE id=?",
                        java.time.LocalDate.now().minusDays(1), temporaryId);
                assertThat(blacklistMapper.active(tenantId, supplierId, java.time.LocalDate.now())).isZero();
                assertThat(blacklistMapper.openCase(tenantId, supplierId, java.time.LocalDate.now(), "TEMPORARY")).isZero();
                long watchId = 9952;
                assertThat(blacklistMapper.insert(watchId, tenantId, supplierId, orgId,
                        "PERFORMANCE_SUP", "Performance Supplier", "WATCH", null, null,
                        "重点关注履约", "CASE-3", 1)).isOne();
                assertThat(blacklistMapper.submit(tenantId, watchId, 1, 0)).isOne();
                assertThat(blacklistMapper.review(tenantId, watchId, "APPROVE", "纳入观察", 2, 1)).isOne();
                assertThat(blacklistMapper.observed(tenantId, supplierId)).isOne();
                assertThat(blacklistMapper.active(tenantId, supplierId, java.time.LocalDate.now())).isZero();
                assertThat(supplierReferenceService.activeForNewBusiness(supplierId)).isNotNull();
                assertThat(supplierMapper.list(tenantId, null, null, null, "TENANT_ALL", java.util.Set.of(), 1, 0, 20))
                        .anySatisfy(supplier -> { assertThat(supplier.id()).isEqualTo(Long.toString(supplierId)); assertThat(supplier.observed()).isTrue(); });
                assertThat(blacklistMapper.openCase(tenantId, supplierId, java.time.LocalDate.now(), "WATCH")).isOne();
                assertThat(blacklistMapper.openCase(tenantId, supplierId, java.time.LocalDate.now(), "BLACKLIST")).isZero();
                assertThat(blacklistMapper.revoke(tenantId, watchId, "观察期结束", 2, 2)).isOne();
                assertThat(blacklistMapper.observed(tenantId, supplierId)).isZero();
                assertThat(blacklistMapper.insert(9980,tenantId,supplierId,orgId,"PERFORMANCE_SUP","Performance Supplier","BLACKLIST",null,null,"并发限制","SNAPSHOT-IT",1)).isOne();
                var oldTransaction=new org.springframework.transaction.support.TransactionTemplate(b7TransactionManager);
                var approvalTransaction=new org.springframework.transaction.support.TransactionTemplate(b7TransactionManager);
                approvalTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                oldTransaction.executeWithoutResult(tx -> {
                    assertThat(blacklistMapper.active(tenantId,supplierId,java.time.LocalDate.now())).isZero();
                    approvalTransaction.executeWithoutResult(approval -> {
                        blacklistMapper.lockSupplier(tenantId,supplierId);
                        assertThat(blacklistMapper.submit(tenantId,9980,1,0)).isOne();
                        assertThat(blacklistMapper.review(tenantId,9980,"APPROVE","批准",2,1)).isOne();
                    });
                    assertThat(supplierReferenceService.activeForNewBusiness(supplierId)).isNull();
                });
                assertThat(blacklistMapper.revoke(tenantId,9980,"测试解除",2,2)).isOne();
                assertThat(fileReferenceService.active(fileId)).isTrue();
                jdbcTemplate.update("INSERT INTO prj_contract(id,tenant_id,organization_id,supplier_id,contract_no,contract_name,contract_type,amount,start_date,end_date,owner_id,file_id,created_by,updated_by) VALUES(9953,?,?,?,'EXIT-C','退出测试','SERVICE',10,CURRENT_DATE,CURRENT_DATE,1,?,1,1)",tenantId,orgId,supplierId,fileId);
                jdbcTemplate.update("INSERT INTO prj_project(id,tenant_id,organization_id,supplier_id,project_code,project_name,project_type,planned_start_date,planned_end_date,manager_id,created_by,updated_by) VALUES(9954,?,?,?,'EXIT-P','退出测试','MAINTENANCE',CURRENT_DATE,CURRENT_DATE,1,1,1)",tenantId,orgId,supplierId);
                assertThat(contractProjectExitCheck.blockers(supplierId)).allSatisfy(blocker -> assertThat(blocker.count()).isOne());
                jdbcTemplate.update("UPDATE prj_contract SET status='TERMINATED' WHERE id=9953");
                jdbcTemplate.update("UPDATE prj_project SET status='CANCELLED' WHERE id=9954");
                assertThat(contractProjectExitCheck.blockers(supplierId)).allSatisfy(blocker -> assertThat(blocker.count()).isZero());
                assertThat(qualityPerformanceFacts.forSupplier(supplierId, java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now()).total()).isZero();
                assertThat(safetyPerformanceFacts.forSupplier(supplierId, java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now()).total()).isZero();
                assertThat(performanceMapper.insert(evaluationId, tenantId, orgId, supplierId,
                        "PERFORMANCE_SUP", "Performance Supplier",
                        java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now().minusDays(1),
                        new java.math.BigDecimal("78.00"), "C", 0, 0, 0, 0, 1)).isOne();
                assertThat(performanceMapper.insertItem(evaluationId, tenantId, "QUALITY", 35,
                        new java.math.BigDecimal("90.00"), "质量证据", fileId)).isOne();
                assertThat(performanceMapper.insertItem(evaluationId, tenantId, "DELIVERY", 25,
                        new java.math.BigDecimal("80.00"), "交付证据", fileId)).isOne();
                assertThat(performanceMapper.insertItem(evaluationId, tenantId, "SAFETY", 25,
                        new java.math.BigDecimal("70.00"), "安全证据", fileId)).isOne();
                assertThat(performanceMapper.insertItem(evaluationId, tenantId, "SERVICE", 15,
                        new java.math.BigDecimal("60.00"), "服务证据", fileId)).isOne();
                assertThat(performanceMapper.insertEvent(9946, tenantId, evaluationId, "CREATE", null, "DRAFT", null, 1)).isOne();
                assertThat(performanceMapper.items(tenantId, evaluationId)).hasSize(4);
                assertThat(performanceMapper.count(tenantId, supplierId, "DRAFT", "TENANT_ALL", java.util.Set.of(), 1)).isOne();
                assertThatThrownBy(() -> performanceMapper.insert(9947, tenantId, orgId, supplierId,
                        "PERFORMANCE_SUP", "Performance Supplier",
                        java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now().minusDays(1),
                        new java.math.BigDecimal("78.00"), "C", 0, 0, 0, 0, 1))
                        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
                assertThat(performanceMapper.submit(tenantId, evaluationId, 1, 0)).isOne();
                assertThat(performanceMapper.review(tenantId, evaluationId, "APPROVE", "批准", 2, 1)).isOne();
                assertThat(performanceMapper.review(tenantId, evaluationId, "REJECT", "再次审核", 2, 2)).isZero();
                assertThat(performanceMapper.get(tenantId, evaluationId).status()).isEqualTo("APPROVED");
                assertThat(performanceMapper.events(tenantId, evaluationId)).hasSize(1);
                long planId = 9948;
                assertThat(improvementMapper.insert(planId, tenantId, evaluationId, "根因", "改进措施",
                        java.time.LocalDate.now().plusDays(7), 1)).isOne();
                assertThat(improvementMapper.get(tenantId, evaluationId).status()).isEqualTo("OPEN");
                assertThat(performanceExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isOne());
                try (TenantContext.Scope other = TenantContext.open(tenantId + 100000, 1L)) {
                    assertThat(performanceExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isZero());
                }
                assertThat(improvementMapper.submit(tenantId, evaluationId, "已完成", fileId, 1, 0)).isOne();
                assertThat(improvementMapper.review(tenantId, evaluationId, "REWORK", "证据不足", 2, 1)).isOne();
                assertThat(performanceExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isOne());
                assertThat(improvementMapper.submit(tenantId, evaluationId, "补充证据", fileId, 1, 2)).isOne();
                assertThat(improvementMapper.review(tenantId, evaluationId, "ACCEPT", "验收通过", 2, 3)).isOne();
                assertThat(improvementMapper.review(tenantId, evaluationId, "REWORK", "过期版本", 2, 3)).isZero();
                assertThat(improvementMapper.get(tenantId, evaluationId).status()).isEqualTo("ACCEPTED");
                assertThat(performanceExitCheck.blockers(supplierId)).allSatisfy(b -> assertThat(b.count()).isZero());
                assertThat(improvementMapper.lockSupplier(tenantId, supplierId)).isEqualTo(supplierId);
                jdbcTemplate.update("UPDATE sup_supplier SET status='EXITED' WHERE id=?", supplierId);
                assertThat(improvementMapper.lockSupplier(tenantId, supplierId)).isNull();
                assertThat(improvementMapper.event(9949, tenantId, planId, "CREATE", null, "OPEN", null, 1)).isOne();
                assertThat(improvementMapper.events(tenantId, planId)).hasSize(1);
            }
        } finally {
            jdbcTemplate.update("DELETE FROM sup_blacklist_event WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_blacklist_case WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM per_improvement_event WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM per_improvement_plan WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM per_evaluation_event WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM per_evaluation_item WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM per_supplier_evaluation WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM per_score_rule WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM int_financial_clearance WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM prj_project WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM prj_contract WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM res_file_object WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_supplier WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_organization WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=?", tenantId);
        }
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
    void shouldSeparateConsoleAuthenticationAndEnforcePlatformPermissions() throws Exception {
        long platformAdminId = 9001L;
        long platformViewerId = 9002L;
        jdbcTemplate.update("""
                INSERT INTO plt_user(id,username,display_name,password_hash,status)
                VALUES(?,?,?,?,?),(?,?,?,?,?)
                """, platformAdminId, "platform-admin", "平台管理员", passwordEncoder.encode("Console#Pass123"), "ACTIVE",
                platformViewerId, "platform-viewer", "平台访客", passwordEncoder.encode("Console#Pass123"), "ACTIVE");
        jdbcTemplate.update("INSERT INTO plt_user_role(user_id,role_id) VALUES(?,1001)", platformAdminId);

        ConsoleAuthModels.TokenPair admin = consoleAuthService.login(
                new ConsoleAuthModels.LoginCommand("platform-admin", "Console#Pass123", "console-device"), "127.0.0.1");
        ConsoleAuthModels.TokenPair viewer = consoleAuthService.login(
                new ConsoleAuthModels.LoginCommand("platform-viewer", "Console#Pass123", "viewer-device"), "127.0.0.1");

        mockMvc.perform(get("/api/console/auth/me").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("platform-admin"))
                .andExpect(jsonPath("$.data.permissions[?(@ == 'platform:tenant:view')]").exists());
        mockMvc.perform(get("/api/console/test-permission").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/console/test-permission").header("Authorization", "Bearer " + viewer.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isUnauthorized());

        long tenantId = 9100L;
        long tenantUserId = 9101L;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantId, "CONSOLE-BOUNDARY", "边界租户", "ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status)
                VALUES(?,?,?,?,?,?)
                """, tenantUserId, tenantId, "boundary-admin", "租户管理员",
                passwordEncoder.encode("Tenant#Pass123"), "ACTIVE");
        AuthModels.TokenPair tenant = authService.login(new AuthModels.LoginCommand(
                "CONSOLE-BOUNDARY", "boundary-admin", "Tenant#Pass123", "tenant-device"), "127.0.0.1");
        mockMvc.perform(get("/api/console/test-permission")
                        .header("Authorization", "Bearer " + tenant.accessToken()))
                .andExpect(status().isUnauthorized());

        ConsoleAuthModels.TokenPair rotated = consoleAuthService.refresh(
                new ConsoleAuthModels.RefreshCommand(admin.refreshToken(), "console-device"), "127.0.0.1");
        assertThatThrownBy(() -> consoleAuthService.refresh(
                new ConsoleAuthModels.RefreshCommand(admin.refreshToken(), "console-device"), "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(IamErrorCode.TOKEN_REUSED);
        mockMvc.perform(get("/api/console/auth/me").header("Authorization", "Bearer " + rotated.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldPublishImmutablePackagesPreviewDowngradeAndEnforceQuota() {
        var plan = packagePlanService.create(new PackagePlanModels.CreatePackage("CHEMICAL_ENTERPRISE", "化工企业版"));
        long packageId = Long.parseLong(plan.id());
        var full = packagePlanService.createVersion(packageId, new PackagePlanModels.CreateVersion(
                1, "完整版本", LocalDateTime.now(), List.of(
                new PackagePlanModels.ModuleGrant("SUPPLIER", true, Map.of("SUPPLIER_COUNT", 100L)),
                new PackagePlanModels.ModuleGrant("QUALITY", true, Map.of()),
                new PackagePlanModels.ModuleGrant("SAFETY", true, Map.of("USER_COUNT", 50L)))));
        assertThat(packagePlanService.validate(Long.parseLong(full.id())).valid()).isTrue();
        var publishedFull = packagePlanService.publish(Long.parseLong(full.id()), full.version(), 9001L);
        assertThat(publishedFull.status()).isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> packagePlanService.publish(Long.parseLong(full.id()), publishedFull.version(), 9001L))
                .isInstanceOf(ApiException.class);

        var basic = packagePlanService.createVersion(packageId, new PackagePlanModels.CreateVersion(
                2, "基础版本", LocalDateTime.now(), List.of(
                new PackagePlanModels.ModuleGrant("SUPPLIER", true, Map.of("SUPPLIER_COUNT", 10L)),
                new PackagePlanModels.ModuleGrant("QUALITY", false, Map.of()),
                new PackagePlanModels.ModuleGrant("SAFETY", true, Map.of("USER_COUNT", 20L)))));
        packagePlanService.publish(Long.parseLong(basic.id()), basic.version(), 9001L);

        long tenantId = 9200L;
        jdbcTemplate.update("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantId, "PLAN-TENANT", "套餐验收租户", "ACTIVE");
        packagePlanService.assign(tenantId, Long.parseLong(full.id()), new PackagePlanModels.AssignSubscription(
                full.id(), LocalDateTime.now(), null, Map.of()));
        jdbcTemplate.update("INSERT INTO plt_quota_usage(id,tenant_id,quota_code,period_key,used_value) VALUES(?,?,?,?,?)",
                9201L, tenantId, "SUPPLIER_COUNT", "CURRENT", 12L);

        var preview = packagePlanService.preview(tenantId, Long.parseLong(basic.id()));
        assertThat(preview.removedModules()).contains("QUALITY");
        assertThat(preview.quotaChanges().get("SUPPLIER_COUNT").exceedsTarget()).isTrue();
        assertThatThrownBy(() -> packagePlanService.assign(tenantId, Long.parseLong(basic.id()),
                new PackagePlanModels.AssignSubscription(basic.id(), LocalDateTime.now(), null, Map.of())))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> packagePlanService.requireQuota(tenantId, "SUPPLIER_COUNT", 89L))
                .isInstanceOf(ApiException.class);
        packagePlanService.requireModule(tenantId, "QUALITY");
    }

    @Test
    void shouldProvisionTenantHeadquartersAndFirstAdministrator() {
        var plan = packagePlanService.create(new PackagePlanModels.CreatePackage(
                "TENANT_BOOTSTRAP", "租户开通验收套餐"));
        var draft = packagePlanService.createVersion(Long.parseLong(plan.id()),
                new PackagePlanModels.CreateVersion(1, "正式版", LocalDateTime.now(), List.of(
                        new PackagePlanModels.ModuleGrant("SUPPLIER", true,
                                Map.of("SUPPLIER_COUNT", 100L)))));
        var published = packagePlanService.publish(Long.parseLong(draft.id()), draft.version(), 9001L);

        var pending = platformTenantService.create(new TenantModels.CreateTenant(
                "BOOTSTRAP-TENANT", "初始化验收租户", "Asia/Shanghai", "zh-CN", published.id()));
        assertThat(pending.status()).isEqualTo("PROVISIONING");
        assertThat(pending.initializationStatus()).isEqualTo("PENDING");

        var ready = platformTenantService.initialize(Long.parseLong(pending.id()),
                new TenantModels.InitializeTenant("tenant-admin", "租户首管理员",
                        "Initial#Pass123", "集团总部"));
        assertThat(ready.status()).isEqualTo("ACTIVE");
        assertThat(ready.initializationStatus()).isEqualTo("READY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_organization WHERE tenant_id=? AND organization_type='HEADQUARTERS'",
                Integer.class, Long.parseLong(ready.id()))).isOne();

        AuthModels.TokenPair firstLogin = authService.login(new AuthModels.LoginCommand(
                "BOOTSTRAP-TENANT", "tenant-admin", "Initial#Pass123", "bootstrap-device"), "127.0.0.1");
        assertThat(firstLogin.user().passwordChangeRequired()).isTrue();

        long tenantId = Long.parseLong(ready.id());
        long administratorId = Long.parseLong(firstLogin.user().id());
        long headquartersId = jdbcTemplate.queryForObject(
                "SELECT id FROM iam_organization WHERE tenant_id=? AND organization_type='HEADQUARTERS'",
                Long.class, tenantId);
        try (TenantContext.Scope ignored = TenantContext.open(tenantId, administratorId)) {
            var grants = new DatabaseAuthorizationGrantLoader(tenantAuthorizationMapper)
                    .load(tenantId, administratorId);
            assertThat(grants.actions()).contains("iam:organization:view", "iam:organization:manage",
                    "iam:user:manage", "iam:role:manage");
            assertThat(grants.dataScope("iam:organization").type()).isEqualTo(DataScope.Type.TENANT_ALL);
            assertThat(navigationService.currentMenus()).filteredOn(menu -> menu.code().equals("SYSTEM_MANAGEMENT"))
                    .singleElement().satisfies(menu -> assertThat(menu.children()).hasSize(5));
            assertThat(navigationService.currentMenus()).filteredOn(menu -> menu.code().equals("RESOURCE_CENTER"))
                    .singleElement().satisfies(menu -> assertThat(menu.children())
                            .extracting("code").containsExactly("FILE_MANAGEMENT", "TASK_CENTER", "PRINT_TEMPLATE", "ADVANCED_SEARCH"));
            assertThat(configurationService.dictionary("SUPPLIER_TYPE").items())
                    .extracting(ConfigurationModels.DictionaryItemView::code)
                    .contains("MATERIAL", "SERVICE", "CONTRACTOR");
            var supplierTypes = configurationService.upsertItem("SUPPLIER_TYPE",
                    new ConfigurationModels.UpsertDictionaryItem(
                            "LOGISTICS", "物流供应商", "LOGISTICS", 40, "ACTIVE", 0));
            assertThat(supplierTypes.items()).filteredOn(item -> item.code().equals("LOGISTICS"))
                    .singleElement().extracting(ConfigurationModels.DictionaryItemView::source)
                    .isEqualTo("TENANT_CUSTOM");
            configurationService.upsertItem("SUPPLIER_TYPE", new ConfigurationModels.UpsertDictionaryItem(
                    "LOGISTICS", "物流与运输供应商", "LOGISTICS", 40, "ACTIVE", 0));
            assertThatThrownBy(() -> configurationService.upsertItem("SUPPLIER_TYPE",
                    new ConfigurationModels.UpsertDictionaryItem(
                            "LOGISTICS", "过期写入", "LOGISTICS", 40, "ACTIVE", 0)))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> configurationService.upsertItem("COMMON_STATUS",
                    new ConfigurationModels.UpsertDictionaryItem(
                            "PENDING", "待处理", "PENDING", 30, "ACTIVE", 0)))
                    .isInstanceOf(ApiException.class);
            var systemName = configurationService.updateSetting("branding.systemName",
                    new ConfigurationModels.UpdateSetting("化工供应商协同平台", 0));
            assertThat(systemName.value()).isEqualTo("化工供应商协同平台");
            var renamed = configurationService.updateSetting("branding.systemName",
                    new ConfigurationModels.UpdateSetting("制造业供应商协同平台", 0));
            assertThat(renamed.version()).isOne();
            assertThatThrownBy(() -> configurationService.updateSetting("branding.systemName",
                    new ConfigurationModels.UpdateSetting("过期配置", 0))).isInstanceOf(ApiException.class);

            byte[] content = "industrial supplier file upload".getBytes(StandardCharsets.UTF_8);
            String contentHash;
            try {
                contentHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            var upload = fileService.initialize(new FileModels.InitializeUpload(
                    "qualification.txt", "text/plain", content.length, contentHash));
            assertThat(upload.status()).isEqualTo("UPLOADING");
            var resumed = fileService.putChunk(Long.parseLong(upload.id()), 0, contentHash, content);
            assertThat(resumed.uploadedChunks()).containsExactly(0);
            var storedFile = fileService.complete(Long.parseLong(upload.id()), resumed.version());
            assertThat(storedFile.sha256()).isEqualTo(contentHash);
            try (var input = fileService.download(Long.parseLong(storedFile.id())).stream()) {
                assertThat(input.readAllBytes()).isEqualTo(content);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            assertThat(fileService.initialize(new FileModels.InitializeUpload(
                    "same-content.txt", "text/plain", content.length, contentHash)).status())
                    .isEqualTo("COMPLETED");
            var template = messageService.create(new MessageModels.SaveTemplate(
                    "QUALIFICATION_EXPIRES", "资质到期提醒", "IN_APP", "{{supplierName}}资质即将到期",
                    "供应商{{supplierName}}的{{qualificationName}}将在{{expiryDate}}到期。",
                    Set.of("supplierName", "qualificationName", "expiryDate"), 0));
            assertThat(template.version()).isZero();
            var sent = messageService.send(new MessageModels.SendMessage("QUALIFICATION_EXPIRES",
                    Set.of(Long.toString(administratorId)), Map.of("supplierName", "示例供应商",
                    "qualificationName", "安全生产许可证", "expiryDate", "2026-12-31"),
                    "SUPPLIER_QUALIFICATION", "10001"));
            assertThat(sent.delivered()).isOne();
            assertThat(messageService.unread()).isOne();
            var inbox = messageService.inbox();
            assertThat(inbox).singleElement().satisfies(item ->
                    assertThat(item.content()).contains("示例供应商", "安全生产许可证"));
            messageService.read(Long.parseLong(inbox.get(0).id()));
            assertThat(messageService.unread()).isZero();
            var printTemplate = printService.create(new PrintModels.SaveTemplate("SUPPLIER_CARD", "供应商卡片",
                    "SUPPLIER", "<html><body><h1>{{supplierName}}</h1><p>{{creditCode}}</p></body></html>",
                    Set.of("supplierName", "creditCode"), "A4", "PORTRAIT", 0));
            var publishedTemplate = printService.publish(Long.parseLong(printTemplate.id()), printTemplate.version());
            assertThat(publishedTemplate.currentVersion()).isOne();
            var preview = printService.preview(Long.parseLong(printTemplate.id()), new PrintModels.RenderRequest(
                    Map.of("supplierName", "示例化工供应商", "creditCode", "91370000TEST"), "SUPPLIER", "10001"));
            assertThat(preview.html()).contains("示例化工供应商", "91370000TEST");
            var printJob = printService.print(Long.parseLong(printTemplate.id()), new PrintModels.RenderRequest(
                    Map.of("supplierName", "示例化工供应商", "creditCode", "91370000TEST"), "SUPPLIER", "10001"));
            assertThat(taskCenterService.task(Long.parseLong(printJob.taskId())).taskType()).isEqualTo("PRINT_DOCUMENT");
            assertThat(organizationService.tree()).singleElement()
                    .extracting(OrganizationModels.OrganizationNode::type).isEqualTo("HEADQUARTERS");
            var subsidiary = organizationService.create(new OrganizationModels.CreateOrganization(
                    Long.toString(headquartersId), "EAST_COMPANY", "华东子公司", "SUBSIDIARY", 10));
            var site = organizationService.create(new OrganizationModels.CreateOrganization(
                    subsidiary.id(), "CHEMICAL_SITE", "化工生产基地", "SITE", 10));
            assertThat(organizationService.switchTo(site.id()).id()).isEqualTo(site.id());
            assertThat(organizationService.current().name()).isEqualTo("化工生产基地");
            assertThatThrownBy(() -> organizationService.create(new OrganizationModels.CreateOrganization(
                    site.id(), "INVALID_SITE", "错误场站", "SITE", 20)))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> organizationService.disable(Long.parseLong(subsidiary.id()), subsidiary.version()))
                    .isInstanceOf(ApiException.class);

            var siteManager = accessService.createRole(new AccessModels.CreateRole("SITE_MANAGER", "场站管理员"));
            accessService.grantRole(Long.parseLong(siteManager.id()), new AccessModels.GrantRole(
                    Set.of("iam:organization:view", "iam:menu:view"), Set.of("ORGANIZATION_MANAGEMENT"),
                    List.of(new AccessModels.DataScopeGrant("iam:organization", "ORGANIZATION_SET",
                            Set.of(subsidiary.id())))));
            var siteUser = accessService.createUser(new AccessModels.CreateUser("site-manager", "场站管理员",
                    "Initial#Site123", site.id(), Set.of(siteManager.id())));
            var siteGrants = new DatabaseAuthorizationGrantLoader(tenantAuthorizationMapper)
                    .load(tenantId, Long.parseLong(siteUser.id()));
            assertThat(siteGrants.actions()).containsExactlyInAnyOrder("iam:organization:view", "iam:menu:view");
            assertThat(siteGrants.dataScope("iam:organization").organizationIds())
                    .contains(Long.parseLong(subsidiary.id()), Long.parseLong(site.id()));
        }

        assertThat(platformTenantService.suspend(Long.parseLong(ready.id())).status()).isEqualTo("SUSPENDED");
        assertThatThrownBy(() -> authService.login(new AuthModels.LoginCommand(
                "BOOTSTRAP-TENANT", "tenant-admin", "Initial#Pass123", "bootstrap-device"), "127.0.0.1"))
                .isInstanceOf(ApiException.class);
        assertThat(platformTenantService.resume(Long.parseLong(ready.id())).status()).isEqualTo("ACTIVE");
        platformTenantService.suspend(Long.parseLong(ready.id()));
        assertThat(platformTenantService.cancel(Long.parseLong(ready.id())).status()).isEqualTo("CANCELLED");
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
    void shouldSearchOnlyAuthorizedTenantResourcesAndPersistPersonalSchemes(){
        long tenantId=125L,userId=126L,organizationId=127L;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",tenantId,"SEARCH_IT","Search Tenant","ACTIVE");
        jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(?,?,?,?,?,?)",userId,tenantId,"search.user","Searchable User",passwordEncoder.encode("Search#123456"),"ACTIVE");
        jdbcTemplate.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,?,?,?,?)",organizationId,tenantId,"SEARCH_SITE","Searchable Site","SITE","ACTIVE");
        var permissions=new PermissionSnapshot(Set.of("iam:user:view","iam:organization:view"),Map.of("iam:user",DataScope.all(),"iam:organization",DataScope.all()),Set.of());
        try(var tenant=TenantContext.open(tenantId,userId);var authorization=AuthorizationContext.open(permissions)){
            var query=new SearchModels.SearchRequest("Searchable",Set.of(SearchModels.EntityType.USER,SearchModels.EntityType.ORGANIZATION,SearchModels.EntityType.FILE),Set.of("ACTIVE"),null,null,0,20);
            var result=searchService.search(query);
            assertThat(result.searchedTypes()).containsExactlyInAnyOrder(SearchModels.EntityType.USER,SearchModels.EntityType.ORGANIZATION);
            assertThat(result.items()).extracting(SearchModels.SearchItem::title).containsExactlyInAnyOrder("Searchable User","Searchable Site");
            var saved=searchService.create(new SearchModels.SaveSearch("常用搜索",query,true,0));
            assertThat(saved.defaultSearch()).isTrue();assertThat(searchService.saved()).extracting(SearchModels.SavedView::name).containsExactly("常用搜索");
            searchService.delete(Long.parseLong(saved.id()),saved.version());assertThat(searchService.saved()).isEmpty();
        }finally{
            jdbcTemplate.update("DELETE FROM src_saved_search WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM iam_organization WHERE id=?",organizationId);
            jdbcTemplate.update("DELETE FROM iam_user WHERE id=?",userId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=?",tenantId);
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
            assertThat(asyncTaskService.progress(nodeBTask.id(), "node-b", 60, "生成导出文件")).isTrue();
            assertThat(asyncTaskService.complete(nodeBTask.id(), "node-b")).isTrue();
            try (TenantContext.Scope ignored = TenantContext.open(tenantId, actorId)) {
                assertThat(taskCenterService.task(taskId).status()).isEqualTo("SUCCEEDED");
                assertThat(taskCenterService.task(taskId).progress()).isEqualTo(100);
            }
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

    @RestController
    static class ConsolePermissionProbeController {
        @GetMapping("/api/console/test-permission")
        @RequiresPermission("platform:tenant:view")
        Map<String, String> viewTenantSummary() {
            return Map.of("scope", "control-plane-only");
        }
    }
}

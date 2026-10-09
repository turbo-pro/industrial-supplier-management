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
import io.github.turbopro.ism.iam.console.ConsoleUserModels;
import io.github.turbopro.ism.iam.console.ConsoleUserService;
import io.github.turbopro.ism.iam.console.ConsoleUserErrorCode;
import io.github.turbopro.ism.iam.organization.OrganizationModels;
import io.github.turbopro.ism.iam.organization.OrganizationService;
import io.github.turbopro.ism.iam.authorization.DatabaseAuthorizationGrantLoader;
import io.github.turbopro.ism.iam.authorization.TenantAuthorizationMapper;
import io.github.turbopro.ism.iam.navigation.NavigationService;
import io.github.turbopro.ism.iam.access.AccessModels;
import io.github.turbopro.ism.iam.access.AccessErrorCode;
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
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="ism.integration.finance.mode=MANUAL")
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
    private ConsoleUserService consoleUserService;

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
    @Autowired private io.github.turbopro.ism.integration.table.TableViewService tableViewService;
    @Autowired private SafetyCredentialMapper safetyCredentialMapper;
    @Autowired private io.github.turbopro.ism.safety.SafetyAttendanceMapper safetyAttendanceMapper;
    @Autowired private io.github.turbopro.ism.safety.SafetyAttendanceService safetyAttendanceService;
    @Autowired private QualityNcrMapper qualityNcrMapper;
    @Autowired private PerformanceMapper performanceMapper;
    @Autowired private ImprovementMapper improvementMapper;
    @Autowired private SupplierReferenceService supplierReferenceService;
    @Autowired private io.github.turbopro.ism.supplier.RestrictionGateHitService restrictionGateHitService;
    @Autowired private io.github.turbopro.ism.supplier.ExitApplicationService exitApplicationService;
    @Autowired private io.github.turbopro.ism.supplier.ExitArchiveService exitArchiveService;
    @Autowired private io.github.turbopro.ism.supplier.ExitAccessRecoveryService exitAccessRecoveryService;
    @Autowired private io.github.turbopro.ism.supplier.ExitTaskService exitTaskService;
    @Autowired private ExitAutoReminderWorker exitAutoReminderWorker;
    @Autowired private ExitAccessAutoReminderWorker exitAccessAutoReminderWorker;
    @Autowired private io.github.turbopro.ism.supplier.ExitReminderFailureService exitReminderFailureService;
    @Autowired private io.github.turbopro.ism.supplier.RestrictionExplanationService restrictionExplanationService;
    @Autowired private io.github.turbopro.ism.supplier.SupplierService supplierService;
    @Autowired private io.github.turbopro.ism.supplier.PurchaseCategoryService purchaseCategoryService;
    @Autowired private io.github.turbopro.ism.qualification.AdmissionService admissionService;
    @Autowired private SupplierMapper supplierMapper;
    @Autowired private io.github.turbopro.ism.project.ContractProjectExitCheck contractProjectExitCheck;
    @Autowired private io.github.turbopro.ism.resource.supplier.ResourceExitCheck resourceExitCheck;
    @Autowired private io.github.turbopro.ism.quality.QualityExitCheck qualityExitCheck;
    @Autowired private io.github.turbopro.ism.safety.SafetyExitCheck safetyExitCheck;
    @Autowired private io.github.turbopro.ism.performance.PerformanceExitCheck performanceExitCheck;
    @Autowired private io.github.turbopro.ism.integration.finance.ManualClearanceMapper manualClearanceMapper;
    @Autowired private BlacklistMapper blacklistMapper;
    @Autowired private io.github.turbopro.ism.supplier.LiftService liftService;
    @Autowired private io.github.turbopro.ism.supplier.AppealService appealService;
    @Autowired private io.github.turbopro.ism.supplier.AppealMapper appealMapper;
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
    void shouldManageTenantPurchaseCategoriesAndSupplierAssignments() {
        long tenantId=99651,organizationId=99652,supplierId=99653,fileId=99654;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",tenantId,"CATEGORY_IT","Category Tenant","ACTIVE");
        try {
            jdbcTemplate.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,?,?,?,?)",organizationId,tenantId,"CATEGORY_ORG","Category Organization","SITE","ACTIVE");
            jdbcTemplate.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?)",supplierId,tenantId,organizationId,"CATEGORY_SUP","Category Supplier","MANUFACTURER","ACTIVE",1,1);
            jdbcTemplate.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(?,?,?,?,?,?,?,?,?,?)",fileId,tenantId,1,"license.pdf","application/pdf",1,"a".repeat(64),"LOCAL","it/category-license","ACTIVE");
            var permissions=new PermissionSnapshot(java.util.Set.of(),java.util.Map.of("supplier:master",DataScope.all(),"supplier:admission",DataScope.all()),java.util.Set.of());
            try(var tenant=TenantContext.open(tenantId,1);var authorization=AuthorizationContext.open(permissions)){
                var chemical=purchaseCategoryService.create(new io.github.turbopro.ism.supplier.PurchaseCategoryModels.Save("CHEMICAL_RAW","化工原料",io.github.turbopro.ism.supplier.PurchaseCategoryModels.Status.ACTIVE,0));
                var service=purchaseCategoryService.create(new io.github.turbopro.ism.supplier.PurchaseCategoryModels.Save("INDUSTRIAL_SERVICE","工业服务",io.github.turbopro.ism.supplier.PurchaseCategoryModels.Status.ACTIVE,0));
                assertThat(purchaseCategoryService.categories()).hasSize(2);
                var assigned=purchaseCategoryService.assign(supplierId,new io.github.turbopro.ism.supplier.PurchaseCategoryModels.Assign(java.util.List.of(chemical.id(),service.id()),0));
                assertThat(assigned.version()).isOne();
                assertThat(purchaseCategoryService.assignment(supplierId).categoryIds()).containsExactlyInAnyOrder(chemical.id(),service.id());
                var material=new io.github.turbopro.ism.qualification.AdmissionModels.MaterialCommand("BUSINESS_LICENSE","营业执照",Long.toString(fileId),true,true,null,10);
                assertThatThrownBy(()->admissionService.create(new io.github.turbopro.ism.qualification.AdmissionModels.SaveApplication(Long.toString(supplierId),"自由填写","准入验证",null,"CNY",java.util.List.of(material),0))).isInstanceOf(ApiException.class);
                var application=admissionService.create(new io.github.turbopro.ism.qualification.AdmissionModels.SaveApplication(Long.toString(supplierId),"任意客户端文本","准入验证",null,"CNY",java.util.List.of(material),0,chemical.id()));
                assertThat(application.purchaseCategoryId()).isEqualTo(chemical.id());
                assertThat(application.purchaseCategory()).isEqualTo("化工原料");
                var policy=purchaseCategoryService.saveMaterialPolicy(Long.parseLong(chemical.id()),new io.github.turbopro.ism.supplier.PurchaseCategoryModels.SaveRequiredMaterials(java.util.List.of(new io.github.turbopro.ism.supplier.PurchaseCategoryModels.RequiredMaterial("SAFETY_LICENSE","安全生产许可证")),0));
                assertThat(policy.version()).isOne();
                assertThat(policy.materials()).extracting(io.github.turbopro.ism.supplier.PurchaseCategoryModels.RequiredMaterial::type).containsExactly("SAFETY_LICENSE");
                assertThatThrownBy(()->purchaseCategoryService.saveMaterialPolicy(Long.parseLong(chemical.id()),new io.github.turbopro.ism.supplier.PurchaseCategoryModels.SaveRequiredMaterials(java.util.List.of(),0))).isInstanceOf(ApiException.class);
                assertThatThrownBy(()->admissionService.submit(Long.parseLong(application.id()),application.version())).isInstanceOf(ApiException.class);
                assertThatThrownBy(()->admissionService.create(new io.github.turbopro.ism.qualification.AdmissionModels.SaveApplication(Long.toString(supplierId),"化工原料","规则绕过",null,"CNY",java.util.List.of(material),0,chemical.id()))).isInstanceOf(ApiException.class);
                assertThat(supplierService.list("", "",null,null,null,Long.parseLong(chemical.id()),0,20).items()).extracting(io.github.turbopro.ism.supplier.SupplierModels.SupplierSummary::id).containsExactly(Long.toString(supplierId));
                assertThat(supplierService.list("", "",null,null,null,Long.parseLong(chemical.id()),0,20).total()).isOne();
                assertThatThrownBy(()->purchaseCategoryService.assign(supplierId,new io.github.turbopro.ism.supplier.PurchaseCategoryModels.Assign(java.util.List.of(),0))).isInstanceOf(ApiException.class);
                purchaseCategoryService.update(Long.parseLong(chemical.id()),new io.github.turbopro.ism.supplier.PurchaseCategoryModels.Save("CHEMICAL_RAW","化工原料",io.github.turbopro.ism.supplier.PurchaseCategoryModels.Status.INACTIVE,1));
                assertThatThrownBy(()->admissionService.submit(Long.parseLong(application.id()),application.version())).isInstanceOf(ApiException.class);
                assertThat(purchaseCategoryService.assignment(supplierId).categoryIds()).contains(chemical.id());
                var retained=purchaseCategoryService.assign(supplierId,new io.github.turbopro.ism.supplier.PurchaseCategoryModels.Assign(java.util.List.of(chemical.id()),1));
                assertThat(retained.version()).isEqualTo(2);
                assertThatThrownBy(()->purchaseCategoryService.assign(supplierId,new io.github.turbopro.ism.supplier.PurchaseCategoryModels.Assign(java.util.List.of(chemical.id(),service.id(),chemical.id()),2))).isInstanceOf(ApiException.class);
                try(var other=TenantContext.open(tenantId+1,1)){
                    assertThat(purchaseCategoryService.categories()).isEmpty();
                    assertThatThrownBy(()->purchaseCategoryService.materialPolicy(Long.parseLong(chemical.id()))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->purchaseCategoryService.assignment(supplierId)).isInstanceOf(ApiException.class);
                }
                assertThat(supplierService.list("", "",null,null,null,Long.parseLong(chemical.id()),0,20).total()).isOne();
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_PURCHASE_CATEGORIES_UPDATE'",Integer.class,tenantId)).isEqualTo(2);
            }
        } finally {
            jdbcTemplate.update("DELETE FROM qua_admission_review WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM qua_admission_material WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM qua_admission_application WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_purchase_category_admission_material WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_supplier_purchase_category WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_purchase_category WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_supplier WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM res_file_object WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM iam_organization WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sys_audit_event WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=?",tenantId);
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
                long gateHitsBeforeRollback=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_restriction_gate_hit WHERE tenant_id=? AND supplier_id=?",Long.class,tenantId,supplierId);
                var deniedTransaction=new org.springframework.transaction.support.TransactionTemplate(b7TransactionManager);
                deniedTransaction.executeWithoutResult(tx->{assertThat(supplierReferenceService.eligibleForBusiness(supplierId,io.github.turbopro.ism.supplier.SupplierRestrictionEvaluator.Action.CONTRACT_CREATE)).isNull();tx.setRollbackOnly();});
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_restriction_gate_hit WHERE tenant_id=? AND supplier_id=?",Long.class,tenantId,supplierId)).isGreaterThan(gateHitsBeforeRollback);
                try(var hitReader=TenantContext.open(tenantId,4);var hitAuth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){
                    assertThat(restrictionGateHitService.list(supplierId,0,20).items()).anySatisfy(hit->{assertThat(hit.action()).isEqualTo("CONTRACT_CREATE");assertThat(hit.decision()).isEqualTo("DENY");});
                }
                try(var hitReader=TenantContext.open(tenantId,4);var hitAuth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.none()),Set.of()))){
                    assertThatThrownBy(()->restrictionGateHitService.list(supplierId,0,20)).isInstanceOf(ApiException.class);
                }
                try(var hitReader=TenantContext.open(tenantId+100000,4);var hitAuth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){
                    assertThatThrownBy(()->restrictionGateHitService.list(supplierId,0,20)).isInstanceOf(ApiException.class);
                }
                long appealId;
                try(var applicant=TenantContext.open(tenantId,3L);var authorization=io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext.open(new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(java.util.Set.of(),java.util.Map.of("supplier:master",io.github.turbopro.ism.common.infrastructure.authorization.DataScope.all()),java.util.Set.of()))){
                    var appeal=appealService.create(blacklistId,new io.github.turbopro.ism.supplier.AppealModels.Create("提出新证据",Long.toString(fileId)));
                    appealId=Long.parseLong(appeal.id());
                    assertThat(appealService.list(blacklistId,0,20).total()).isOne();
                    assertThatThrownBy(()->appealService.create(blacklistId,new io.github.turbopro.ism.supplier.AppealModels.Create("重复申诉",Long.toString(fileId)))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                }
                assertThat(blacklistMapper.active(tenantId,supplierId,java.time.LocalDate.now())).isOne();
                assertThat(appealMapper.review(tenantId,blacklistId,appealId,"ACCEPTED","过期版本",4,1)).isZero();
                try(var originalApprover=TenantContext.open(tenantId,2L);var authorization=io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext.open(new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(java.util.Set.of(),java.util.Map.of("supplier:master",io.github.turbopro.ism.common.infrastructure.authorization.DataScope.all()),java.util.Set.of()))){
                    assertThatThrownBy(()->appealService.review(blacklistId,appealId,new io.github.turbopro.ism.supplier.AppealModels.Review(io.github.turbopro.ism.supplier.AppealModels.Decision.ACCEPT,"自审",0))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                }
                try(var reviewer=TenantContext.open(tenantId,4L);var authorization=io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext.open(new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(java.util.Set.of(),java.util.Map.of("supplier:master",io.github.turbopro.ism.common.infrastructure.authorization.DataScope.all()),java.util.Set.of()))){
                    assertThat(appealService.review(blacklistId,appealId,new io.github.turbopro.ism.supplier.AppealModels.Review(io.github.turbopro.ism.supplier.AppealModels.Decision.ACCEPT,"申诉成立，另行解除审批",0)).status()).isEqualTo("ACCEPTED");
                    assertThatThrownBy(()->appealService.review(blacklistId,appealId,new io.github.turbopro.ism.supplier.AppealModels.Review(io.github.turbopro.ism.supplier.AppealModels.Decision.REJECT,"重复",1))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                }
                try(var otherTenant=TenantContext.open(tenantId+100000,4L);var authorization=io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext.open(new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(java.util.Set.of(),java.util.Map.of("supplier:master",io.github.turbopro.ism.common.infrastructure.authorization.DataScope.all()),java.util.Set.of()))){
                    assertThatThrownBy(()->appealService.list(blacklistId,0,20)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                }
                assertThat(blacklistMapper.active(tenantId,supplierId,java.time.LocalDate.now())).isOne();
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
                assertThat(supplierMapper.list(tenantId, null, null, null, null, null, null, "TENANT_ALL", java.util.Set.of(), 1, 0, 20))
                        .anySatisfy(supplier -> { assertThat(supplier.id()).isEqualTo(Long.toString(supplierId)); assertThat(supplier.observed()).isTrue(); });
                assertThat(supplierMapper.count(tenantId,null,null,null,"SERVICE_PROVIDER","LOW",null,"TENANT_ALL",java.util.Set.of(),1)).isOne();
                assertThat(supplierMapper.list(tenantId,null,null,null,"SERVICE_PROVIDER","LOW",null,"TENANT_ALL",java.util.Set.of(),1,0,20))
                        .extracting(io.github.turbopro.ism.supplier.SupplierModels.SupplierSummary::id).containsExactly(Long.toString(supplierId));
                assertThat(supplierMapper.count(tenantId,null,null,null,"CONTRACTOR","LOW",null,"TENANT_ALL",java.util.Set.of(),1)).isZero();
                assertThat(supplierMapper.count(tenantId,null,null,null,"SERVICE_PROVIDER","HIGH",null,"TENANT_ALL",java.util.Set.of(),1)).isZero();
                assertThat(supplierMapper.count(tenantId,null,null,null,"SERVICE_PROVIDER","LOW",null,"ORGANIZATION_SET",java.util.Set.of(orgId+1),1)).isZero();
                try(var otherTenant=TenantContext.open(tenantId+100000,1)){
                    assertThat(supplierMapper.count(tenantId+100000,null,null,null,"SERVICE_PROVIDER","LOW",null,"TENANT_ALL",java.util.Set.of(),1)).isZero();
                }
                assertThat(blacklistMapper.openCase(tenantId, supplierId, java.time.LocalDate.now(), "WATCH")).isOne();
                assertThat(blacklistMapper.openCase(tenantId, supplierId, java.time.LocalDate.now(), "BLACKLIST")).isZero();
                var observationPermissions=new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(java.util.Set.of(),java.util.Map.of("supplier:master",io.github.turbopro.ism.common.infrastructure.authorization.DataScope.all()),java.util.Set.of());
                try(var actor=TenantContext.open(tenantId,3);var authorization=AuthorizationContext.open(observationPermissions)){
                    var explanation=restrictionExplanationService.explain(supplierId,io.github.turbopro.ism.supplier.RestrictionExplanationService.Action.PROJECT_CREATE);
                    assertThat(explanation.decision()).isEqualTo(io.github.turbopro.ism.supplier.RestrictionExplanationService.Decision.WARN);
                    assertThat(explanation.hits()).extracting(io.github.turbopro.ism.supplier.RestrictionExplanationService.Hit::sourceId).containsExactly(Long.toString(watchId));
                    for(var action:io.github.turbopro.ism.supplier.SupplierRestrictionEvaluator.Action.values())assertThat(supplierReferenceService.eligibleForBusiness(supplierId,action)).isNotNull();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_RESTRICTION_EXPLAIN'",Long.class,tenantId)).isPositive();
                }
                String observationApplication;
                try(var applicant=TenantContext.open(tenantId,3);var authorization=AuthorizationContext.open(observationPermissions)){
                    configurationService.updateSetting("restriction.watchPeriodDays",new ConfigurationModels.UpdateSetting("30",0));
                    assertThatThrownBy(()->configurationService.updateSetting("restriction.watchPeriodDays",new ConfigurationModels.UpdateSetting("0",0))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->configurationService.updateSetting("restriction.watchPeriodDays",new ConfigurationModels.UpdateSetting("3651",0))).isInstanceOf(ApiException.class);
                    var application=liftService.create(watchId,new io.github.turbopro.ism.supplier.LiftModels.Create("观察解除",Long.toString(fileId)));
                    assertThat(application.observationSeconds()).isEqualTo(java.time.Duration.ofDays(30).getSeconds());
                    observationApplication=application.id();
                    configurationService.updateSetting("restriction.watchPeriodDays",new ConfigurationModels.UpdateSetting("7",0));
                    assertThatThrownBy(()->configurationService.updateSetting("restriction.watchPeriodDays",new ConfigurationModels.UpdateSetting("15",0))).isInstanceOf(ApiException.class);
                }
                jdbcTemplate.update("UPDATE sup_blacklist_case SET reviewed_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 10 DAY) WHERE tenant_id=? AND id=?",tenantId,watchId);
                try(var reviewer=TenantContext.open(tenantId,4);var authorization=AuthorizationContext.open(observationPermissions)){
                    assertThat(liftService.readiness(watchId).ready()).isFalse();
                    assertThatThrownBy(()->liftService.review(watchId,Long.parseLong(observationApplication),new io.github.turbopro.ism.supplier.LiftModels.Review(io.github.turbopro.ism.supplier.LiftModels.Decision.APPROVE,"提前解除",0))).isInstanceOf(ApiException.class);
                    jdbcTemplate.update("UPDATE sup_blacklist_case SET reviewed_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 40 DAY) WHERE tenant_id=? AND id=?",tenantId,watchId);
                    assertThat(liftService.readiness(watchId).ready()).isTrue();
                    assertThat(liftService.review(watchId,Long.parseLong(observationApplication),new io.github.turbopro.ism.supplier.LiftModels.Review(io.github.turbopro.ism.supplier.LiftModels.Decision.APPROVE,"观察完成",0)).status()).isEqualTo("APPROVED");
                }
                assertThat(blacklistMapper.observed(tenantId,supplierId)).isZero();
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
                for(long restrictionId:new long[]{9981,9982}){
                    String type=restrictionId==9981?"BLACKLIST":"TEMPORARY";
                    var start=restrictionId==9981?null:java.time.LocalDate.now();
                    var end=restrictionId==9981?null:java.time.LocalDate.now().plusDays(2);
                    assertThat(blacklistMapper.insert(restrictionId,tenantId,supplierId,orgId,"PERFORMANCE_SUP","Performance Supplier",type,start,end,"解除验收","LIFT-IT",1)).isOne();
                    assertThat(blacklistMapper.submit(tenantId,restrictionId,1,0)).isOne();
                    assertThat(blacklistMapper.review(tenantId,restrictionId,"APPROVE","批准",2,1)).isOne();
                }
                String liftId;
                var liftPermissions=new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(java.util.Set.of(),java.util.Map.of("supplier:master",io.github.turbopro.ism.common.infrastructure.authorization.DataScope.all()),java.util.Set.of());
                try(var applicant=TenantContext.open(tenantId,3);var authorization=AuthorizationContext.open(liftPermissions)){
                    liftId=liftService.create(9981,new io.github.turbopro.ism.supplier.LiftModels.Create("整改完成",Long.toString(fileId))).id();
                    org.junit.jupiter.api.Assertions.assertThrows(io.github.turbopro.ism.common.api.error.ApiException.class,()->liftService.create(9981,new io.github.turbopro.ism.supplier.LiftModels.Create("重复",Long.toString(fileId))));
                    org.junit.jupiter.api.Assertions.assertThrows(io.github.turbopro.ism.common.api.error.ApiException.class,()->liftService.review(9981,Long.parseLong(liftId),new io.github.turbopro.ism.supplier.LiftModels.Review(io.github.turbopro.ism.supplier.LiftModels.Decision.APPROVE,"自审",0)));
                }
                try(var reviewer=TenantContext.open(tenantId,4);var authorization=AuthorizationContext.open(liftPermissions)){
                    assertThat(liftService.readiness(9981).ready()).isTrue();
                    assertThat(liftService.review(9981,Long.parseLong(liftId),new io.github.turbopro.ism.supplier.LiftModels.Review(io.github.turbopro.ism.supplier.LiftModels.Decision.APPROVE,"核验通过",0)).status()).isEqualTo("APPROVED");
                }
                assertThat(blacklistMapper.currentCase(tenantId,9981).status()).isEqualTo("APPROVED");
                assertThat(blacklistMapper.lifted(tenantId,9981)).isOne();
                try(var actor=TenantContext.open(tenantId,3);var authorization=AuthorizationContext.open(liftPermissions)){
                    long beforeExplain=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_restriction_gate_hit WHERE tenant_id=? AND supplier_id=?",Long.class,tenantId,supplierId);
                    var explanation=restrictionExplanationService.explain(supplierId,io.github.turbopro.ism.supplier.RestrictionExplanationService.Action.PROJECT_CREATE);
                    assertThat(explanation.decision()).isEqualTo(io.github.turbopro.ism.supplier.RestrictionExplanationService.Decision.DENY);
                    assertThat(explanation.hits()).extracting(io.github.turbopro.ism.supplier.RestrictionExplanationService.Hit::sourceId).containsExactly("9982");
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_restriction_gate_hit WHERE tenant_id=? AND supplier_id=?",Long.class,tenantId,supplierId)).isEqualTo(beforeExplain);
                    for(var action:io.github.turbopro.ism.supplier.SupplierRestrictionEvaluator.Action.values())assertThat(supplierReferenceService.eligibleForBusiness(supplierId,action)).isNull();
                }
                assertThat(blacklistMapper.currentEffectiveCases(tenantId,supplierId,java.time.LocalDate.now())).extracting(io.github.turbopro.ism.supplier.BlacklistModels.Row::id).containsExactly(9982L);
                assertThat(blacklistMapper.active(tenantId,supplierId,java.time.LocalDate.now())).isOne();
                assertThat(supplierReferenceService.activeForNewBusiness(supplierId)).isNull();
                assertThat(blacklistMapper.revoke(tenantId,9982,"测试清理",2,2)).isOne();
                assertThat(supplierReferenceService.activeForNewBusiness(supplierId)).isNotNull();
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
                assertThat(manualClearanceMapper.resubmit(tenantId,supplierId,fileId,"退出前重新核验",1,2)).isOne();
                assertThat(manualClearanceMapper.review(tenantId,supplierId,"APPROVED","财务核验通过",2,3)).isOne();
                var exitPermissions=new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(java.util.Set.of("supplier:exit:review","supplier:exit:assign","supplier:exit:remind"),java.util.Map.of("supplier:master",io.github.turbopro.ism.common.infrastructure.authorization.DataScope.all()),java.util.Set.of());
                var previewTransaction=new org.springframework.transaction.support.TransactionTemplate(b7TransactionManager);
                var exitSubmitTransaction=new org.springframework.transaction.support.TransactionTemplate(b7TransactionManager);
                exitSubmitTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                var firstExitId=new java.util.concurrent.atomic.AtomicReference<String>();
                previewTransaction.executeWithoutResult(tx->{
                    assertThat(supplierMapper.find(tenantId,supplierId).status()).isEqualTo("ACTIVE");
                    exitSubmitTransaction.executeWithoutResult(submit->{
                        try(var applicant=TenantContext.open(tenantId,3);var authorization=AuthorizationContext.open(exitPermissions)){
                            firstExitId.set(exitApplicationService.create(supplierId,new io.github.turbopro.ism.supplier.ExitModels.Create(io.github.turbopro.ism.supplier.ExitModels.Type.NORMAL,"正常结束合作",Long.toString(fileId))).id());
                        }
                    });
                    assertThat(supplierReferenceService.activeForNewBusiness(supplierId)).isNull();
                    assertThat(supplierReferenceService.eligibleForAdmission(supplierId)).isNull();
                });
                final long firstId=Long.parseLong(firstExitId.get());
                try(var applicant=TenantContext.open(tenantId,3);var authorization=AuthorizationContext.open(exitPermissions)){
                    assertThatThrownBy(()->exitApplicationService.create(supplierId,new io.github.turbopro.ism.supplier.ExitModels.Create(io.github.turbopro.ism.supplier.ExitModels.Type.NORMAL,"重复申请",Long.toString(fileId)))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitApplicationService.review(supplierId,firstId,new io.github.turbopro.ism.supplier.ExitModels.Review(io.github.turbopro.ism.supplier.ExitModels.Decision.APPROVE,"自审",0))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->supplierService.changeStatus(supplierId,new io.github.turbopro.ism.supplier.SupplierModels.ChangeStatus(io.github.turbopro.ism.supplier.SupplierModels.Status.EXITED,"绕过审批",0))).isInstanceOf(ApiException.class);
                }
                try(var reviewer=TenantContext.open(tenantId,4);var authorization=AuthorizationContext.open(exitPermissions)){
                    assertThat(exitApplicationService.review(supplierId,firstId,new io.github.turbopro.ism.supplier.ExitModels.Review(io.github.turbopro.ism.supplier.ExitModels.Decision.REJECT,"退回处置",0)).status()).isEqualTo("REJECTED");
                }
                assertThat(supplierReferenceService.activeForNewBusiness(supplierId)).isNotNull();
                long exitId;
                try(var applicant=TenantContext.open(tenantId,3);var authorization=AuthorizationContext.open(exitPermissions)){
                    exitId=Long.parseLong(exitApplicationService.create(supplierId,new io.github.turbopro.ism.supplier.ExitModels.Create(io.github.turbopro.ism.supplier.ExitModels.Type.NORMAL,"准备退出",Long.toString(fileId))).id());
                    assertThat(exitApplicationService.cancel(supplierId,exitId,new io.github.turbopro.ism.supplier.ExitModels.Version(0)).status()).isEqualTo("CANCELLED");
                    exitId=Long.parseLong(exitApplicationService.create(supplierId,new io.github.turbopro.ism.supplier.ExitModels.Create(io.github.turbopro.ism.supplier.ExitModels.Type.ELIMINATION,"正式淘汰",Long.toString(fileId))).id());
                }
                final long finalExitId=exitId;
                jdbcTemplate.update("INSERT INTO prj_contract(id,tenant_id,organization_id,supplier_id,contract_no,contract_name,contract_type,amount,start_date,end_date,owner_id,file_id,created_by,updated_by) VALUES(9975,?,?,?,'EXIT-LATE','新增处置事项','SERVICE',10,CURRENT_DATE,CURRENT_DATE,1,?,1,1)",tenantId,orgId,supplierId,fileId);
                jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(89781,'EXIT_ENTITY_FOREIGN','退出事项外租户','ACTIVE')");
                jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(9976,?,'exit-owner','处置责任人','not-a-login-password','ACTIVE'),(9977,?,'exit-disabled','停用责任人','not-a-login-password','DISABLED'),(9978,89781,'exit-foreign','外租户责任人','not-a-login-password','ACTIVE')",tenantId,tenantId);
                try(var reviewer=TenantContext.open(tenantId,4);var authorization=AuthorizationContext.open(exitPermissions)){
                    assertThatThrownBy(()->exitApplicationService.review(supplierId,finalExitId,new io.github.turbopro.ism.supplier.ExitModels.Review(io.github.turbopro.ism.supplier.ExitModels.Decision.APPROVE,"过期快照",0))).isInstanceOf(ApiException.class);
                    var blocked=exitApplicationService.recheck(supplierId,finalExitId,new io.github.turbopro.ism.supplier.ExitModels.Version(0));
                    assertThat(blocked.localReady()).isFalse();assertThat(blocked.version()).isOne();
                    assertThat(blocked.items()).filteredOn(item->item.code().equals("OPEN_CONTRACT")).singleElement().satisfies(item->{assertThat(item.initialCount()).isZero();assertThat(item.currentCount()).isOne();});
                    assertThat(blocked.entities()).singleElement().satisfies(entity->{assertThat(entity.code()).isEqualTo("OPEN_CONTRACT");assertThat(entity.sourceId()).isEqualTo("9975");assertThat(entity.state()).isEqualTo("OPEN");});
                    var entity=blocked.entities().get(0);long entityId=Long.parseLong(entity.id());
                    assertThat(exitMonitor(tenantId,4,DataScope.all(),true,false).items()).singleElement().satisfies(item->{assertThat(item.id()).isEqualTo(entity.id());assertThat(item.assigneeId()).isNull();});
                    assertThat(exitMonitor(tenantId,4,DataScope.none(),false,false).total()).isZero();
                    assertThat(exitMonitor(89781,4,DataScope.all(),false,false).total()).isZero();
                    assertThat(blocked.entityTotal()).isOne();assertThat(exitApplicationService.entities(supplierId,finalExitId,0,20).items()).hasSize(1);
                    assertThat(exitApplicationService.entities(supplierId,finalExitId,1,20).items()).isEmpty();
                    for(String invalidOwner:List.of("9977","9978","9979"))assertThatThrownBy(()->exitApplicationService.assign(supplierId,finalExitId,entityId,new io.github.turbopro.ism.supplier.ExitModels.Assign(invalidOwner,"不能越权分派",entity.version(),blocked.version()))).isInstanceOf(ApiException.class);
                    var assignmentTemplate=messageService.create(new MessageModels.SaveTemplate("SUPPLIER_EXIT_ASSIGNMENT","退出事项责任分派","IN_APP","退出事项待处置","供应商 {{supplierId}} / 申请 {{applicationId}} / {{code}} / {{sourceId}}，请处置后重新核验",Set.of("supplierId","applicationId","code","sourceId"),0));
                    jdbcTemplate.update("UPDATE msg_template SET status='DISABLED' WHERE id=?",Long.parseLong(assignmentTemplate.id()));
                    assertThatThrownBy(()->exitApplicationService.assign(supplierId,finalExitId,entityId,new io.github.turbopro.ism.supplier.ExitModels.Assign("9976","模板停用不能静默保存",entity.version(),blocked.version()))).isInstanceOf(ApiException.class);
                    assertThat(exitApplicationService.get(supplierId,finalExitId).version()).isEqualTo(blocked.version());
                    assertThat(exitApplicationService.get(supplierId,finalExitId).entities().get(0).assigneeId()).isNull();
                    jdbcTemplate.update("UPDATE msg_template SET status='ACTIVE' WHERE id=?",Long.parseLong(assignmentTemplate.id()));
                    var assigned=exitApplicationService.assign(supplierId,finalExitId,entityId,new io.github.turbopro.ism.supplier.ExitModels.Assign("9976","跟进合同结束，不手工清除阻断",entity.version(),blocked.version()));
                    assertThat(assigned.version()).isEqualTo(2);assertThat(assigned.localReady()).isFalse();
                    assertThat(assigned.entities().get(0).assigneeId()).isEqualTo("9976");assertThat(assigned.entities().get(0).state()).isEqualTo("OPEN");
                    long taskOrganization=supplierMapper.find(tenantId,supplierId).organizationId();
                    assertThat(exitTasks(tenantId,9976,DataScope.all()).items()).singleElement().satisfies(task->{assertThat(task.id()).isEqualTo(entity.id());assertThat(task.sourceId()).isEqualTo("9975");assertThat(task.applicationVersion()).isEqualTo(2);});
                    assertThat(exitMonitor(tenantId,4,DataScope.all(),true,false).total()).isZero();
                    assertThat(exitMonitor(tenantId,4,DataScope.organizations(Set.of(taskOrganization)),false,false).total()).isOne();
                    assertThat(exitMonitor(tenantId,4,DataScope.organizations(Set.of(taskOrganization+100000)),false,false).total()).isZero();
                    assertThat(exitTasks(tenantId,4,DataScope.all()).total()).isZero();
                    assertThat(exitTasks(89781,9976,DataScope.all()).total()).isZero();
                    assertThat(exitTasks(tenantId,9976,DataScope.none()).total()).isZero();
                    assertThat(exitTasks(tenantId,9976,DataScope.organizations(Set.of())).total()).isZero();
                    assertThat(exitTasks(tenantId,9976,DataScope.organizations(Set.of(taskOrganization))).total()).isOne();
                    assertThat(exitTasks(tenantId,9976,DataScope.organizations(Set.of(taskOrganization+100000))).total()).isZero();
                    assertThat(exitTasks(tenantId,9976,DataScope.created()).total()).isZero();
                    assertThat(exitTasks(tenantId,9976,DataScope.projects(Set.of(1L))).total()).isZero();
                    jdbcTemplate.update("UPDATE sup_exit_entity SET assignee_id=4 WHERE id=?",entityId);
                    assertThat(exitTasks(tenantId,9976,DataScope.all()).total()).isZero();
                    jdbcTemplate.update("UPDATE sup_exit_entity SET assignee_id=9976 WHERE id=?",entityId);
                    for(String terminal:List.of("CANCELLED","REJECTED","BUSINESS_CLOSED")){
                        jdbcTemplate.update("UPDATE sup_exit_application SET status=? WHERE id=?",terminal,finalExitId);
                        assertThat(exitTasks(tenantId,9976,DataScope.all()).total()).isZero();
                    }
                    jdbcTemplate.update("UPDATE sup_exit_application SET status='SUBMITTED' WHERE id=?",finalExitId);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox i JOIN msg_delivery d ON d.id=i.delivery_id AND d.tenant_id=i.tenant_id WHERE i.tenant_id=? AND i.recipient_id=9976 AND d.business_type='SUPPLIER_EXIT_ENTITY' AND d.business_id=?",Integer.class,tenantId,entityId)).isOne();
                    assertThatThrownBy(()->exitApplicationService.assign(supplierId,finalExitId,entityId,new io.github.turbopro.ism.supplier.ExitModels.Assign("9976","过期修改",entity.version(),blocked.version()))).isInstanceOf(ApiException.class);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=? AND recipient_id=9976",Integer.class,tenantId)).isOne();
                    assertThatThrownBy(()->exitApplicationService.review(supplierId,finalExitId,new io.github.turbopro.ism.supplier.ExitModels.Review(io.github.turbopro.ism.supplier.ExitModels.Decision.APPROVE,"说明不能放行",assigned.version()))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitApplicationService.recheck(supplierId,finalExitId,new io.github.turbopro.ism.supplier.ExitModels.Version(0))).isInstanceOf(ApiException.class);
                    var dueDate=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1);
                    var timed=exitApplicationService.deadline(supplierId,finalExitId,entityId,new io.github.turbopro.ism.supplier.ExitModels.Deadline(dueDate,"约定合同处置期限",assigned.entities().get(0).version(),assigned.version()));
                    assertThat(timed.version()).isEqualTo(3);assertThat(timed.entities().get(0).dueDate()).isEqualTo(dueDate);assertThat(timed.localReady()).isFalse();
                    assertThat(exitTasks(tenantId,9976,DataScope.all()).items().get(0).dueDate()).isEqualTo(dueDate);
                    jdbcTemplate.update("UPDATE sup_exit_entity SET due_date=? WHERE id=?",dueDate.minusDays(2),entityId);
                    assertThat(exitTasks(tenantId,9976,DataScope.all()).items().get(0).overdue()).isTrue();
                    assertThat(exitMonitor(tenantId,4,DataScope.all(),false,true).items()).singleElement().satisfies(item->{assertThat(item.id()).isEqualTo(entity.id());assertThat(item.overdue()).isTrue();});
                    jdbcTemplate.update("UPDATE sup_exit_entity SET due_date=? WHERE id=?",dueDate,entityId);
                    var reminderTemplate=messageService.create(new MessageModels.SaveTemplate("SUPPLIER_EXIT_REMINDER","退出事项催办","IN_APP","退出事项催办","供应商 {{supplierId}} / {{applicationId}} / {{code}} / {{sourceId}} / {{dueDate}}",Set.of("supplierId","applicationId","code","sourceId","dueDate"),0));
                    jdbcTemplate.update("UPDATE msg_template SET status='DISABLED' WHERE id=?",Long.parseLong(reminderTemplate.id()));
                    var reminderCommand=new io.github.turbopro.ism.supplier.ExitModels.Reminder(timed.entities().get(0).version(),timed.version());
                    assertThatThrownBy(()->exitApplicationService.remind(supplierId,finalExitId,entityId,reminderCommand)).isInstanceOf(ApiException.class);
                    assertThat(exitApplicationService.get(supplierId,finalExitId).version()).isEqualTo(timed.version());
                    assertThat(exitApplicationService.get(supplierId,finalExitId).entities().get(0).lastRemindedAt()).isNull();
                    jdbcTemplate.update("UPDATE msg_template SET status='ACTIVE' WHERE id=?",Long.parseLong(reminderTemplate.id()));
                    var reminded=exitApplicationService.remind(supplierId,finalExitId,entityId,reminderCommand);
                    assertThat(reminded.version()).isEqualTo(4);assertThat(reminded.entities().get(0).lastRemindedAt()).isNotNull();assertThat(reminded.localReady()).isFalse();
                    assertThatThrownBy(()->exitApplicationService.remind(supplierId,finalExitId,entityId,new io.github.turbopro.ism.supplier.ExitModels.Reminder(reminded.entities().get(0).version(),reminded.version()))).isInstanceOf(ApiException.class);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=? AND recipient_id=9976",Integer.class,tenantId)).isEqualTo(2);
                    jdbcTemplate.update("UPDATE sup_exit_entity SET due_date=?,last_reminded_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 25 HOUR) WHERE id=?",dueDate.minusDays(2),entityId);
                    exitAutoReminderWorker.poll();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=? AND recipient_id=9976",Integer.class,tenantId)).isEqualTo(2);
                    jdbcTemplate.update("INSERT INTO cfg_tenant_setting(id,tenant_id,setting_key,value_type,setting_value) VALUES(?,?,?,?,?)",99411,tenantId,"exit.autoReminderIntervalHours","INTEGER","24");
                    jdbcTemplate.update("INSERT INTO cfg_tenant_setting(id,tenant_id,setting_key,value_type,setting_value) VALUES(?,?,?,?,?)",99412,tenantId,"exit.autoReminderEnabled","INTEGER","1");
                    jdbcTemplate.update("UPDATE msg_template SET status='DISABLED' WHERE id=?",Long.parseLong(reminderTemplate.id()));
                    exitAutoReminderWorker.poll();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=? AND recipient_id=9976",Integer.class,tenantId)).isEqualTo(2);
                    assertThat(jdbcTemplate.queryForObject("SELECT version FROM sup_exit_entity WHERE id=?",Integer.class,entityId)).isEqualTo(reminded.entities().get(0).version());
                    var failure=exitReminderFailureService.list(supplierId,0,20);
                    assertThat(failure.total()).isOne();assertThat(failure.items().get(0).status()).isEqualTo("FAILED");
                    assertThat(failure.items().get(0).failureCount()).isOne();
                    assertThat(failure.items().get(0).reasonCode()).isEqualTo("COMMON_VALIDATION_FAILED");
                    exitAutoReminderWorker.poll();
                    assertThat(exitReminderFailureService.list(supplierId,0,20).items().get(0).failureCount()).isEqualTo(2);
                    jdbcTemplate.update("UPDATE msg_template SET status='ACTIVE' WHERE id=?",Long.parseLong(reminderTemplate.id()));
                    jdbcTemplate.update("UPDATE cfg_tenant_setting SET setting_value='48' WHERE tenant_id=? AND setting_key='exit.autoReminderIntervalHours'",tenantId);
                    exitAutoReminderWorker.poll();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=? AND recipient_id=9976",Integer.class,tenantId)).isEqualTo(2);
                    jdbcTemplate.update("UPDATE cfg_tenant_setting SET setting_value='24' WHERE tenant_id=? AND setting_key='exit.autoReminderIntervalHours'",tenantId);
                    jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(9979,?,'exit-escalation','升级接收人','not-a-login-password','ACTIVE')",tenantId);
                    assertThat(configurationService.escalationUsers("9979",0,20).items()).singleElement().satisfies(user->{assertThat(user.id()).isEqualTo("9979");assertThat(user.displayName()).isEqualTo("升级接收人");});
                    assertThat(configurationService.escalationUsers("exit-",0,20).items()).extracting(ConfigurationModels.EscalationUser::id).contains("9976","9979").doesNotContain("9977","9978");
                    jdbcTemplate.update("INSERT INTO cfg_tenant_setting(id,tenant_id,setting_key,value_type,setting_value) VALUES(?,?,?,?,?)",99413,tenantId,"exit.escalationRecipientId","INTEGER","9979");
                    jdbcTemplate.update("INSERT INTO cfg_tenant_setting(id,tenant_id,setting_key,value_type,setting_value) VALUES(?,?,?,?,?)",99414,tenantId,"exit.escalationAfterDays","INTEGER","1");
                    exitAutoReminderWorker.poll();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=? AND recipient_id=9976",Integer.class,tenantId)).isEqualTo(3);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=? AND recipient_id=9979",Integer.class,tenantId)).isOne();
                    assertThat(jdbcTemplate.queryForObject("SELECT actor_id FROM sup_exit_event WHERE tenant_id=? AND application_id=? AND action='AUTO_ENTITY_REMIND'",Long.class,tenantId,finalExitId)).isZero();
                    assertThat(jdbcTemplate.queryForObject("SELECT operator_id FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_EXIT_ENTITY_AUTO_REMIND' ORDER BY occurred_at DESC LIMIT 1",Long.class,tenantId)).isZero();
                    var recovered=exitReminderFailureService.list(supplierId,0,20).items().get(0);
                    assertThat(recovered.status()).isEqualTo("DELIVERED");assertThat(recovered.resolvedAt()).isNotNull();
                    assertThat(recovered.failureCount()).isEqualTo(2);
                    exitAutoReminderWorker.poll();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=? AND recipient_id=9976",Integer.class,tenantId)).isEqualTo(3);
                    jdbcTemplate.update("UPDATE cfg_tenant_setting SET setting_value='0' WHERE tenant_id=? AND setting_key='exit.autoReminderEnabled'",tenantId);
                    jdbcTemplate.update("UPDATE sup_exit_entity SET last_reminded_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 25 HOUR) WHERE id=?",entityId);
                    exitAutoReminderWorker.poll();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox WHERE tenant_id=? AND recipient_id=9976",Integer.class,tenantId)).isEqualTo(3);
                    var autoReminded=exitApplicationService.get(supplierId,finalExitId);
                    jdbcTemplate.update("UPDATE prj_contract SET status='TERMINATED' WHERE id=9975");
                    var ready=exitApplicationService.recheck(supplierId,finalExitId,new io.github.turbopro.ism.supplier.ExitModels.Version(autoReminded.version()));
                    assertThat(ready.localReady()).isTrue();
                    assertThat(ready.entities().get(0).state()).isEqualTo("CLEARED");assertThat(ready.entities().get(0).clearedAt()).isNotNull();assertThat(ready.entities().get(0).assigneeId()).isEqualTo("9976");
                    assertThat(ready.entities().get(0).note()).isEqualTo("跟进合同结束，不手工清除阻断");
                    assertThat(exitTasks(tenantId,9976,DataScope.all()).total()).isZero();
                    assertThat(exitMonitor(tenantId,4,DataScope.all(),false,false).total()).isZero();
                    assertThatThrownBy(()->exitApplicationService.assign(supplierId,finalExitId,entityId,new io.github.turbopro.ism.supplier.ExitModels.Assign("9976","不改结清历史",ready.entities().get(0).version(),ready.version()))).isInstanceOf(ApiException.class);
                    var closed=exitApplicationService.review(supplierId,finalExitId,new io.github.turbopro.ism.supplier.ExitModels.Review(io.github.turbopro.ism.supplier.ExitModels.Decision.APPROVE,"处置核验通过",ready.version()));
                    assertThat(closed.status()).isEqualTo("BUSINESS_CLOSED");assertThat(closed.result().completionScope()).isEqualTo("LOCAL_BUSINESS");
                    assertThat(closed.result().accessRecoveryStatus()).isEqualTo("NOT_VERIFIED");
                    var recovery=exitAccessRecoveryService.inventory(supplierId,finalExitId);
                    assertThat(recovery.accessRecoveryStatus()).isEqualTo("NOT_VERIFIED");
                    assertThat(recovery.tasks()).extracting(io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Task::channel)
                        .containsExactly("API_CREDENTIAL","DOOR_ACCESS","PORTAL_ACCOUNT");
                    assertThat(recovery.tasks()).extracting(io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Task::status)
                        .containsOnly("DISCOVERY_REQUIRED");
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_exit_access_recovery_task WHERE tenant_id=? AND application_id=?",Integer.class,tenantId,finalExitId)).isEqualTo(3);
                    long recoveryTaskId=Long.parseLong(recovery.tasks().get(0).id());
                    var discovered=exitAccessRecoveryService.record(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.RecordFinding(
                            io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Finding.PRESENT,Long.toString(fileId),"发现待回收账号，不含口令",0));
                    assertThat(discovered.accessRecoveryStatus()).isEqualTo("NOT_VERIFIED");
                    assertThat(discovered.tasks().get(0).status()).isEqualTo("DISCOVERY_RECORDED");
                    assertThat(discovered.tasks().get(0).finding()).isEqualTo("PRESENT");
                    assertThat(discovered.tasks().get(0).version()).isOne();
                    assertThat(discovered.tasks().get(0).events()).hasSize(1);
                    int beforeAccessMessages=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_delivery WHERE tenant_id=? AND business_type='SUPPLIER_EXIT_ACCESS' AND business_id=?",Integer.class,tenantId,recoveryTaskId);
                    var assignedRecovery=exitAccessRecoveryService.assign(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Assign("9976",java.time.LocalDate.now().plusDays(7),"核查实际访问清单",1));
                    assertThat(assignedRecovery.tasks().get(0).assigneeId()).isEqualTo("9976");
                    assertThat(assignedRecovery.tasks().get(0).assignments()).hasSize(1);
                    assertThat(assignedRecovery.tasks().get(0).version()).isEqualTo(2);
                    assertThat(assignedRecovery.accessRecoveryStatus()).isEqualTo("NOT_VERIFIED");
                    assertThat(accessTasks(tenantId,9976,DataScope.all()).items()).singleElement().satisfies(task->{
                        assertThat(task.id()).isEqualTo(Long.toString(recoveryTaskId));
                        assertThat(task.status()).isEqualTo("DISCOVERY_RECORDED");
                        assertThat(task.finding()).isEqualTo("PRESENT");
                    });
                    assertThat(accessTasks(tenantId,9976,DataScope.none()).total()).isZero();
                    assertThat(accessTasks(tenantId,9976,DataScope.organizations(Set.of(taskOrganization+100000))).total()).isZero();
                    assertThat(accessTasks(tenantId+100000,9976,DataScope.all()).total()).isZero();
                    assertThat(accessTasks(tenantId,9979,DataScope.all()).total()).isZero();
                    try(var taskOwner=TenantContext.open(tenantId,9976);var taskAuthorization=AuthorizationContext.open(new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(Set.of(),Map.of("supplier:master",DataScope.all()),Set.of()))){
                        assertThat(exitTaskService.accessMine(null,"API_CREDENTIAL",0,20).total()).isOne();
                        assertThat(exitTaskService.accessMine(null,"DOOR_ACCESS",0,20).total()).isZero();
                        assertThat(exitTaskService.accessMine("%",null,0,20).total()).isZero();
                        assertThatThrownBy(()->exitTaskService.accessMine(null,"UNKNOWN",0,20)).isInstanceOf(ApiException.class);
                    }
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_delivery WHERE tenant_id=? AND business_type='SUPPLIER_EXIT_ACCESS' AND business_id=?",Integer.class,tenantId,recoveryTaskId)).isEqualTo(beforeAccessMessages+1);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_inbox i JOIN msg_delivery d ON d.id=i.delivery_id WHERE i.tenant_id=? AND i.recipient_id=9976 AND d.business_type='SUPPLIER_EXIT_ACCESS' AND d.business_id=?",Integer.class,tenantId,recoveryTaskId)).isOne();
                    jdbcTemplate.update("UPDATE msg_template SET status='DISABLED' WHERE tenant_id=? AND template_code='SUPPLIER_EXIT_ACCESS_ASSIGNMENT'",tenantId);
                    assertThatThrownBy(()->exitAccessRecoveryService.assign(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Assign("9976",null,"模板停用应回滚",2))).isInstanceOf(ApiException.class);
                    assertThat(exitAccessRecoveryService.inventory(supplierId,finalExitId).tasks().get(0).version()).isEqualTo(2);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_exit_access_assignment_event WHERE tenant_id=? AND task_id=?",Integer.class,tenantId,recoveryTaskId)).isOne();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_delivery WHERE tenant_id=? AND business_type='SUPPLIER_EXIT_ACCESS' AND business_id=?",Integer.class,tenantId,recoveryTaskId)).isEqualTo(beforeAccessMessages+1);
                    jdbcTemplate.update("UPDATE msg_template SET status='ACTIVE' WHERE tenant_id=? AND template_code='SUPPLIER_EXIT_ACCESS_ASSIGNMENT'",tenantId);
                    var firstReminder=exitAccessRecoveryService.remind(supplierId,finalExitId,recoveryTaskId,new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Remind(2));
                    assertThat(firstReminder.tasks().get(0).version()).isEqualTo(3);
                    assertThat(firstReminder.tasks().get(0).lastRemindedAt()).isNotNull();
                    assertThat(firstReminder.tasks().get(0).reminders()).hasSize(1);
                    assertThat(firstReminder.accessRecoveryStatus()).isEqualTo("NOT_VERIFIED");
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_delivery WHERE tenant_id=? AND recipient_id=9976 AND business_type='SUPPLIER_EXIT_ACCESS' AND business_id=?",Integer.class,tenantId,recoveryTaskId)).isEqualTo(beforeAccessMessages+2);
                    assertThatThrownBy(()->exitAccessRecoveryService.remind(supplierId,finalExitId,recoveryTaskId,new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Remind(3))).isInstanceOf(ApiException.class);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_exit_access_reminder_event WHERE tenant_id=? AND task_id=?",Integer.class,tenantId,recoveryTaskId)).isOne();
                    jdbcTemplate.update("UPDATE sup_exit_access_recovery_task SET last_reminded_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 25 HOUR) WHERE id=?",recoveryTaskId);
                    jdbcTemplate.update("UPDATE msg_template SET status='DISABLED' WHERE tenant_id=? AND template_code='SUPPLIER_EXIT_ACCESS_REMINDER'",tenantId);
                    assertThatThrownBy(()->exitAccessRecoveryService.remind(supplierId,finalExitId,recoveryTaskId,new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Remind(3))).isInstanceOf(ApiException.class);
                    assertThat(exitAccessRecoveryService.inventory(supplierId,finalExitId).tasks().get(0).version()).isEqualTo(3);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_exit_access_reminder_event WHERE tenant_id=? AND task_id=?",Integer.class,tenantId,recoveryTaskId)).isOne();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_delivery WHERE tenant_id=? AND recipient_id=9976 AND business_type='SUPPLIER_EXIT_ACCESS' AND business_id=?",Integer.class,tenantId,recoveryTaskId)).isEqualTo(beforeAccessMessages+2);
                    jdbcTemplate.update("UPDATE msg_template SET status='ACTIVE' WHERE tenant_id=? AND template_code='SUPPLIER_EXIT_ACCESS_REMINDER'",tenantId);
                    assertThatThrownBy(()->exitAccessRecoveryService.assign(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Assign("9977",null,"停用账号",3))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitAccessRecoveryService.assign(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Assign("9978",null,"外租户账号",3))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitAccessRecoveryService.assign(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Assign("9976",null,"过期版本",1))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitAccessRecoveryService.record(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.RecordFinding(
                            io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Finding.ABSENT,Long.toString(fileId),"过期版本",0))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitAccessRecoveryService.record(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.RecordFinding(
                            io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Finding.ABSENT,"999999999999","不存在的证据",3))).isInstanceOf(ApiException.class);
                    jdbcTemplate.update("UPDATE sup_exit_access_recovery_task SET due_date=DATE_SUB(CURDATE(),INTERVAL 1 DAY) WHERE id=?",recoveryTaskId);
                    exitAccessAutoReminderWorker.poll();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_exit_access_reminder_event WHERE tenant_id=? AND task_id=?",Integer.class,tenantId,recoveryTaskId)).isOne();
                    jdbcTemplate.update("INSERT INTO cfg_tenant_setting(id,tenant_id,setting_key,value_type,setting_value) VALUES(?,?,?,?,?)",99415,tenantId,"exit.accessAutoReminderEnabled","INTEGER","1");
                    jdbcTemplate.update("INSERT INTO cfg_tenant_setting(id,tenant_id,setting_key,value_type,setting_value) VALUES(?,?,?,?,?)",99416,tenantId,"exit.accessAutoReminderIntervalHours","INTEGER","24");
                    jdbcTemplate.update("UPDATE msg_template SET status='DISABLED' WHERE tenant_id=? AND template_code='SUPPLIER_EXIT_ACCESS_REMINDER'",tenantId);
                    exitAccessAutoReminderWorker.poll();
                    assertThat(jdbcTemplate.queryForObject("SELECT version FROM sup_exit_access_recovery_task WHERE id=?",Integer.class,recoveryTaskId)).isEqualTo(3);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_exit_access_reminder_event WHERE tenant_id=? AND task_id=?",Integer.class,tenantId,recoveryTaskId)).isOne();
                    assertThat(exitAccessRecoveryService.inventory(supplierId,finalExitId).tasks().get(0).reminderFailureCount()).isOne();
                    assertThat(exitAccessRecoveryService.inventory(supplierId,finalExitId).tasks().get(0).reminderFailureStatus()).isEqualTo("FAILED");
                    exitAccessAutoReminderWorker.poll();
                    assertThat(exitAccessRecoveryService.inventory(supplierId,finalExitId).tasks().get(0).reminderFailureCount()).isEqualTo(2);
                    jdbcTemplate.update("UPDATE msg_template SET status='ACTIVE' WHERE tenant_id=? AND template_code='SUPPLIER_EXIT_ACCESS_REMINDER'",tenantId);
                    exitAccessAutoReminderWorker.poll();
                    exitAccessAutoReminderWorker.poll();
                    assertThat(jdbcTemplate.queryForObject("SELECT version FROM sup_exit_access_recovery_task WHERE id=?",Integer.class,recoveryTaskId)).isEqualTo(4);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_exit_access_reminder_event WHERE tenant_id=? AND task_id=?",Integer.class,tenantId,recoveryTaskId)).isEqualTo(2);
                    assertThat(jdbcTemplate.queryForObject("SELECT actor_id FROM sup_exit_access_reminder_event WHERE tenant_id=? AND task_id=? ORDER BY created_at DESC,id DESC LIMIT 1",Long.class,tenantId,recoveryTaskId)).isZero();
                    assertThat(exitAccessRecoveryService.inventory(supplierId,finalExitId).tasks().get(0).reminderFailureStatus()).isEqualTo("DELIVERED");
                    assertThat(exitAccessRecoveryService.inventory(supplierId,finalExitId).tasks().get(0).reminderResolvedAt()).isNotNull();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM msg_delivery WHERE tenant_id=? AND recipient_id=9976 AND business_type='SUPPLIER_EXIT_ACCESS' AND business_id=?",Integer.class,tenantId,recoveryTaskId)).isEqualTo(beforeAccessMessages+3);
                    var principalCommand=new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.RegisterPrincipal(
                        "acct-9976","生产门户账号",Long.toString(fileId),"人工发现的账号标识，未验证停用",4);
                    var withPrincipal=exitAccessRecoveryService.registerPrincipal(supplierId,finalExitId,recoveryTaskId,principalCommand);
                    assertThat(withPrincipal.tasks().get(0).version()).isEqualTo(5);
                    assertThat(withPrincipal.tasks().get(0).principals()).singleElement().satisfies(principal->{
                        assertThat(principal.externalReference()).isEqualTo("acct-9976");
                        assertThat(principal.evidenceFileId()).isEqualTo(Long.toString(fileId));
                    });
                    assertThat(withPrincipal.accessRecoveryStatus()).isEqualTo("NOT_VERIFIED");
                    assertThatThrownBy(()->exitAccessRecoveryService.registerPrincipal(supplierId,finalExitId,recoveryTaskId,principalCommand)).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitAccessRecoveryService.registerPrincipal(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.RegisterPrincipal("acct-9976","重复账号",Long.toString(fileId),"重复登记",5))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitAccessRecoveryService.registerPrincipal(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.RegisterPrincipal("other-9976","无效证据","999999999999","不得登记",5))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitAccessRecoveryService.registerPrincipal(supplierId,finalExitId,
                        Long.parseLong(withPrincipal.tasks().get(1).id()),
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.RegisterPrincipal("other-9976","未发现渠道",Long.toString(fileId),"不得登记",0))).isInstanceOf(ApiException.class);
                    assertThat(exitAccessRecoveryService.inventory(supplierId,finalExitId).tasks().get(0).version()).isEqualTo(5);
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sup_exit_access_principal WHERE tenant_id=? AND task_id=?",Integer.class,tenantId,recoveryTaskId)).isOne();
                    long principalId=Long.parseLong(withPrincipal.tasks().get(0).principals().get(0).id());
                    assertThatThrownBy(()->exitAccessRecoveryService.voidPrincipal(supplierId,finalExitId,recoveryTaskId,principalId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.VoidPrincipal("999999999999","证据无效不得作废",5,0))).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitAccessRecoveryService.voidPrincipal(supplierId,finalExitId,
                        Long.parseLong(withPrincipal.tasks().get(1).id()),principalId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.VoidPrincipal(Long.toString(fileId),"跨任务不得作废",0,0))).isInstanceOf(ApiException.class);
                    var voidedPrincipal=exitAccessRecoveryService.voidPrincipal(supplierId,finalExitId,recoveryTaskId,principalId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.VoidPrincipal(Long.toString(fileId),"登记标识有误，保留原记录",5,0));
                    assertThat(voidedPrincipal.tasks().get(0).version()).isEqualTo(6);
                    assertThat(voidedPrincipal.tasks().get(0).principals().get(0).status()).isEqualTo("VOIDED");
                    assertThat(voidedPrincipal.tasks().get(0).principals().get(0).voidReason()).contains("标识有误");
                    assertThat(voidedPrincipal.tasks().get(0).principals().get(0).voidedBy()).isEqualTo("4");
                    assertThatThrownBy(()->exitAccessRecoveryService.voidPrincipal(supplierId,finalExitId,recoveryTaskId,principalId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.VoidPrincipal(Long.toString(fileId),"不得重复作废",6,1))).isInstanceOf(ApiException.class);
                    var correctedPrincipal=exitAccessRecoveryService.registerPrincipal(supplierId,finalExitId,recoveryTaskId,
                        new io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.RegisterPrincipal("acct-9976","核对后的账号",Long.toString(fileId),"登记纠错后重新确认",6));
                    assertThat(correctedPrincipal.tasks().get(0).principals()).hasSize(2);
                    assertThat(correctedPrincipal.tasks().get(0).principals()).extracting(io.github.turbopro.ism.supplier.ExitAccessRecoveryModels.Principal::status).containsExactly("VOIDED","ACTIVE");
                    assertThat(correctedPrincipal.accessRecoveryStatus()).isEqualTo("NOT_VERIFIED");
                    assertThat(jdbcTemplate.queryForObject("SELECT access_recovery_status FROM sup_exit_result WHERE tenant_id=? AND application_id=?",String.class,tenantId,finalExitId)).isEqualTo("NOT_VERIFIED");
                    var archive=exitArchiveService.get(supplierId,finalExitId);
                    assertThat(archive.integrityVerified()).isTrue();
                    assertThat(archive.scope()).isEqualTo("LOCAL_RECORD_METADATA");
                    assertThat(archive.externalAccessStatus()).isEqualTo("NOT_VERIFIED");
                    assertThat(archive.entityCount()).isOne();
                    jdbcTemplate.update("UPDATE sup_exit_event SET comment=CONCAT(comment,'!') WHERE tenant_id=? AND application_id=? AND action='BUSINESS_CLOSE'",tenantId,finalExitId);
                    assertThat(exitArchiveService.get(supplierId,finalExitId).integrityVerified()).isFalse();
                    assertThat(closed.events()).extracting(io.github.turbopro.ism.supplier.ExitModels.Event::action).containsExactly("SUBMIT","RECHECK","ENTITY_ASSIGN","ENTITY_DEADLINE","ENTITY_REMIND","AUTO_ENTITY_REMIND","RECHECK","BUSINESS_CLOSE");
                    assertThat(exitApplicationService.list(supplierId,0,20).total()).isEqualTo(3);
                    assertThatThrownBy(()->exitApplicationService.review(supplierId,finalExitId,new io.github.turbopro.ism.supplier.ExitModels.Review(io.github.turbopro.ism.supplier.ExitModels.Decision.APPROVE,"重复",closed.version()))).isInstanceOf(ApiException.class);
                }
                try(var other=TenantContext.open(tenantId+100000,4);var authorization=AuthorizationContext.open(exitPermissions)){
                    assertThatThrownBy(()->exitApplicationService.list(supplierId,0,20)).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitAccessRecoveryService.inventory(supplierId,finalExitId)).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitReminderFailureService.list(supplierId,0,20)).isInstanceOf(ApiException.class);
                    assertThatThrownBy(()->exitArchiveService.get(supplierId,finalExitId)).isInstanceOf(ApiException.class);
                }
                assertThat(supplierMapper.find(tenantId,supplierId).status()).isEqualTo("EXITED");
                assertThat(supplierReferenceService.activeForNewBusiness(supplierId)).isNull();
                assertThat(improvementMapper.lockSupplier(tenantId, supplierId)).isNull();
                assertThat(improvementMapper.event(9949, tenantId, planId, "CREATE", null, "OPEN", null, 1)).isOne();
                assertThat(improvementMapper.events(tenantId, planId)).hasSize(1);
            }
        } finally {
            jdbcTemplate.update("DELETE FROM sup_exit_access_principal WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_access_reminder_event WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_access_assignment_event WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_access_recovery_event WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_access_recovery_task WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_blacklist_event WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_restriction_gate_hit WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM msg_inbox WHERE tenant_id=? AND recipient_id=9976",tenantId);
            jdbcTemplate.update("DELETE FROM msg_inbox WHERE tenant_id=? AND recipient_id=9979",tenantId);
            jdbcTemplate.update("DELETE FROM msg_delivery WHERE tenant_id=? AND recipient_id=9976",tenantId);
            jdbcTemplate.update("DELETE FROM msg_delivery WHERE tenant_id=? AND recipient_id=9979",tenantId);
            jdbcTemplate.update("DELETE FROM msg_template WHERE tenant_id=? AND template_code IN ('SUPPLIER_EXIT_ASSIGNMENT','SUPPLIER_EXIT_REMINDER','SUPPLIER_EXIT_ESCALATION','SUPPLIER_EXIT_ACCESS_ASSIGNMENT','SUPPLIER_EXIT_ACCESS_REMINDER')",tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_event WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_reminder_failure WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_entity WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_archive WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_result WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_item WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_exit_application WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_user WHERE id IN (9976,9977,9978,9979)");
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=89781");
            jdbcTemplate.update("DELETE FROM sup_lift_result WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_lift_application WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM cfg_tenant_setting WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM sup_restriction_appeal WHERE tenant_id=?", tenantId);
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
    void shouldManageConsoleAccountsWithoutTenantPermissionsOrLastAdminLoss() throws Exception {
        long adminId=9401L;
        jdbcTemplate.update("INSERT INTO plt_user(id,username,display_name,password_hash,status) VALUES(?,?,?,?,?)",
                adminId,"platform-account-admin","账号管理员",passwordEncoder.encode("Console#Pass123"),"ACTIVE");
        jdbcTemplate.update("INSERT INTO plt_user_role(user_id,role_id) VALUES(?,1001)",adminId);
        try {
            var adminToken=consoleAuthService.login(new ConsoleAuthModels.LoginCommand(
                    "platform-account-admin","Console#Pass123","account-device"),"127.0.0.1");
            mockMvc.perform(get("/api/console/users").header("Authorization","Bearer "+adminToken.accessToken()))
                    .andExpect(status().isOk());
            var support=consoleUserService.create(new ConsoleUserModels.Create(
                    "platform-account-support","支持人员","Temporary#Pass123","PLATFORM_SUPPORT"));
            long supportId=Long.parseLong(support.id());
            assertThat(support.passwordChangeRequired()).isTrue();
            // Simulate completion of the required first-login password change before testing role permissions.
            jdbcTemplate.update("UPDATE plt_user SET force_password_change=0 WHERE id=?",supportId);
            var supportToken=consoleAuthService.login(new ConsoleAuthModels.LoginCommand(
                    "platform-account-support","Temporary#Pass123","support-device"),"127.0.0.1");
            mockMvc.perform(get("/api/console/users").header("Authorization","Bearer "+supportToken.accessToken()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/console/users")
                            .header("Authorization","Bearer "+supportToken.accessToken())
                            .header("Idempotency-Key","support-must-not-create-001")
                            .contentType("application/json")
                            .content("{\"username\":\"forbidden-account\",\"displayName\":\"无权创建\",\"initialPassword\":\"Temporary#Pass123\",\"roleCode\":\"PLATFORM_ADMIN\"}"))
                    .andExpect(status().isForbidden());
            assertThatThrownBy(()->consoleUserService.changeStatus(adminId,
                    new ConsoleUserModels.ChangeStatus("DISABLED",0),adminId))
                    .isInstanceOfSatisfying(ApiException.class,error->assertThat(error.errorCode())
                            .isEqualTo(ConsoleUserErrorCode.SELF_DISABLE));
            assertThat(consoleUserService.changeStatus(supportId,
                    new ConsoleUserModels.ChangeStatus("DISABLED",0),adminId).status()).isEqualTo("DISABLED");
            mockMvc.perform(get("/api/console/auth/me").header("Authorization","Bearer "+supportToken.accessToken()))
                    .andExpect(status().isUnauthorized());
            assertThatThrownBy(()->consoleAuthService.login(new ConsoleAuthModels.LoginCommand(
                    "platform-account-support","Temporary#Pass123","support-device"),"127.0.0.1"))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(()->consoleUserService.changeStatus(supportId,
                    new ConsoleUserModels.ChangeStatus("ACTIVE",0),adminId)).isInstanceOf(ApiException.class);
            assertThat(consoleUserService.changeStatus(supportId,
                    new ConsoleUserModels.ChangeStatus("ACTIVE",1),adminId).status()).isEqualTo("ACTIVE");
            assertThatThrownBy(()->consoleAuthService.refresh(new ConsoleAuthModels.RefreshCommand(
                    supportToken.refreshToken(),"support-device"),"127.0.0.1"))
                    .isInstanceOf(ApiException.class);
            List<Long> otherAdmins=jdbcTemplate.queryForList("""
                    SELECT u.id FROM plt_user u JOIN plt_user_role ur ON ur.user_id=u.id
                    JOIN plt_role r ON r.id=ur.role_id WHERE u.status='ACTIVE'
                    AND r.role_code='PLATFORM_ADMIN' AND u.id<>?
                    """,Long.class,adminId);
            try {
                for(long otherId:otherAdmins) jdbcTemplate.update("UPDATE plt_user SET status='DISABLED' WHERE id=?",otherId);
                assertThatThrownBy(()->consoleUserService.changeStatus(adminId,
                        new ConsoleUserModels.ChangeStatus("DISABLED",0),supportId))
                        .isInstanceOfSatisfying(ApiException.class,error->assertThat(error.errorCode())
                                .isEqualTo(ConsoleUserErrorCode.LAST_ADMIN));
            } finally {
                for(long otherId:otherAdmins) jdbcTemplate.update("UPDATE plt_user SET status='ACTIVE' WHERE id=?",otherId);
            }
        } finally {
            jdbcTemplate.update("DELETE FROM plt_refresh_token WHERE user_id IN (SELECT id FROM plt_user WHERE username IN (?,?))",
                    "platform-account-admin","platform-account-support");
            jdbcTemplate.update("DELETE FROM plt_user_role WHERE user_id IN (SELECT id FROM plt_user WHERE username IN (?,?))",
                    "platform-account-admin","platform-account-support");
            jdbcTemplate.update("DELETE FROM plt_user WHERE username IN (?,?)","platform-account-admin","platform-account-support");
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
    void shouldGuideConsoleForcedAndInactivePasswordChanges() throws Exception {
        long userId = 9009L;
        jdbcTemplate.update("""
                INSERT INTO plt_user(id,username,display_name,password_hash,status,force_password_change)
                VALUES(?,?,?,?,?,?)
                """, userId, "platform-password-policy", "密码策略验收员",
                passwordEncoder.encode("Console#Pass123"), "ACTIVE", true);
        try {
            ConsoleAuthModels.LoginCommand initial = new ConsoleAuthModels.LoginCommand(
                    "platform-password-policy", "Console#Pass123", "policy-device");
            ConsoleAuthModels.TokenPair forced = consoleAuthService.login(initial, "127.0.0.1");
            assertThat(forced.user().passwordChangeRequired()).isTrue();
            assertThat(forced.user().passwordChangeRecommended()).isFalse();
            mockMvc.perform(get("/api/console/packages").header("Authorization", "Bearer " + forced.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("IAM_PASSWORD_CHANGE_REQUIRED"));
            consoleAuthService.changePassword(userId,
                    new ConsoleAuthModels.ChangePasswordCommand("Console#Pass123", "Changed#Pass456"));
            mockMvc.perform(get("/api/console/packages").header("Authorization", "Bearer " + forced.accessToken()))
                    .andExpect(status().isUnauthorized());
            ConsoleAuthModels.LoginCommand changed = new ConsoleAuthModels.LoginCommand(
                    "platform-password-policy", "Changed#Pass456", "policy-device");
            ConsoleAuthModels.TokenPair normal = consoleAuthService.login(changed, "127.0.0.1");
            assertThat(normal.user().passwordChangeRequired()).isFalse();
            assertThat(normal.user().passwordChangeRecommended()).isFalse();
            jdbcTemplate.update("UPDATE plt_user SET last_login_at=? WHERE id=?",
                    LocalDateTime.now().minusDays(91), userId);
            ConsoleAuthModels.TokenPair inactive = consoleAuthService.login(changed, "127.0.0.1");
            assertThat(inactive.user().passwordChangeRequired()).isFalse();
            assertThat(inactive.user().passwordChangeRecommended()).isTrue();
            assertThat(consoleAuthService.login(changed, "127.0.0.1").user().passwordChangeRecommended()).isFalse();
        } finally {
            jdbcTemplate.update("DELETE FROM plt_refresh_token WHERE user_id=?", userId);
            jdbcTemplate.update("DELETE FROM plt_user WHERE id=?", userId);
        }
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
            try (var otherTenant = TenantContext.open(tenantId + 1, administratorId)) {
                assertThatThrownBy(() -> fileService.file(Long.parseLong(storedFile.id())))
                        .isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> fileService.download(Long.parseLong(storedFile.id())))
                        .isInstanceOf(ApiException.class);
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
    void shouldEnforceTenantUserStatusAndLastAdministratorSafety() {
        long tenantId = 198000L, adminId = 198001L, memberId = 198002L, foreignTenantId = 198100L;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?, 'ACTIVE')", tenantId, "ACCESS-LIFECYCLE", "账号生命周期");
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?, 'ACTIVE')", foreignTenantId, "ACCESS-FOREIGN", "外部租户");
        try {
            jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(?,?,?,?,?,'ACTIVE')",
                    adminId, tenantId, "lifecycle-admin", "管理员", passwordEncoder.encode("Initial#Pass123"));
            jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(?,?,?,?,?,'ACTIVE')",
                    memberId, tenantId, "lifecycle-member", "成员", passwordEncoder.encode("Initial#Pass123"));
            jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(?,?,?,?,?,'ACTIVE')",
                    foreignTenantId + 1, foreignTenantId, "foreign-member", "外部成员", passwordEncoder.encode("Initial#Pass123"));
            jdbcTemplate.update("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(?,?, 'TENANT_ADMIN','租户管理员',1,'ACTIVE')", tenantId + 10, tenantId);
            jdbcTemplate.update("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(?,?, 'MEMBER','普通成员',0,'ACTIVE')", tenantId + 11, tenantId);
            jdbcTemplate.update("INSERT INTO iam_user_role(tenant_id,user_id,role_id) VALUES(?,?,?)", tenantId, adminId, tenantId + 10);
            jdbcTemplate.update("INSERT INTO iam_user_role(tenant_id,user_id,role_id) VALUES(?,?,?)", tenantId, memberId, tenantId + 11);
            AuthModels.TokenPair memberToken = authService.login(new AuthModels.LoginCommand(
                    "ACCESS-LIFECYCLE", "lifecycle-member", "Initial#Pass123", "lifecycle-device"), "127.0.0.1");
            try (var context = TenantContext.open(tenantId, adminId)) {
                assertThat(accessService.users()).filteredOn(user -> user.id().equals(Long.toString(memberId)))
                        .singleElement().satisfies(user -> assertThat(user.roleIds()).containsExactly(Long.toString(tenantId + 11)));
                assertThatThrownBy(() -> accessService.changeUserStatus(adminId, new AccessModels.ChangeUserStatus("DISABLED", 0)))
                        .isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> accessService.assignUserRoles(adminId,
                        new AccessModels.AssignUserRoles(Set.of(Long.toString(tenantId + 11)), 0)))
                        .isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> accessService.assignUserRoles(foreignTenantId + 1,
                        new AccessModels.AssignUserRoles(Set.of(Long.toString(tenantId + 11)), 0)))
                        .isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> accessService.changeUserStatus(foreignTenantId + 1,
                        new AccessModels.ChangeUserStatus("DISABLED", 0))).isInstanceOf(ApiException.class);
                var disabled = accessService.changeUserStatus(memberId, new AccessModels.ChangeUserStatus("DISABLED", 0));
                assertThat(disabled.status()).isEqualTo("DISABLED");
                assertThatThrownBy(() -> accessService.changeUserStatus(memberId, new AccessModels.ChangeUserStatus("ACTIVE", 0)))
                        .isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> authService.requireActiveUser(memberId, 0)).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> authService.refresh(new AuthModels.RefreshCommand(
                        memberToken.refreshToken(), "lifecycle-device"), "127.0.0.1")).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE", "lifecycle-member", "Initial#Pass123", "lifecycle-device"), "127.0.0.1"))
                        .isInstanceOf(ApiException.class);
                assertThat(accessService.changeUserStatus(memberId, new AccessModels.ChangeUserStatus("ACTIVE", 1)).status())
                        .isEqualTo("ACTIVE");
                AuthModels.TokenPair restored = authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE", "lifecycle-member", "Initial#Pass123", "lifecycle-device"), "127.0.0.1");
                assertThat(restored.accessToken()).isNotBlank();
                assertThatThrownBy(() -> accessService.assignUserRoles(memberId,
                        new AccessModels.AssignUserRoles(Set.of(Long.toString(tenantId + 10)), 1)))
                        .isInstanceOf(ApiException.class);
                var reassigned = accessService.assignUserRoles(memberId,
                        new AccessModels.AssignUserRoles(Set.of(Long.toString(tenantId + 10),Long.toString(tenantId + 11)), 2));
                assertThat(reassigned.version()).isEqualTo(3);
                assertThat(reassigned.roleIds()).containsExactlyInAnyOrder(Long.toString(tenantId + 10),Long.toString(tenantId + 11));
                assertThatThrownBy(() -> authService.requireActiveUser(memberId, 2)).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> authService.refresh(new AuthModels.RefreshCommand(
                        restored.refreshToken(), "lifecycle-device"), "127.0.0.1")).isInstanceOf(ApiException.class);
                assertThat(accessService.assignUserRoles(memberId,
                        new AccessModels.AssignUserRoles(Set.of(Long.toString(tenantId + 11)), 3)).roleIds())
                        .containsExactly(Long.toString(tenantId + 11));
            }
            try (var context = TenantContext.open(tenantId, memberId)) {
                assertThatThrownBy(() -> accessService.changeUserStatus(adminId,
                        new AccessModels.ChangeUserStatus("DISABLED", 0))).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> accessService.assignUserRoles(memberId,
                        new AccessModels.AssignUserRoles(Set.of(Long.toString(tenantId + 10)), 4)))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.errorCode()).isEqualTo(AccessErrorCode.ADMIN_GRANT_REQUIRES_ADMIN));
                assertThatThrownBy(() -> accessService.createUser(new AccessModels.CreateUser(
                        "unauthorized-admin", "未授权管理员", "Initial#Pass123", "1",
                        Set.of(Long.toString(tenantId + 10)))))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.errorCode()).isEqualTo(AccessErrorCode.ADMIN_GRANT_REQUIRES_ADMIN));
                assertThatThrownBy(() -> accessService.resetUserPassword(adminId,
                        new AccessModels.ResetUserPassword("Temporary#Pass123", 0)))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.errorCode()).isEqualTo(AccessErrorCode.PASSWORD_RESET_REQUIRES_ADMIN));
                assertThatThrownBy(() -> accessService.changeLoginLock(adminId,
                        new AccessModels.ChangeLoginLock(true,"无权锁定管理员",0)))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.errorCode()).isEqualTo(AccessErrorCode.LOGIN_LOCK_REQUIRES_ADMIN));
            }
            try (var context = TenantContext.open(tenantId, adminId)) {
                AuthModels.TokenPair beforeReset = authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE", "lifecycle-member", "Initial#Pass123", "reset-device"), "127.0.0.1");
                assertThatThrownBy(() -> accessService.resetUserPassword(adminId,
                        new AccessModels.ResetUserPassword("Temporary#Pass123", 0)))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.errorCode()).isEqualTo(AccessErrorCode.SELF_PASSWORD_RESET));
                assertThatThrownBy(() -> accessService.resetUserPassword(foreignTenantId + 1,
                        new AccessModels.ResetUserPassword("Temporary#Pass123", 0))).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> accessService.resetUserPassword(memberId,
                        new AccessModels.ResetUserPassword("weak-password", 4)))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.errorCode()).isEqualTo(IamErrorCode.PASSWORD_POLICY));
                assertThatThrownBy(() -> accessService.resetUserPassword(memberId,
                        new AccessModels.ResetUserPassword("Initial#Pass123", 4)))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.errorCode()).isEqualTo(IamErrorCode.PASSWORD_POLICY));
                assertThatThrownBy(() -> accessService.resetUserPassword(memberId,
                        new AccessModels.ResetUserPassword("Temporary#Pass123", 3))).isInstanceOf(ApiException.class);
                var reset = accessService.resetUserPassword(memberId,
                        new AccessModels.ResetUserPassword("Temporary#Pass123", 4));
                assertThat(reset.version()).isEqualTo(5);
                assertThat(reset.passwordChangeRequired()).isTrue();
                assertThatThrownBy(() -> authService.requireActiveUser(memberId, 4)).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> authService.refresh(new AuthModels.RefreshCommand(
                        beforeReset.refreshToken(), "reset-device"), "127.0.0.1")).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE", "lifecycle-member", "Initial#Pass123", "reset-device"), "127.0.0.1"))
                        .isInstanceOf(ApiException.class);
                assertThat(authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE", "lifecycle-member", "Temporary#Pass123", "reset-device"), "127.0.0.1")
                        .user().passwordChangeRequired()).isTrue();
                authService.changePassword(memberId,
                        new AuthModels.ChangePasswordCommand("Temporary#Pass123", "Changed#Pass456"));
                assertThat(authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE", "lifecycle-member", "Changed#Pass456", "reset-device"), "127.0.0.1")
                        .user().passwordChangeRequired()).isFalse();
                AuthModels.TokenPair beforeLock = authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE", "lifecycle-member", "Changed#Pass456", "lock-device"), "127.0.0.1");
                assertThatThrownBy(() -> accessService.changeLoginLock(adminId,
                        new AccessModels.ChangeLoginLock(true,"测试自锁",0)))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.errorCode()).isEqualTo(AccessErrorCode.SELF_LOGIN_LOCK));
                assertThatThrownBy(() -> accessService.changeLoginLock(foreignTenantId+1,
                        new AccessModels.ChangeLoginLock(true,"跨租户",0))).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> accessService.changeLoginLock(memberId,
                        new AccessModels.ChangeLoginLock(true,"陈旧版本",4))).isInstanceOf(ApiException.class);
                var locked = accessService.changeLoginLock(memberId,
                        new AccessModels.ChangeLoginLock(true,"安全调查",5));
                assertThat(locked.manualLocked()).isTrue();
                assertThat(locked.manualLockReason()).isEqualTo("安全调查");
                assertThat(locked.status()).isEqualTo("ACTIVE");
                assertThatThrownBy(() -> authService.requireActiveUser(memberId,6)).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> authService.refresh(new AuthModels.RefreshCommand(
                        beforeLock.refreshToken(),"lock-device"),"127.0.0.1")).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE","lifecycle-member","Changed#Pass456","lock-device"),"127.0.0.1"))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.errorCode()).isEqualTo(IamErrorCode.ACCOUNT_LOCKED));
                assertThatThrownBy(() -> accessService.changeLoginLock(memberId,
                        new AccessModels.ChangeLoginLock(true,"重复锁定",6))).isInstanceOf(ApiException.class);
                var unlocked = accessService.changeLoginLock(memberId,
                        new AccessModels.ChangeLoginLock(false,"调查结束",6));
                assertThat(unlocked.manualLocked()).isFalse();
                assertThat(unlocked.manualLockReason()).isNull();
                assertThatThrownBy(() -> authService.refresh(new AuthModels.RefreshCommand(
                        beforeLock.refreshToken(),"lock-device"),"127.0.0.1")).isInstanceOf(ApiException.class);
                jdbcTemplate.update("UPDATE iam_user SET locked_until=?,failed_count=5 WHERE tenant_id=? AND id=?",
                        LocalDateTime.now().plusMinutes(15),tenantId,memberId);
                assertThat(accessService.users()).filteredOn(user -> user.id().equals(Long.toString(memberId)))
                        .singleElement().satisfies(user -> assertThat(user.automaticLockedUntil()).isNotNull());
                assertThatThrownBy(() -> authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE","lifecycle-member","Changed#Pass456","lock-device"),"127.0.0.1"))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.errorCode()).isEqualTo(IamErrorCode.ACCOUNT_LOCKED));
                var autoUnlocked = accessService.changeLoginLock(memberId,
                        new AccessModels.ChangeLoginLock(false,"核实本人身份",7));
                assertThat(autoUnlocked.automaticLockedUntil()).isNull();
                assertThat(authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE","lifecycle-member","Changed#Pass456","lock-device"),"127.0.0.1")
                        .accessToken()).isNotBlank();
                assertThat(accessService.changeUserStatus(memberId,
                        new AccessModels.ChangeUserStatus("DISABLED",8)).status()).isEqualTo("DISABLED");
                assertThat(accessService.changeLoginLock(memberId,
                        new AccessModels.ChangeLoginLock(true,"停用期间保留锁定",9)).manualLocked()).isTrue();
                assertThat(accessService.changeLoginLock(memberId,
                        new AccessModels.ChangeLoginLock(false,"解除独立锁定",10)).status()).isEqualTo("DISABLED");
                assertThatThrownBy(() -> authService.login(new AuthModels.LoginCommand(
                        "ACCESS-LIFECYCLE","lifecycle-member","Changed#Pass456","lock-device"),"127.0.0.1"))
                        .isInstanceOf(ApiException.class);
            }
        } finally {
            jdbcTemplate.update("DELETE FROM iam_refresh_token WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_user_role WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_role WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_user WHERE tenant_id IN (?,?)", tenantId, foreignTenantId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id IN (?,?)", tenantId, foreignTenantId);
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
            assertThat(first.user().passwordChangeRecommended()).isFalse();
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
            assertThat(afterChange.user().passwordChangeRecommended()).isFalse();
            jdbcTemplate.update("UPDATE iam_user SET last_login_at=? WHERE id=?",
                    LocalDateTime.now().minusDays(91), userId);
            AuthModels.TokenPair inactive = authService.login(
                    new AuthModels.LoginCommand("T-A", "admin", "Changed#Pass456", "device-a"), "127.0.0.1");
            assertThat(inactive.user().passwordChangeRecommended()).isTrue();
            assertThat(inactive.user().passwordChangeRequired()).isFalse();
            assertThat(authService.login(new AuthModels.LoginCommand(
                    "T-A", "admin", "Changed#Pass456", "device-a"), "127.0.0.1")
                    .user().passwordChangeRecommended()).isFalse();
            jdbcTemplate.update("INSERT INTO cfg_tenant_setting(id,tenant_id,setting_key,value_type,setting_value) "
                    + "VALUES(?,?,?,?,?)", 1098L, tenantId, "security.inactivePasswordDays", "INTEGER", "0");
            jdbcTemplate.update("UPDATE iam_user SET last_login_at=? WHERE id=?",
                    LocalDateTime.now().minusDays(91), userId);
            assertThat(authService.login(new AuthModels.LoginCommand(
                    "T-A", "admin", "Changed#Pass456", "device-a"), "127.0.0.1")
                    .user().passwordChangeRecommended()).isFalse();
        } finally {
            jdbcTemplate.update("DELETE FROM cfg_tenant_setting WHERE tenant_id=?", tenantId);
            jdbcTemplate.update("DELETE FROM iam_refresh_token WHERE user_id=?", userId);
            jdbcTemplate.update("DELETE FROM iam_user WHERE id=?", userId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=?", tenantId);
        }
    }

    @Test
    void shouldReadOnlyAuthenticatedTenantsEffectiveBrandingWithoutSettingPermission() throws Exception {
        long tenantA = 97001L, tenantB = 97002L, userA = 97003L, userB = 97004L;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantA, "BRAND-A", "品牌租户 A", "ACTIVE");
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",
                tenantB, "BRAND-B", "品牌租户 B", "ACTIVE");
        jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) "
                + "VALUES(?,?,?,?,?,?)", userA, tenantA, "brand-user", "品牌用户 A",
                passwordEncoder.encode("Brand#Pass123"), "ACTIVE");
        jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) "
                + "VALUES(?,?,?,?,?,?)", userB, tenantB, "brand-user", "品牌用户 B",
                passwordEncoder.encode("Brand#Pass123"), "ACTIVE");
        try {
            try (TenantContext.Scope ignored = TenantContext.open(tenantA, userA)) {
                assertThatThrownBy(() -> configurationService.updateSetting("branding.systemName",
                        new ConfigurationModels.UpdateSetting(" ", 0))).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> configurationService.updateSetting("branding.logoUrl",
                        new ConfigurationModels.UpdateSetting("//untrusted.example/logo.png", 0))).isInstanceOf(ApiException.class);
                configurationService.updateSetting("branding.systemName",
                        new ConfigurationModels.UpdateSetting("化工集团 A", 0));
                configurationService.updateSetting("branding.logoUrl",
                        new ConfigurationModels.UpdateSetting("/assets/tenant-a-logo.png", 0));
                configurationService.updateSetting("branding.footerText",
                        new ConfigurationModels.UpdateSetting("仅 A 租户页脚", 0));
            }
            var a = authService.login(new AuthModels.LoginCommand(
                    "BRAND-A", "brand-user", "Brand#Pass123", "brand-device-a"), "127.0.0.1");
            var b = authService.login(new AuthModels.LoginCommand(
                    "BRAND-B", "brand-user", "Brand#Pass123", "brand-device-b"), "127.0.0.1");
            mockMvc.perform(get("/api/configuration/branding"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/configuration/settings")
                            .header("Authorization", "Bearer " + a.accessToken()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/configuration/branding")
                            .header("Authorization", "Bearer " + a.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.systemName").value("化工集团 A"))
                    .andExpect(jsonPath("$.data.logoUrl").value("/assets/tenant-a-logo.png"))
                    .andExpect(jsonPath("$.data.footerText").value("仅 A 租户页脚"))
                    .andExpect(jsonPath("$.data.faviconUrl").value(""))
                    .andExpect(jsonPath("$.data").value(aMapWithSize(4)));
            mockMvc.perform(get("/api/configuration/branding")
                            .header("Authorization", "Bearer " + b.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.systemName").value("工业供应商管理系统"))
                    .andExpect(jsonPath("$.data.footerText").value(""));
        } finally {
            jdbcTemplate.update("DELETE FROM cfg_tenant_setting WHERE tenant_id IN (?,?)", tenantA, tenantB);
            jdbcTemplate.update("DELETE FROM iam_refresh_token WHERE user_id IN (?,?)", userA, userB);
            jdbcTemplate.update("DELETE FROM iam_user WHERE id IN (?,?)", userA, userB);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id IN (?,?)", tenantA, tenantB);
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
        long tenantId=125L,userId=126L,otherUserId=128L,organizationId=127L;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,?)",tenantId,"SEARCH_IT","Search Tenant","ACTIVE");
        jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(?,?,?,?,?,?)",userId,tenantId,"search.user","Searchable User",passwordEncoder.encode("Search#123456"),"ACTIVE");
        jdbcTemplate.update("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status) VALUES(?,?,?,?,?,?)",otherUserId,tenantId,"search.other","Other User",passwordEncoder.encode("Search#123456"),"ACTIVE");
        jdbcTemplate.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,?,?,?,?)",organizationId,tenantId,"SEARCH_SITE","Searchable Site","SITE","ACTIVE");
        var permissions=new PermissionSnapshot(Set.of("iam:user:view","iam:organization:view"),Map.of("iam:user",DataScope.all(),"iam:organization",DataScope.all()),Set.of());
        try(var tenant=TenantContext.open(tenantId,userId);var authorization=AuthorizationContext.open(permissions)){
            var query=new SearchModels.SearchRequest("Searchable",Set.of(SearchModels.EntityType.USER,SearchModels.EntityType.ORGANIZATION,SearchModels.EntityType.FILE),Set.of("ACTIVE"),null,null,0,20);
            var result=searchService.search(query);
            assertThat(result.searchedTypes()).containsExactlyInAnyOrder(SearchModels.EntityType.USER,SearchModels.EntityType.ORGANIZATION);
            assertThat(result.items()).extracting(SearchModels.SearchItem::title).containsExactlyInAnyOrder("Searchable User","Searchable Site");
            var saved=searchService.create(new SearchModels.SaveSearch("常用搜索",query,true,0));
            assertThat(saved.defaultSearch()).isTrue();assertThat(searchService.saved()).extracting(SearchModels.SavedView::name).containsExactly("常用搜索");
            var second=searchService.create(new SearchModels.SaveSearch("合同搜索",query,true,0));
            assertThat(searchService.saved()).filteredOn(SearchModels.SavedView::defaultSearch).extracting(SearchModels.SavedView::id).containsExactly(second.id());
            var refreshed=searchService.saved().stream().filter(s->s.id().equals(saved.id())).findFirst().orElseThrow();
            assertThat(refreshed.version()).isGreaterThan(saved.version());
            assertThatThrownBy(()->searchService.update(Long.parseLong(saved.id()),new SearchModels.SaveSearch("过期更新",query,true,saved.version()))).isInstanceOf(ApiException.class);
            assertThat(searchService.saved()).filteredOn(SearchModels.SavedView::defaultSearch).extracting(SearchModels.SavedView::id).containsExactly(second.id());
            try(var other=TenantContext.open(tenantId,otherUserId)){
                assertThat(searchService.saved()).isEmpty();
                assertThatThrownBy(()->searchService.update(Long.parseLong(second.id()),new SearchModels.SaveSearch("越权更新",query,true,second.version()))).isInstanceOf(ApiException.class);
                assertThatThrownBy(()->searchService.delete(Long.parseLong(second.id()),second.version())).isInstanceOf(ApiException.class);
            }
            var updated=searchService.update(Long.parseLong(refreshed.id()),new SearchModels.SaveSearch("常用搜索-重命名",query,true,refreshed.version()));
            assertThat(updated.name()).isEqualTo("常用搜索-重命名");
            assertThat(searchService.saved()).filteredOn(SearchModels.SavedView::defaultSearch).extracting(SearchModels.SavedView::id).containsExactly(updated.id());
            assertThatThrownBy(()->searchService.delete(Long.parseLong(updated.id()),refreshed.version())).isInstanceOf(ApiException.class);
            searchService.delete(Long.parseLong(updated.id()),updated.version());
            searchService.delete(Long.parseLong(second.id()),searchService.saved().stream().filter(s->s.id().equals(second.id())).findFirst().orElseThrow().version());
            assertThat(searchService.saved()).isEmpty();
        }finally{
            jdbcTemplate.update("DELETE FROM src_saved_search WHERE tenant_id=?",tenantId);
            jdbcTemplate.update("DELETE FROM iam_organization WHERE id=?",organizationId);
            jdbcTemplate.update("DELETE FROM iam_user WHERE id IN (?,?)",userId,otherUserId);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id=?",tenantId);
        }
    }

    @Test
    void shouldSearchBusinessRecordsWithTenantPermissionAndObjectScope(){
        long tenantA=98101,tenantB=98102,orgA=98111,orgOther=98112,orgB=98113;
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,'Search Business A','ACTIVE')",tenantA,"SEARCH_BIZ_A");
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,'Search Business B','ACTIVE')",tenantB,"SEARCH_BIZ_B");
        try{
            for(long[] row:new long[][]{{orgA,tenantA},{orgOther,tenantA},{orgB,tenantB}})
                jdbcTemplate.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,?,?,'SITE','ACTIVE')",row[0],row[1],"SEARCH_"+row[0],"Search Site "+row[0]);
            for(long tenantId:new long[]{tenantA,tenantB})
                jdbcTemplate.update("INSERT INTO qua_qualification_type(id,tenant_id,type_code,type_name,category,created_by,updated_by) VALUES(?,?,?,'Safety License','LEGAL',9,9)",tenantId+1000,tenantId,"SEARCH_CERT_"+tenantId);
            for(long[] row:new long[][]{{98121,tenantA,orgA},{98122,tenantA,orgOther},{98123,tenantB,orgB}}){
                jdbcTemplate.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(?,?,?,?,?,'MANUFACTURER',9,9)",row[0],row[1],row[2],"BIZ-S"+row[0],"Business Supplier "+row[0]);
                jdbcTemplate.update("INSERT INTO prj_contract(id,tenant_id,organization_id,supplier_id,contract_no,contract_name,contract_type,amount,start_date,end_date,owner_id,file_id,created_by,updated_by) VALUES(?,?,?,?,?,?,'SERVICE',10,CURRENT_DATE,CURRENT_DATE,9,1,9,9)",row[0]+10,row[1],row[2],row[0],"BIZ-C"+row[0],"Business Contract "+row[0]);
                jdbcTemplate.update("INSERT INTO prj_project(id,tenant_id,organization_id,supplier_id,project_code,project_name,project_type,planned_start_date,planned_end_date,manager_id,created_by,updated_by) VALUES(?,?,?,?,?,?,'MAINTENANCE',CURRENT_DATE,CURRENT_DATE,9,9,9)",row[0]+20,row[1],row[2],row[0],"BIZ-P"+row[0],"Business Project "+row[0]);
                jdbcTemplate.update("INSERT INTO res_supplier_person(id,tenant_id,organization_id,supplier_id,project_id,person_code,person_name,id_type,id_number_hash,id_number_masked,mobile,created_by,updated_by) VALUES(?,?,?,?,?,?,'Business Person','NATIONAL_ID',?,'********1234','13800000000',9,9)",row[0]+30,row[1],row[2],row[0],row[0]+20,"BIZ-PER"+row[0],String.format("%064d",row[0]));
                jdbcTemplate.update("INSERT INTO res_supplier_asset(id,tenant_id,organization_id,supplier_id,project_id,asset_code,asset_name,asset_type,plate_no,created_by,updated_by) VALUES(?,?,?,?,?,?,'Business Asset','VEHICLE',?,9,9)",row[0]+40,row[1],row[2],row[0],row[0]+20,"BIZ-A"+row[0],"BIZ-PLATE"+row[0]);
                jdbcTemplate.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(?,?,9,'search-evidence.pdf','application/pdf',1,?,'LOCAL',?,'ACTIVE')",row[0]+70,row[1],String.format("%064d",row[0]+70),"search/"+row[0]);
                jdbcTemplate.update("INSERT INTO saf_issue(id,tenant_id,organization_id,project_id,supplier_id,issue_no,title,category,severity,description,discovered_at,deadline,responsible_user_id,created_by,updated_by) VALUES(?,?,?,?,?,?,'Business Safety','GENERAL','LOW','Fixture',CURRENT_TIMESTAMP,CURRENT_DATE,9,9,9)",row[0]+50,row[1],row[2],row[0]+20,row[0],"BIZ-SAFE"+row[0]);
                jdbcTemplate.update("INSERT INTO qua_nonconformance(id,tenant_id,organization_id,project_id,supplier_id,ncr_no,title,category,severity,description,inspection_date,inspected_quantity,defective_quantity,unit,evidence_file_id,deadline,responsible_user_id,created_by,updated_by) VALUES(?,?,?,?,?,?,'Business Quality','PRODUCT','LOW','Fixture',CURRENT_DATE,10,1,'件',?,CURRENT_DATE,9,9,9)",row[0]+60,row[1],row[2],row[0]+20,row[0],"BIZ-NCR"+row[0],row[0]+70);
                jdbcTemplate.update("INSERT INTO per_supplier_evaluation(id,tenant_id,organization_id,supplier_id,supplier_code,supplier_name,period_start,period_end,total_score,grade,created_by,updated_by) VALUES(?,?,?,?,?,?,'2026-01-01','2026-01-31',80,'B',9,9)",row[0]+80,row[1],row[2],row[0],"BIZ-S"+row[0],"Business Supplier "+row[0]);
                jdbcTemplate.update("INSERT INTO saf_site_attendance(id,tenant_id,organization_id,project_id,supplier_id,person_id,site_name,check_in_at,check_in_by,created_by) VALUES(?,?,?,?,?,?,'BIZ-SITE',CURRENT_TIMESTAMP,9,9)",row[0]+90,row[1],row[2],row[0]+20,row[0],row[0]+30);
                jdbcTemplate.update("INSERT INTO qua_supplier_qualification(id,tenant_id,organization_id,supplier_id,qualification_type_id,certificate_no,expiry_date,file_id,status,created_by,updated_by) VALUES(?,?,?,?,?,? ,DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),?,'VALID',9,9)",row[0]+100,row[1],row[2],row[0],row[1]+1000,"BIZ-CERT"+row[0],row[0]+70);
                jdbcTemplate.update("INSERT INTO qua_admission_application(id,tenant_id,organization_id,supplier_id,application_no,purchase_category,admission_reason,status,created_by,updated_by) VALUES(?,?,?,?,?,'Equipment','Search fixture','SUBMITTED',9,9)",row[0]+110,row[1],row[2],row[0],"BIZ-ADM"+row[0]);
            }
            var admissionRequest=new SearchModels.SearchRequest("BIZ-ADM",Set.of(SearchModels.EntityType.SUPPLIER_ADMISSION),Set.of("SUBMITTED"),null,null,0,20);
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:admission:view"),Map.of("supplier:admission",DataScope.organizations(Set.of(orgA))),Set.of()))){
                assertThat(searchService.search(admissionRequest).items()).extracting(SearchModels.SearchItem::id).containsExactly("98231");
            }
            try(var tenant=TenantContext.open(tenantB,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:admission:view"),Map.of("supplier:admission",DataScope.all()),Set.of()))){
                assertThat(searchService.search(admissionRequest).items()).extracting(SearchModels.SearchItem::id).containsExactly("98233");
            }
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:admission",DataScope.all()),Set.of()))){
                assertThat(searchService.search(admissionRequest).items()).isEmpty();
            }
            var certificateRequest=new SearchModels.SearchRequest("BIZ-CERT",Set.of(SearchModels.EntityType.SUPPLIER_QUALIFICATION),Set.of("EXPIRED"),null,null,0,20);
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:qualification:view"),Map.of("supplier:qualification",DataScope.organizations(Set.of(orgA))),Set.of()))){
                assertThat(searchService.search(certificateRequest).items()).extracting(SearchModels.SearchItem::id).containsExactly("98221");
            }
            try(var tenant=TenantContext.open(tenantB,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:qualification:view"),Map.of("supplier:qualification",DataScope.all()),Set.of()))){
                assertThat(searchService.search(certificateRequest).items()).extracting(SearchModels.SearchItem::id).containsExactly("98223");
            }
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:qualification",DataScope.all()),Set.of()))){
                assertThat(searchService.search(certificateRequest).items()).isEmpty();
            }
            var attendanceRequest=new SearchModels.SearchRequest("BIZ-SITE",Set.of(SearchModels.EntityType.SITE_ATTENDANCE),Set.of("OPEN"),null,null,0,20);
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("safety:attendance:view"),Map.of("safety:attendance",DataScope.projects(Set.of(98141L))),Set.of()))){
                assertThat(searchService.search(attendanceRequest).items()).extracting(SearchModels.SearchItem::id).containsExactly("98211");
                assertThat(searchService.search(attendanceRequest).items()).extracting(SearchModels.SearchItem::route).containsExactly("/safety/attendance");
                assertThat(safetyAttendanceService.get(98211).personId()).isEqualTo("98151");
                assertThatThrownBy(()->safetyAttendanceService.get(98212)).isInstanceOf(ApiException.class);
            }
            try(var tenant=TenantContext.open(tenantB,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("safety:attendance:view"),Map.of("safety:attendance",DataScope.all()),Set.of()))){
                assertThat(searchService.search(attendanceRequest).items()).extracting(SearchModels.SearchItem::id).containsExactly("98213");
            }
            var performanceRequest=new SearchModels.SearchRequest("BIZ-S",Set.of(SearchModels.EntityType.PERFORMANCE_EVALUATION),Set.of(),null,null,0,20);
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("performance:evaluation:view"),Map.of("performance:evaluation",DataScope.organizations(Set.of(orgA))),Set.of()))){
                assertThat(searchService.search(performanceRequest).items()).extracting(SearchModels.SearchItem::id).containsExactly("98201");
                assertThat(searchService.search(performanceRequest).items()).extracting(SearchModels.SearchItem::route).containsExactly("/performance/evaluations");
            }
            try(var tenant=TenantContext.open(tenantB,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("performance:evaluation:view"),Map.of("performance:evaluation",DataScope.all()),Set.of()))){
                assertThat(searchService.search(performanceRequest).items()).extracting(SearchModels.SearchItem::id).containsExactly("98203");
            }
            var issueTypes=Set.of(SearchModels.EntityType.SAFETY_ISSUE,SearchModels.EntityType.QUALITY_NCR);
            var issueRequest=new SearchModels.SearchRequest("BIZ-",issueTypes,Set.of(),null,null,0,20);
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("safety:issue:view","quality:ncr:view"),Map.of("safety:issue",DataScope.projects(Set.of(98141L)),"quality:ncr",DataScope.organizations(Set.of(orgA))),Set.of()))){
                assertThat(searchService.search(issueRequest).items()).extracting(SearchModels.SearchItem::id).containsExactlyInAnyOrder("98171","98181");
                assertThat(searchService.search(issueRequest).items()).extracting(SearchModels.SearchItem::route).contains("/safety/issues","/quality/nonconformances");
            }
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("quality:ncr:view"),Map.of("quality:ncr",DataScope.owned()),Set.of()))){
                assertThat(searchService.search(issueRequest).items()).extracting(SearchModels.SearchItem::id).containsExactlyInAnyOrder("98181","98182");
            }
            try(var tenant=TenantContext.open(tenantB,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("safety:issue:view","quality:ncr:view"),Map.of("safety:issue",DataScope.all(),"quality:ncr",DataScope.all()),Set.of()))){
                assertThat(searchService.search(issueRequest).items()).extracting(SearchModels.SearchItem::id).containsExactlyInAnyOrder("98173","98183");
            }
            var types=Set.of(SearchModels.EntityType.SUPPLIER,SearchModels.EntityType.CONTRACT,SearchModels.EntityType.PROJECT,SearchModels.EntityType.PERSON,SearchModels.EntityType.ASSET);
            var request=new SearchModels.SearchRequest("BIZ-",types,Set.of(),null,null,0,20);
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view","contract:view","project:view","resource:person:view","resource:asset:view"),Map.of("supplier:master",DataScope.all(),"contract",DataScope.all(),"project",DataScope.all(),"resource:person",DataScope.all(),"resource:asset",DataScope.all()),Set.of()))){
                assertThat(searchService.search(request).items()).hasSize(10).noneMatch(item->item.id().startsWith("98123"));
                assertThat(searchService.search(request).items()).extracting(SearchModels.SearchItem::route).contains("/suppliers/master","/projects/contracts","/projects/ledger","/resources/persons","/resources/assets");
                assertThat(searchService.search(new SearchModels.SearchRequest("13800000000",Set.of(SearchModels.EntityType.PERSON),Set.of(),null,null,0,20)).items()).isEmpty();
                assertThat(searchService.search(new SearchModels.SearchRequest("BIZ-PLATE98121",Set.of(SearchModels.EntityType.ASSET),Set.of(),null,null,0,20)).items()).extracting(SearchModels.SearchItem::id).containsExactly("98161");
                assertThat(searchService.search(request).items()).filteredOn(item->item.type()==SearchModels.EntityType.PERSON).allSatisfy(item->{assertThat(item.subtitle()).doesNotContain("13800000000","1234");});
            }
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view","contract:view","project:view","resource:person:view","resource:asset:view"),Map.of("supplier:master",DataScope.organizations(Set.of(orgA)),"contract",DataScope.organizations(Set.of(orgA)),"project",DataScope.projects(Set.of(98141L)),"resource:person",DataScope.projects(Set.of(98141L)),"resource:asset",DataScope.organizations(Set.of(orgA))),Set.of()))){
                assertThat(searchService.search(request).items()).extracting(SearchModels.SearchItem::id).containsExactlyInAnyOrder("98121","98131","98141","98151","98161");
            }
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view"),Map.of("supplier:master",DataScope.created()),Set.of()))){
                var result=searchService.search(request);
                assertThat(result.searchedTypes()).containsExactly(SearchModels.EntityType.SUPPLIER);
                assertThat(result.items()).extracting(SearchModels.SearchItem::id).containsExactlyInAnyOrder("98121","98122");
            }
            try(var tenant=TenantContext.open(tenantA,10);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("resource:person:view","resource:asset:view"),Map.of("resource:person",DataScope.created(),"resource:asset",DataScope.created()),Set.of()))){
                assertThat(searchService.search(request).items()).isEmpty();
            }
            try(var tenant=TenantContext.open(tenantA,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("resource:person:view","resource:asset:view"),Map.of("resource:person",DataScope.created(),"resource:asset",DataScope.created()),Set.of()))){
                assertThat(searchService.search(request).items()).extracting(SearchModels.SearchItem::id).containsExactlyInAnyOrder("98151","98152","98161","98162");
            }
            try(var tenant=TenantContext.open(tenantB,9);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view","contract:view","project:view","resource:person:view","resource:asset:view"),Map.of("supplier:master",DataScope.all(),"contract",DataScope.all(),"project",DataScope.all(),"resource:person",DataScope.all(),"resource:asset",DataScope.all()),Set.of()))){
                assertThat(searchService.search(request).items()).extracting(SearchModels.SearchItem::id).containsExactlyInAnyOrder("98123","98133","98143","98153","98163");
            }
        }finally{
            jdbcTemplate.update("DELETE FROM qua_admission_application WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM qua_supplier_qualification WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM qua_qualification_type WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM saf_site_attendance WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM per_supplier_evaluation WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM qua_nonconformance WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM saf_issue WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM res_file_object WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM res_supplier_person WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM res_supplier_asset WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM prj_project WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM prj_contract WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM sup_supplier WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM iam_organization WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id IN (?,?)",tenantA,tenantB);
        }
    }

    @Test
    void shouldPersistPrivateTableViewsWithDefaultVersionAndConcurrencyGuards() throws Exception {
        long tenantA=87001,tenantB=87002,owner=87011,otherOwner=87012;
        String key="supplier.master";
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,'ACTIVE')",tenantA,"TABLE_VIEW_A","列方案租户 A");
        jdbcTemplate.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,'ACTIVE')",tenantB,"TABLE_VIEW_B","列方案租户 B");
        var permission=new PermissionSnapshot(Set.of("supplier:master:view","supplier:admission:view","supplier:qualification:view","contract:view","project:view","resource:person:view","resource:asset:view","table:view:manage"),Map.of(),Set.of());
        var publisherPermission=new PermissionSnapshot(Set.of("supplier:master:view","table:view:manage","table:view:publish"),Map.of(),Set.of());
        var columns=List.of("code","name","type","riskLevel","status","updatedAt").stream().map(k->new io.github.turbopro.ism.integration.table.TableViewModels.Column(k,!k.equals("type"),160)).toList();
        String firstId,secondId;
        try {
            try(var tenant=TenantContext.open(tenantA,owner);var authorization=AuthorizationContext.open(permission)){
                var first=tableViewService.create(key,new io.github.turbopro.ism.integration.table.TableViewModels.Save("默认一",columns,true,0));firstId=first.id();
                var second=tableViewService.create(key,new io.github.turbopro.ism.integration.table.TableViewModels.Save("默认二",columns,true,0));secondId=second.id();
                assertThat(tableViewService.list(key).views()).filteredOn(io.github.turbopro.ism.integration.table.TableViewModels.View::defaultView).extracting(io.github.turbopro.ism.integration.table.TableViewModels.View::id).containsExactly(secondId);
                assertThatThrownBy(()->tableViewService.update(key,Long.parseLong(firstId),new io.github.turbopro.ism.integration.table.TableViewModels.Save("旧版本",columns,true,0))).isInstanceOf(ApiException.class);
                assertThatThrownBy(()->tableViewService.create(key,new io.github.turbopro.ism.integration.table.TableViewModels.Save("默认二",columns,true,0))).isInstanceOf(ApiException.class);
                assertThat(tableViewService.list(key).views()).filteredOn(io.github.turbopro.ism.integration.table.TableViewModels.View::defaultView).extracting(io.github.turbopro.ism.integration.table.TableViewModels.View::id).containsExactly(secondId);
                var updated=tableViewService.update(key,Long.parseLong(secondId),new io.github.turbopro.ism.integration.table.TableViewModels.Save("已更新",columns,true,0));
                assertThat(updated.version()).isOne();assertThat(updated.columns()).isEqualTo(columns);
                assertThatThrownBy(()->tableViewService.delete(key,Long.parseLong(secondId),0)).isInstanceOf(ApiException.class);
            }
            try(var tenant=TenantContext.open(tenantA,otherOwner);var authorization=AuthorizationContext.open(permission)){
                assertThat(tableViewService.list(key).views()).isEmpty();
                assertThatThrownBy(()->tableViewService.update(key,Long.parseLong(secondId),new io.github.turbopro.ism.integration.table.TableViewModels.Save("越权修改",columns,true,1))).isInstanceOf(ApiException.class);
                assertThatThrownBy(()->tableViewService.delete(key,Long.parseLong(secondId),1)).isInstanceOf(ApiException.class);
                tableViewService.create(key,new io.github.turbopro.ism.integration.table.TableViewModels.Save("已更新",columns,true,0));
            }
            try(var tenant=TenantContext.open(tenantB,owner);var authorization=AuthorizationContext.open(permission)){
                assertThat(tableViewService.list(key).views()).isEmpty();
                assertThatThrownBy(()->tableViewService.delete(key,Long.parseLong(secondId),1)).isInstanceOf(ApiException.class);
                tableViewService.create(key,new io.github.turbopro.ism.integration.table.TableViewModels.Save("已更新",columns,true,0));
            }
            ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
            try{
                var tasks=new java.util.ArrayList<Future<?>>();
                for(int n=0;n<2;n++){final int index=n;tasks.add(pool.submit(()->{
                    try(var tenant=TenantContext.open(tenantA,owner);var authorization=AuthorizationContext.open(permission)){
                        ready.countDown();start.await();tableViewService.create(key,new io.github.turbopro.ism.integration.table.TableViewModels.Save("并发默认"+index,columns,true,0));
                    }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                }));}
                assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
                for(var task:tasks)task.get(20,java.util.concurrent.TimeUnit.SECONDS);
            }finally{start.countDown();pool.shutdownNow();}
            try(var tenant=TenantContext.open(tenantA,owner);var authorization=AuthorizationContext.open(permission)){
                var saved=tableViewService.list(key).views();assertThat(saved).hasSize(4);
                assertThat(saved.stream().filter(io.github.turbopro.ism.integration.table.TableViewModels.View::defaultView).count()).isOne();
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='TABLE_VIEW_CREATE'",Long.class,tenantA)).isEqualTo(5L);
                for(var view:saved)tableViewService.delete(key,Long.parseLong(view.id()),view.version());
                assertThat(tableViewService.list(key).views()).isEmpty();
                var supplierView=tableViewService.create(key,new io.github.turbopro.ism.integration.table.TableViewModels.Save("共同方案",columns,true,0));
                var contractColumns=tableViewService.list("contract.ledger").catalog().stream().map(c->new io.github.turbopro.ism.integration.table.TableViewModels.Column(c.key(),true,c.width())).toList();
                var projectColumns=tableViewService.list("project.ledger").catalog().stream().map(c->new io.github.turbopro.ism.integration.table.TableViewModels.Column(c.key(),true,c.width())).toList();
                var contractView=tableViewService.create("contract.ledger",new io.github.turbopro.ism.integration.table.TableViewModels.Save("共同方案",contractColumns,true,0));
                var projectView=tableViewService.create("project.ledger",new io.github.turbopro.ism.integration.table.TableViewModels.Save("共同方案",projectColumns,true,0));
                assertThat(tableViewService.list(key).views()).extracting(io.github.turbopro.ism.integration.table.TableViewModels.View::id).containsExactly(supplierView.id());
                assertThat(tableViewService.list("contract.ledger").views()).extracting(io.github.turbopro.ism.integration.table.TableViewModels.View::id).containsExactly(contractView.id());
                assertThat(tableViewService.list("project.ledger").views()).extracting(io.github.turbopro.ism.integration.table.TableViewModels.View::id).containsExactly(projectView.id());
                assertThatThrownBy(()->tableViewService.delete("contract.ledger",Long.parseLong(supplierView.id()),0)).isInstanceOf(ApiException.class);
                assertThatThrownBy(()->tableViewService.create("contract.ledger",new io.github.turbopro.ism.integration.table.TableViewModels.Save("错用列",columns,true,0))).isInstanceOf(ApiException.class);
                tableViewService.update("contract.ledger",Long.parseLong(contractView.id()),new io.github.turbopro.ism.integration.table.TableViewModels.Save("共同方案",contractColumns,false,0));
                assertThat(tableViewService.list("contract.ledger").views().get(0).defaultView()).isFalse();
                assertThat(tableViewService.list(key).views().get(0).defaultView()).isTrue();
                assertThat(tableViewService.list("project.ledger").views().get(0).defaultView()).isTrue();
                for(String resourceKey:List.of("resource.person","resource.asset")){
                    var resourceColumns=tableViewService.list(resourceKey).catalog().stream().map(c->new io.github.turbopro.ism.integration.table.TableViewModels.Column(c.key(),true,c.width())).toList();
                    var resourceView=tableViewService.create(resourceKey,new io.github.turbopro.ism.integration.table.TableViewModels.Save("共同方案",resourceColumns,true,0));
                    assertThat(tableViewService.list(resourceKey).views()).extracting(io.github.turbopro.ism.integration.table.TableViewModels.View::id).containsExactly(resourceView.id());
                    assertThatThrownBy(()->tableViewService.delete(resourceKey,Long.parseLong(supplierView.id()),0)).isInstanceOf(ApiException.class);
                    tableViewService.update(resourceKey,Long.parseLong(resourceView.id()),new io.github.turbopro.ism.integration.table.TableViewModels.Save("共同方案",resourceColumns,false,0));
                    assertThat(tableViewService.list(resourceKey).views().get(0).defaultView()).isFalse();
                    assertThat(tableViewService.list("project.ledger").views().get(0).defaultView()).isTrue();
                    try(var other=TenantContext.open(tenantA,otherOwner)){
                        assertThat(tableViewService.list(resourceKey).views()).isEmpty();
                        assertThatThrownBy(()->tableViewService.delete(resourceKey,Long.parseLong(resourceView.id()),1)).isInstanceOf(ApiException.class);
                    }
                    try(var otherTenant=TenantContext.open(tenantB,owner)){
                        assertThat(tableViewService.list(resourceKey).views()).isEmpty();
                        assertThatThrownBy(()->tableViewService.delete(resourceKey,Long.parseLong(resourceView.id()),1)).isInstanceOf(ApiException.class);
                    }
                    tableViewService.delete(resourceKey,Long.parseLong(resourceView.id()),1);
                    assertThat(tableViewService.list(resourceKey).views()).isEmpty();
                }
                for(String supplierKey:List.of("supplier.admission","supplier.qualification")){
                    var supplierColumns=tableViewService.list(supplierKey).catalog().stream().map(c->new io.github.turbopro.ism.integration.table.TableViewModels.Column(c.key(),true,c.width())).toList();
                    var supplierTableView=tableViewService.create(supplierKey,new io.github.turbopro.ism.integration.table.TableViewModels.Save("常用供应商列",supplierColumns,true,0));
                    assertThat(tableViewService.list(supplierKey).views()).extracting(io.github.turbopro.ism.integration.table.TableViewModels.View::id).containsExactly(supplierTableView.id());
                    try(var other=TenantContext.open(tenantA,otherOwner)){
                        assertThat(tableViewService.list(supplierKey).views()).isEmpty();
                        assertThatThrownBy(()->tableViewService.delete(supplierKey,Long.parseLong(supplierTableView.id()),supplierTableView.version())).isInstanceOf(ApiException.class);
                    }
                    try(var otherTenant=TenantContext.open(tenantB,owner)){
                        assertThat(tableViewService.list(supplierKey).views()).isEmpty();
                    }
                    tableViewService.delete(supplierKey,Long.parseLong(supplierTableView.id()),supplierTableView.version());
                    assertThat(tableViewService.list(supplierKey).views()).isEmpty();
                }
            }
            String sharedId;
            try(var tenant=TenantContext.open(tenantA,owner);var authorization=AuthorizationContext.open(publisherPermission)){
                var shared=tableViewService.createShared(key,new io.github.turbopro.ism.integration.table.TableViewModels.Save("租户通用",columns,true,0));
                sharedId=shared.id();
                assertThat(tableViewService.list(key).canPublish()).isTrue();
                assertThat(tableViewService.list(key).sharedViews()).extracting(io.github.turbopro.ism.integration.table.TableViewModels.View::id).containsExactly(sharedId);
                assertThat(tableViewService.list(key).views()).filteredOn(io.github.turbopro.ism.integration.table.TableViewModels.View::defaultView).hasSize(1);
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND operator_id=? AND action='TABLE_VIEW_SHARED_CREATE'",Long.class,tenantA,owner)).isOne();
                assertThatThrownBy(()->tableViewService.createShared(key,new io.github.turbopro.ism.integration.table.TableViewModels.Save("租户通用",columns,false,0))).isInstanceOf(ApiException.class);
            }
            try(var tenant=TenantContext.open(tenantA,otherOwner);var authorization=AuthorizationContext.open(permission)){
                assertThat(tableViewService.list(key).sharedViews()).extracting(io.github.turbopro.ism.integration.table.TableViewModels.View::id).containsExactly(sharedId);
                assertThat(tableViewService.list(key).canPublish()).isFalse();
                assertThatThrownBy(()->tableViewService.updateShared(key,Long.parseLong(sharedId),new io.github.turbopro.ism.integration.table.TableViewModels.Save("越权修改",columns,false,0))).isInstanceOf(ApiException.class);
                assertThatThrownBy(()->tableViewService.delete(key,Long.parseLong(sharedId),0)).isInstanceOf(ApiException.class);
            }
            try(var tenant=TenantContext.open(tenantB,owner);var authorization=AuthorizationContext.open(publisherPermission)){
                assertThat(tableViewService.list(key).sharedViews()).isEmpty();
                assertThatThrownBy(()->tableViewService.updateShared(key,Long.parseLong(sharedId),new io.github.turbopro.ism.integration.table.TableViewModels.Save("跨租户修改",columns,false,0))).isInstanceOf(ApiException.class);
            }
            try(var tenant=TenantContext.open(tenantA,owner);var authorization=AuthorizationContext.open(publisherPermission)){
                var updated=tableViewService.updateShared(key,Long.parseLong(sharedId),new io.github.turbopro.ism.integration.table.TableViewModels.Save("租户通用",columns,false,0));
                assertThat(updated.version()).isOne();
                assertThatThrownBy(()->tableViewService.deleteShared(key,Long.parseLong(sharedId),0)).isInstanceOf(ApiException.class);
                tableViewService.deleteShared(key,Long.parseLong(sharedId),1);
                assertThat(tableViewService.list(key).sharedViews()).isEmpty();
            }
        }finally{
            jdbcTemplate.update("DELETE FROM sys_audit_event WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM ui_table_view WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM ui_table_view_owner WHERE tenant_id IN (?,?)",tenantA,tenantB);
            jdbcTemplate.update("DELETE FROM iam_tenant WHERE id IN (?,?)",tenantA,tenantB);
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

    private io.github.turbopro.ism.supplier.ExitTaskModels.Page exitTasks(long tenantId,long actorId,DataScope scope){
        try(var identity=TenantContext.open(tenantId,actorId);var authorization=AuthorizationContext.open(new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(Set.of(),Map.of("supplier:master",scope),Set.of()))){
            return exitTaskService.mine(null,null,0,20);
        }
    }
    private io.github.turbopro.ism.supplier.ExitTaskModels.AccessPage accessTasks(long tenantId,long actorId,DataScope scope){
        try(var identity=TenantContext.open(tenantId,actorId);var authorization=AuthorizationContext.open(new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(Set.of(),Map.of("supplier:master",scope),Set.of()))){
            return exitTaskService.accessMine(null,null,0,20);
        }
    }
    private io.github.turbopro.ism.supplier.ExitTaskModels.MonitorPage exitMonitor(long tenantId,long actorId,DataScope scope,boolean unassignedOnly,boolean overdueOnly){
        try(var identity=TenantContext.open(tenantId,actorId);var authorization=AuthorizationContext.open(new io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot(Set.of("supplier:exit:monitor"),Map.of("supplier:master",scope),Set.of()))){
            return exitTaskService.monitor(null,null,unassignedOnly,overdueOnly,0,20);
        }
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

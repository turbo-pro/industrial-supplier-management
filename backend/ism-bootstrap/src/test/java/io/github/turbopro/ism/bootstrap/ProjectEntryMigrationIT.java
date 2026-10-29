package io.github.turbopro.ism.bootstrap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
class ProjectEntryMigrationIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("project_entry_upgrade").withUsername("ism").withPassword("ism");
    @Test void upgradePreservesLegacyOwnershipAndGrantsOnlyBuiltInAdministrators(){
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var jdbc=new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("88").load().migrate();
        jdbc.update("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status) VALUES(89001,'ENTRY-OLD','旧企业','ACTIVE','READY')");jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(89001,'ENTRY-OLD','旧企业','ACTIVE')");jdbc.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(89002,89001,'HQ','原总部','HEADQUARTERS','ACTIVE')");
        jdbc.update("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(89003,89001,'TENANT_ADMIN','管理员',1,'ACTIVE'),(89004,89001,'CUSTOM','自定义',0,'ACTIVE')");
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(89005,89001,89002,'OLD-S','旧供应商','SERVICE_PROVIDER',1,1)");
        jdbc.update("INSERT INTO prj_project(id,tenant_id,organization_id,supplier_id,project_code,project_name,project_type,planned_start_date,planned_end_date,manager_id,created_by,updated_by) VALUES(89006,89001,89002,89005,'OLD-P','旧项目','MAINTENANCE','2026-01-01','2026-12-31',1,1,1)");var before=jdbc.queryForMap("SELECT * FROM prj_project WHERE id=89006");
        var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();flyway.migrate();flyway.validate();
        assertThat(jdbc.queryForMap("SELECT * FROM prj_project WHERE id=89006")).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT operation_mode FROM plt_tenant WHERE id=89001",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role_permission WHERE tenant_id=89001 AND role_id=89003 AND permission_id IN(6314,6315)",Integer.class)).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role_permission WHERE tenant_id=89001 AND role_id=89004 AND permission_id IN(6314,6315)",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='25'",Integer.class)).isEqualTo(1394665689);
    }
}

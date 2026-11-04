package io.github.turbopro.ism.bootstrap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
class TenantSupportMigrationIT {
 @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("tenant_support_upgrade").withUsername("ism").withPassword("ism");
 @Test void v90UpgradePreservesBusinessAndExistingPermissionsWithoutAutomaticConsent(){
  var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var jdbc=new JdbcTemplate(ds);Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("90").load().migrate();
  jdbc.update("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status) VALUES(90001,'SUPPORT-OLD','原企业','ACTIVE','READY')");jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(90001,'SUPPORT-OLD','原企业','ACTIVE')");jdbc.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(90002,90001,'HQ','原总部','HEADQUARTERS','ACTIVE')");jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(90003,90001,90002,'OLD-S','原供应商','MANUFACTURER',1,1)");var before=jdbc.queryForMap("SELECT * FROM sup_supplier WHERE id=90003");
  jdbc.update("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(90004,90001,'TENANT_ADMIN','租户管理员',1,'ACTIVE'),(90005,90001,'READER','普通角色',0,'ACTIVE')");
  var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();flyway.migrate();flyway.validate();assertThat(jdbc.queryForMap("SELECT * FROM sup_supplier WHERE id=90003")).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_role_permission WHERE role_id=1002 AND permission_id=1125",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_role_permission WHERE role_id=1001 AND permission_id=1125",Integer.class)).isOne();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_role_permission WHERE role_id=1002 AND permission_id=1124",Integer.class)).isOne();assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='25'",Integer.class)).isEqualTo(1394665689);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role_permission WHERE role_id=90004 AND permission_id IN (3080,3081,3082)",Integer.class)).isEqualTo(3);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role_permission WHERE role_id=90005 AND permission_id IN (3080,3081,3082)",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_tenant_support_policy",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_support_tenant_decision",Integer.class)).isZero();assertThat(flyway.migrate().migrationsExecuted).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_support_request",Integer.class)).isZero();
 }
}

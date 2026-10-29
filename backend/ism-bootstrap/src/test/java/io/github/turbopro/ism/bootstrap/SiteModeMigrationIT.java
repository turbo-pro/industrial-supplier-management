package io.github.turbopro.ism.bootstrap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
class SiteModeMigrationIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("site_upgrade").withUsername("ism").withPassword("ism");
    @Test void nullableMetadataDoesNotReclassifyLegacyStructure(){
        var source=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("85").load().migrate();
        jdbc.update("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status) VALUES(88001,'LEGACY_SITE','历史企业','ACTIVE','READY')");
        jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(88001,'LEGACY_SITE','历史企业','ACTIVE')");
        jdbc.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(88002,88001,'HEADQUARTERS','历史总部','HEADQUARTERS','ACTIVE')");
        jdbc.update("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(88003,88001,'TENANT_ADMIN','旧租户管理员',1,'ACTIVE')");
        jdbc.update("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(88004,88001,'CUSTOM_READER','旧只读角色',0,'ACTIVE')");
        var flyway=Flyway.configure().dataSource(source).locations("classpath:db/migration").load();flyway.migrate();flyway.validate();
        var row=jdbc.queryForMap("SELECT operation_mode,site_label,default_site_id FROM plt_tenant WHERE id=88001");assertThat(row.values()).containsOnlyNulls();
        assertThat(jdbc.queryForObject("SELECT organization_type FROM iam_organization WHERE id=88002",String.class)).isEqualTo("HEADQUARTERS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_role_permission rp JOIN plt_role r ON r.id=rp.role_id JOIN plt_permission p ON p.id=rp.permission_id WHERE r.role_code='PLATFORM_ADMIN' AND p.permission_code='platform:tenant:upgrade'",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_role_permission rp JOIN plt_role r ON r.id=rp.role_id JOIN plt_permission p ON p.id=rp.permission_id WHERE r.role_code='PLATFORM_SUPPORT' AND p.permission_code='platform:tenant:upgrade'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user_site_preference",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role_permission WHERE tenant_id=88001 AND role_id=88003 AND permission_id IN(3070,3071)",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role_permission WHERE tenant_id=88001 AND role_id=88004 AND permission_id IN(3070,3071)",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='25'",Integer.class)).isEqualTo(1394665689);
    }
}

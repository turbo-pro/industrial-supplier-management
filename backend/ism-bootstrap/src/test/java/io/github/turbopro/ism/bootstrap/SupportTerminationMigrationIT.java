package io.github.turbopro.ism.bootstrap;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SupportTerminationMigrationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("support_termination_upgrade").withUsername("ism").withPassword("ism");

    @Test void upgradeGrantsOnlyPlatformAdminAndPreservesHistory() {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("97").load().migrate();
        jdbc.update("""
                INSERT INTO sys_audit_event (id,tenant_id,operator_id,action,object_type,object_id,after_summary,trace_id,occurred_at)
                VALUES (901,0,10,'SUPPORT_SUPPLIER_READ','SUPPLIER',70,JSON_OBJECT('sessionId','80','targetTenantId','90'),'trace-1',UTC_TIMESTAMP(3)),
                       (902,0,10,'SUPPORT_SUPPLIER_READ_DENIED','SUPPLIER',70,JSON_OBJECT('requestedSessionId','80'),'trace-2',UTC_TIMESTAMP(3)),
                       (903,90,10,'SUPPORT_SUPPLIER_READ','SUPPLIER',70,JSON_OBJECT('sessionId','80'),'trace-3',UTC_TIMESTAMP(3))
                """);
        String columns = "SELECT id,tenant_id,action,after_summary,trace_id FROM sys_audit_event WHERE id BETWEEN 901 AND 903 ORDER BY id";
        var old = jdbc.queryForList(columns);
        var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("98").load();
        assertThat(flyway.migrate().migrationsExecuted).isOne();
        flyway.validate();
        assertThat(jdbc.queryForList(columns)).isEqualTo(old);
        assertThat(jdbc.queryForList("SELECT role_id FROM plt_role_permission WHERE permission_id=1128", Long.class)).containsExactly(1001L);
        assertThat(jdbc.queryForObject("SELECT permission_code FROM plt_permission WHERE id=1128", String.class)).isEqualTo("platform:support:terminate");
        assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='25'", Integer.class)).isEqualTo(1394665689);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}

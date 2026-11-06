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
class SupportAccessLogMigrationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("support_access_upgrade").withUsername("ism").withPassword("ism");

    @Test void upgradeIndexesOnlyAttributedEventsWithoutChangingHistory() {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("96").load().migrate();
        jdbc.update("""
                INSERT INTO sys_audit_event (id,tenant_id,operator_id,action,object_type,object_id,after_summary,trace_id,occurred_at)
                VALUES (901,0,10,'SUPPORT_SUPPLIER_READ','SUPPLIER',70,JSON_OBJECT('sessionId','80','targetTenantId','90'),'trace-1',UTC_TIMESTAMP(3)),
                       (902,0,10,'SUPPORT_SUPPLIER_READ_DENIED','SUPPLIER',70,JSON_OBJECT('requestedSessionId','80'),'trace-2',UTC_TIMESTAMP(3)),
                       (903,90,10,'SUPPORT_SUPPLIER_READ','SUPPLIER',70,JSON_OBJECT('sessionId','80'),'trace-3',UTC_TIMESTAMP(3))
                """);
        String columns = "SELECT id,tenant_id,action,after_summary,trace_id FROM sys_audit_event WHERE id BETWEEN 901 AND 903 ORDER BY id";
        var old = jdbc.queryForList(columns);
        var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isOne();
        flyway.validate();
        assertThat(jdbc.queryForList(columns)).isEqualTo(old);
        assertThat(jdbc.queryForList("SELECT id FROM sys_audit_event WHERE support_session_ref='80'", Long.class)).containsExactly(901L);
        assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='25'", Integer.class)).isEqualTo(1394665689);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}

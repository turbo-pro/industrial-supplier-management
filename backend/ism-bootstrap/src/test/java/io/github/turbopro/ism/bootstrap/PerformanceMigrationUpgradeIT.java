package io.github.turbopro.ism.bootstrap;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker=true)
class PerformanceMigrationUpgradeIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("performance_upgrade").withUsername("ism").withPassword("ism");
    @Test void preservesOriginalV25ChecksumAndBackfillsExistingEvaluationWithoutRepair(){
        var source=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        var jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("25").load().migrate();
        assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='25'",Integer.class)).isEqualTo(1394665689);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='per_supplier_evaluation' AND column_name='supplier_code'",Integer.class)).isZero();
        jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(9901,'V25_UPGRADE','升级测试','ACTIVE')");
        jdbc.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(9902,9901,'SITE','测试厂区','SITE','ACTIVE')");
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(9903,9901,9902,'LEGACY_SUP','历史供应商','SERVICE',1,1)");
        jdbc.update("INSERT INTO per_supplier_evaluation(id,tenant_id,organization_id,supplier_id,period_start,period_end,total_score,grade,created_by,updated_by) VALUES(9904,9901,9902,9903,'2026-01-01','2026-03-31',88.50,'A',1,1)");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("80").load().migrate();
        jdbc.update("INSERT INTO iam_portal_account(id,tenant_id,supplier_id,username,display_name,email,password_hash) VALUES(9905,9901,9903,'legacy.portal','历史联系人','legacy@example.test','not-a-login-credential')");
        jdbc.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(9907,9901,9905,'legacy.pdf','application/pdf',16,?,'LOCAL','legacy/object','ACTIVE')","a".repeat(64));
        jdbc.update("INSERT INTO res_upload_session(id,tenant_id,uploader_id,file_name,content_type,total_size,file_sha256,chunk_size,total_chunks,status,file_id,expires_at) VALUES(9906,9901,9905,'legacy.pdf','application/pdf',16,?,1048576,1,'COMPLETED',9907,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY))","a".repeat(64));
        jdbc.update("INSERT INTO res_portal_upload(upload_id,tenant_id,supplier_id,account_id,request_key) VALUES(9906,9901,9903,9905,'00000000-0000-0000-0000-000000000001')");
        jdbc.update("INSERT INTO qua_qualification_type(id,tenant_id,type_code,type_name,category,created_by,updated_by) VALUES(9908,9901,'LEGACY','历史证照','LEGAL',1,1)");
        jdbc.update("INSERT INTO qua_supplier_qualification(id,tenant_id,organization_id,supplier_id,qualification_type_id,certificate_no,file_id,created_by,updated_by) VALUES(9909,9901,9902,9903,9908,'LEGACY-CERT',9907,9905,9905)");
        jdbc.update("INSERT INTO qua_portal_submission(qualification_id,tenant_id,supplier_id,account_id,request_key,upload_id,request_json,file_name) VALUES(9909,9901,9903,9905,'00000000-0000-0000-0000-000000000002',9906,'{}','legacy.pdf')");
        var flyway=Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        flyway.migrate();flyway.validate();
        assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='25'",Integer.class)).isEqualTo(1394665689);
        var row=jdbc.queryForMap("SELECT supplier_code,supplier_name,total_score,version FROM per_supplier_evaluation WHERE id=9904");
        assertThat(row.get("supplier_code")).isEqualTo("LEGACY_SUP");assertThat(row.get("supplier_name")).isEqualTo("历史供应商");
        assertThat(row.get("total_score").toString()).isEqualTo("88.50");assertThat(row.get("version")).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT qualification_id FROM qua_portal_upload_use WHERE upload_id=9906 AND usage_kind='CERTIFICATE'",Long.class)).isEqualTo(9909L);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}

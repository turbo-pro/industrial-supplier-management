package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.platform.packageplan.*;
import io.github.turbopro.ism.platform.tenant.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"ism.print.worker-enabled=false", "ism.subscription-reminders.enabled=false"})
@Testcontainers(disabledWithoutDocker=true)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class HttpInputBoundaryIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("http_input_boundary").withUsername("ism").withPassword("ism");
    static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"), "ism-http-input-" + UUID.randomUUID());
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ism.storage.local-root", STORAGE::toString);
    }
    @AfterAll static void cleanupStorage() throws Exception {
        if (Files.exists(STORAGE)) try (var paths = Files.walk(STORAGE)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    @Autowired TestRestTemplate http;
    @Autowired TenantService tenants;
    @Autowired PackagePlanService plans;
    @Autowired JdbcTemplate jdbc;

    @Test void realUploadRejectsMissingHeadersWithoutWritesThenCompletesNormally() throws Exception {
        var plan = plans.create(new PackagePlanModels.CreatePackage("HTTP_INPUT", "输入边界验收"));
        var draft = plans.createVersion(Long.parseLong(plan.id()), new PackagePlanModels.CreateVersion(1, "验收版",
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1), List.of(new PackagePlanModels.ModuleGrant("RESOURCE", true, Map.of()))));
        var published = plans.publish(Long.parseLong(draft.id()), draft.version(), 9001);
        var tenant = tenants.create(new TenantModels.CreateTenant("HTTP-INPUT", "接口边界验收租户", "Asia/Shanghai", "zh-CN", published.id()));
        long tenantId = Long.parseLong(tenant.id());
        tenants.initialize(tenantId, new TenantModels.InitializeTenant("input-admin", "验收管理员", "Initial#Input123", "验收组织"));
        var login = http.postForEntity("/api/auth/login", login("Initial#Input123"), JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        var headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().path("data").path("accessToken").asText());
        var change = http.postForEntity("/api/auth/change-password", new HttpEntity<>(
                Map.of("oldPassword", "Initial#Input123", "newPassword", "Updated#Input456"), headers), JsonNode.class);
        assertThat(change.getStatusCode()).isEqualTo(HttpStatus.OK);
        var relogin = http.postForEntity("/api/auth/login", login("Updated#Input456"), JsonNode.class);
        assertThat(relogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        headers.setBearerAuth(relogin.getBody().path("data").path("accessToken").asText());
        headers.set("X-Trace-Id", "http-input-missing-key");
        byte[] bytes = "controlled content".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var command = Map.of("fileName", "private-customer-file.txt", "contentType", "text/plain", "totalSize", bytes.length, "sha256", hash);
        var missingKey = http.postForEntity("/api/files/uploads", new HttpEntity<>(command, headers), JsonNode.class);
        inputError(missingKey, "Idempotency-Key");
        assertThat(missingKey.getBody().path("traceId").asText()).isEqualTo("http-input-missing-key");
        assertThat(missingKey.getHeaders().getFirst("X-Trace-Id")).isEqualTo("http-input-missing-key");
        assertThat(missingKey.getBody().toString()).doesNotContain("private-customer-file", hash, "FileController", "MissingRequestHeaderException");
        assertThat(count("res_upload_session", tenantId)).isZero();
        assertThat(count("sys_idempotency_record", tenantId)).isZero();

        var malformedHeaders = new HttpHeaders(headers);
        malformedHeaders.set("Idempotency-Key", "malformed-body-key");
        malformedHeaders.setContentType(MediaType.APPLICATION_JSON);
        var malformed = http.postForEntity("/api/files/uploads", new HttpEntity<>("{\"private\":\"private-customer-file", malformedHeaders), JsonNode.class);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getBody().path("error").path("code").asText()).isEqualTo("COMMON_VALIDATION_MALFORMED_REQUEST");
        assertThat(malformed.getBody().toString()).doesNotContain("private-customer-file");
        assertThat(count("res_upload_session", tenantId)).isZero();
        assertThat(count("sys_idempotency_record", tenantId)).isZero();

        for (String invalid : List.of("   ", "private-key-" + "x".repeat(129))) {
            var invalidHeaders = new HttpHeaders(headers);
            invalidHeaders.set("Idempotency-Key", invalid);
            var rejected = http.postForEntity("/api/files/uploads", new HttpEntity<>(command, invalidHeaders), JsonNode.class);
            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(rejected.getBody().path("error").path("code").asText()).isEqualTo("COMMON_VALIDATION_FAILED");
            assertThat(rejected.getBody().toString()).doesNotContain("private-key-", "private-customer-file", "DataIntegrityViolation", "SQL");
            assertThat(count("res_upload_session", tenantId)).isZero();
            assertThat(count("sys_idempotency_record", tenantId)).isZero();
        }

        headers.set("Idempotency-Key", "k".repeat(128));
        var initialized = http.postForEntity("/api/files/uploads", new HttpEntity<>(command, headers), JsonNode.class);
        assertThat(initialized.getStatusCode()).isEqualTo(HttpStatus.OK);
        var replayed = http.postForEntity("/api/files/uploads", new HttpEntity<>(command, headers), JsonNode.class);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayed.getBody().path("data")).isEqualTo(initialized.getBody().path("data"));
        String uploadId = initialized.getBody().path("data").path("id").asText();
        assertThat(count("res_upload_session", tenantId)).isOne();
        var chunkHeaders = new HttpHeaders(headers);
        chunkHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String chunkPath = "/api/files/uploads/" + uploadId + "/chunks/0";
        var missingHash = http.exchange(chunkPath, HttpMethod.PUT, new HttpEntity<>(bytes, chunkHeaders), JsonNode.class);
        inputError(missingHash, "X-Chunk-SHA256");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM res_upload_chunk WHERE tenant_id=?", Long.class, tenantId)).isZero();
        chunkHeaders.set("X-Chunk-SHA256", hash);
        var chunk = http.exchange(chunkPath, HttpMethod.PUT, new HttpEntity<>(bytes, chunkHeaders), JsonNode.class);
        assertThat(chunk.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chunk.getBody().path("data").path("uploadedChunks").size()).isOne();
        int version = chunk.getBody().path("data").path("version").asInt();

        var completeHeaders = new HttpHeaders(headers);
        completeHeaders.remove("Idempotency-Key");
        String completePath = "/api/files/uploads/" + uploadId + "/complete?version=" + version;
        var missingCompletionKey = http.postForEntity(completePath, new HttpEntity<>(completeHeaders), JsonNode.class);
        inputError(missingCompletionKey, "Idempotency-Key");
        assertThat(count("res_file_object", tenantId)).isZero();
        completeHeaders.set("Idempotency-Key", "valid-upload-complete");
        var completed = http.postForEntity(completePath, new HttpEntity<>(completeHeaders), JsonNode.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String fileId = completed.getBody().path("data").path("id").asText();
        assertThat(count("res_file_object", tenantId)).isOne();
        var download = http.exchange("/api/files/" + fileId + "/content", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).isEqualTo(bytes);
        assertThat(http.getForEntity("/api/files/" + fileId + "/content", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    private Map<String, String> login(String password) {
        return Map.of("tenantCode", "HTTP-INPUT", "username", "input-admin", "password", password, "deviceId", "http-input");
    }
    private long count(String table, long tenant) {
        if (!Set.of("res_upload_session", "sys_idempotency_record", "res_file_object").contains(table)) throw new IllegalArgumentException();
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=?", Long.class, tenant);
    }
    private void inputError(ResponseEntity<JsonNode> response, String field) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("COMMON_VALIDATION_FAILED");
        assertThat(response.getBody().path("error").path("fieldErrors").get(0).path("field").asText()).isEqualTo(field);
        assertThat(response.getBody().path("error").path("retryable").asBoolean()).isFalse();
    }
}

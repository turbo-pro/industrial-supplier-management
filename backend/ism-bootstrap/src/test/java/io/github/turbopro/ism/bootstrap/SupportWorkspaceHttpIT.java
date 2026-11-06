package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "ism.support.sessions.enabled=true")
class SupportWorkspaceHttpIT extends TenantSupportIT {
    @Test
    void workspaceCredentialHasItsOwnRealmAndTracksLiveConsent() {
        var tenant = fixture();
        String employee = tenantLogin(tenant, "pref-admin");
        String owner = login(false);
        String requestId = approved(tenant);
        save(employee, tenant.role(), 0, true);
        ok(decide(employee, requestId, "approve", "workspace-" + requestId, decision(detail(employee, requestId))));
        String reservation = "/api/console/support-requests/" + requestId + "/sessions";
        String sessionId = ok(request("POST", reservation, owner, null, null)).path("sessionId").asText();
        String console = "/api/console/support-sessions/" + sessionId;
        String workspace = "/api/support-workspace/" + sessionId;
        String browser = "W".repeat(43);
        var browserBody = Map.of("browserSecret", browser);
        assertThat(request("POST", console + "/workspace-entry-token", login(true), null, browserBody).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        var issued = request("POST", console + "/workspace-entry-token", owner, null, browserBody);
        assertThat(issued.getHeaders().getCacheControl()).contains("no-store");
        String entry = ok(issued).path("entryToken").asText();
        var exchange = Map.of("entryToken", entry, "browserSecret", browser);
        assertThat(request("POST", console + "/exchange", owner, null, exchange).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(workspace + "/exchange", Map.of("entryToken", entry, "browserSecret", "B".repeat(43))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        var exchanged = post(workspace + "/exchange", exchange);
        assertThat(exchanged.getHeaders().getCacheControl()).contains("no-store");
        String access = ok(exchanged).path("accessSecret").asText();
        assertThat(post(workspace + "/exchange", exchange).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = Map.of("accessSecret", access);
        var identityResponse = post(workspace + "/identity", body);
        assertThat(identityResponse.getHeaders().getCacheControl()).contains("no-store");
        var identity = ok(identityResponse);
        assertThat(identity.path("realm").asText()).isEqualTo("SUPPORT_WORKSPACE");
        assertThat(identity.path("tenantId").asText()).isEqualTo(Long.toString(tenant.tenant()));
        assertThat(identity.path("operatorName").asText()).isNotBlank();
        assertThat(identity.path("expiresAt").asText()).isNotBlank();
        assertThat(identity.path("scope").path("fields").toString()).contains("code", "name").doesNotContain("status");
        assertThat(request("POST", workspace + "/identity", employee, null, body).getStatusCode().value()).isIn(401, 403);
        assertThat(request("GET", "/api/suppliers", access, null, null).getStatusCode().value()).isIn(401, 403);
        assertThat(request("POST", console + "/tenants/" + tenant.tenant() + "/suppliers/1/read", owner, null, body)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/api/support-workspace/" + (Long.parseLong(sessionId) + 1) + "/identity", body).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String other = ok(request("POST", reservation, owner, null, null)).path("sessionId").asText();
        String otherConsole = "/api/console/support-sessions/" + other;
        String otherEntry = ok(request("POST", otherConsole + "/entry-token", owner, null, browserBody))
                .path("entryToken").asText();
        assertThat(post("/api/support-workspace/" + other + "/exchange",
                Map.of("entryToken", otherEntry, "browserSecret", browser)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String consoleAccess = ok(request("POST", otherConsole + "/exchange", owner, null,
                Map.of("entryToken", otherEntry, "browserSecret", browser))).path("accessSecret").asText();
        assertThat(post("/api/support-workspace/" + other + "/identity",
                Map.of("accessSecret", consoleAccess)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        long supplierId = tenant.tenant() + 96000;
        jdbc.update("""
                INSERT INTO sup_supplier (id, tenant_id, organization_id, supplier_code, supplier_name,
                    supplier_type, remark, created_by, updated_by)
                VALUES (?, ?, ?, 'SUP-WORKSPACE', '支持工作台供应商', 'MANUFACTURER', 'PRIVATE', ?, ?)
                """, supplierId, tenant.tenant(), tenant.first(), tenant.admin(), tenant.admin());
        String read = workspace + "/suppliers/" + supplierId + "/read";
        var projection = ok(post(read, body));
        assertThat(projection.size()).isEqualTo(2);
        assertThat(projection.path("code").asText()).isEqualTo("SUP-WORKSPACE");
        assertThat(projection.path("name").asText()).isEqualTo("支持工作台供应商");
        assertThat(ok(post(workspace + "/suppliers/" + (supplierId + 1) + "/read", body)).isEmpty()).isTrue();
        assertThat(post(workspace + "/exit", Map.of("accessSecret", consoleAccess)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // These credentials have already succeeded on their own support surfaces.
        // Exercise real employee routes while both sessions are still active.
        for (String credential : java.util.List.of(access, consoleAccess, owner)) {
            assertDenied("GET", "/api/suppliers/" + supplierId, credential, null);
            assertDenied("POST", "/api/suppliers/" + supplierId + "/status", credential,
                    Map.of("status", "ACTIVE", "version", 0, "reason", "support boundary probe"));
            assertDenied("POST", "/api/search", credential, Map.of("keyword", "SUP-WORKSPACE"));
            assertDenied("GET", "/api/files/1/content", credential, null);
            assertDenied("GET", "/api/tasks", credential, null);
        }
        assertThat(ok(request("GET", "/api/suppliers/" + supplierId, employee, null, null))
                .path("status").asText()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT version FROM sup_supplier WHERE id=?",
                Integer.class, supplierId)).isZero();

        var consent = detail(employee, requestId);
        ok(decide(employee, requestId, "revoke", "workspace-revoke-" + requestId, decision(consent)));
        assertThat(post(workspace + "/identity", body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_audit_event WHERE action='SUPPORT_SESSION_ACTIVATE'
                  AND tenant_id=0 AND object_id=? AND JSON_UNQUOTE(JSON_EXTRACT(after_summary,'$.surface'))='SUPPORT_WORKSPACE'
                """, Integer.class, sessionId)).isOne();
        assertThat(jdbc.queryForObject("SELECT access_digest FROM plt_support_session WHERE id=?",
                String.class, sessionId)).hasSize(64).isNotEqualTo(access);
        assertThat(post(read, Map.of("accessSecret", "X".repeat(43))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(read, body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ok(post(workspace + "/exit", body)).path("status").asText()).isEqualTo("REVOKED");
        assertThat(post(read, body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT access_digest FROM plt_support_session WHERE id=?",
                String.class, sessionId)).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_audit_event
                WHERE action='SUPPORT_SUPPLIER_READ' AND object_id=? AND tenant_id=0
                  AND JSON_UNQUOTE(JSON_EXTRACT(after_summary,'$.surface'))='SUPPORT_WORKSPACE'
                """, Integer.class, supplierId)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_audit_event
                WHERE action='SUPPORT_SESSION_REVOKE' AND object_id=? AND tenant_id=0
                  AND JSON_UNQUOTE(JSON_EXTRACT(after_summary,'$.surface'))='SUPPORT_WORKSPACE'
                """, Integer.class, sessionId)).isOne();
        var denial = jdbc.queryForMap("""
                SELECT operator_id, trace_id, after_summary FROM sys_audit_event
                WHERE action='SUPPORT_SUPPLIER_READ_DENIED' AND object_id=? AND tenant_id=0
                """, supplierId);
        String logs = console + "/access-logs";
        var report = request("GET", logs, owner, null, null);
        assertThat(report.getHeaders().getCacheControl()).contains("no-store");
        var rows = ok(report).path("items");
        assertThat(rows.size()).isEqualTo(3);
        assertThat(rows.get(0).path("result").asText()).isEqualTo("DENIED");
        assertThat(rows.get(1).path("result").asText()).isEqualTo("NOT_VISIBLE");
        assertThat(rows.get(2).path("result").asText()).isEqualTo("READ");
        assertThat(rows.get(2).path("objectId").asText()).isEqualTo(Long.toString(supplierId));
        assertThat(rows.toString()).doesNotContain(access, entry, browser, "SUP-WORKSPACE", "PRIVATE");
        assertThat(ok(request("GET", otherConsole + "/access-logs", owner, null, null)).path("items").isEmpty()).isTrue();
        assertThat(request("GET", logs, login(true), null, null).getStatusCode().value()).isIn(403,404);
        assertThat(request("GET", logs, employee, null, null).getStatusCode().value()).isIn(401,403);
        assertThat(request("GET", logs + "?page=101", owner, null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(denial.get("operator_id").toString()).isEqualTo(identity.path("operatorId").asText());
        assertThat(denial.get("trace_id").toString()).isNotBlank();
        assertThat(denial.get("after_summary").toString())
                .contains(sessionId, Long.toString(tenant.tenant()), "SUPPORT_WORKSPACE", "DENIED")
                .doesNotContain(access, entry, browser, "SUP-WORKSPACE", "PRIVATE");
    }

    private void assertDenied(String method, String path, String credential, Object body) {
        var response = request(method, path, credential, null, body);
        assertThat(response.getStatusCode().value()).as("%s %s", method, path).isIn(401, 403);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).isNull();
        assertThat(response.getBody().has("data")).isFalse();
    }

    private ResponseEntity<JsonNode> post(String path, Object body) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body), JsonNode.class);
    }
}

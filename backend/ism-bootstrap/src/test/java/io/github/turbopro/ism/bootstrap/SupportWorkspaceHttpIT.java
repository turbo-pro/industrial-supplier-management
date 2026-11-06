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
        var consent = detail(employee, requestId);
        ok(decide(employee, requestId, "revoke", "workspace-revoke-" + requestId, decision(consent)));
        assertThat(post(workspace + "/identity", body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_audit_event WHERE action='SUPPORT_SESSION_ACTIVATE'
                  AND tenant_id=0 AND object_id=? AND JSON_UNQUOTE(JSON_EXTRACT(after_summary,'$.surface'))='SUPPORT_WORKSPACE'
                """, Integer.class, sessionId)).isOne();
        assertThat(jdbc.queryForObject("SELECT access_digest FROM plt_support_session WHERE id=?",
                String.class, sessionId)).hasSize(64).isNotEqualTo(access);
    }

    private ResponseEntity<JsonNode> post(String path, Object body) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body), JsonNode.class);
    }
}

package io.github.turbopro.ism.bootstrap;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "ism.support.sessions.enabled=true")
class SupportSessionHttpIT extends TenantSupportIT {
    @Test
    void ownerCanPageAndRevokeSessionsWithoutAnyCredential() {
        var tenant = fixture();
        String employee = tenantLogin(tenant, "pref-admin"), owner = login(false);
        String requestId = approved(tenant);
        save(employee, tenant.role(), 0, true);
        ok(decide(employee, requestId, "approve", "http-list-" + requestId, decision(detail(employee, requestId))));
        String path = "/api/console/support-requests/" + requestId + "/sessions";
        for (int i = 0; i < 21; i++) ok(request("POST", path, owner, null, null));
        var newestFirst = jdbc.queryForList("""
                SELECT CAST(id AS CHAR) FROM plt_support_session WHERE request_id=?
                ORDER BY created_at DESC, id DESC
                """, String.class, requestId);
        var response = request("GET", path, owner, null, null);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        var first = ok(response);
        assertThat(first.path("items").size()).isEqualTo(20);
        assertThat(first.path("items").get(0).path("id").asText()).isEqualTo(newestFirst.get(0));
        var second = ok(request("GET", path + "?before=" + first.path("nextBefore").asText(), owner, null, null));
        assertThat(second.path("items").size()).isOne();
        assertThat(second.path("items").get(0).path("id").asText()).isEqualTo(newestFirst.get(20));
        assertThat(second.path("nextBefore").isNull()).isTrue();
        assertThat(first.toString()).doesNotContain("entryToken", "accessSecret", "Digest", "scope");
        assertThat(request("GET", path, login(true), null, null).getStatusCode().value()).isIn(403,404);
        assertThat(request("GET", path, employee, null, null).getStatusCode().value()).isIn(401,403);
        assertThat(request("GET", path + "?before=0", owner, null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String last = newestFirst.get(0);
        String revoke = "/api/console/support-sessions/" + last + "/revoke";
        ok(request("POST", revoke, owner, null, null));
        ok(request("POST", revoke, owner, null, null));
        assertThat(ok(request("GET", path, owner, null, null)).path("items").get(0).path("state").asText()).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE object_id=? AND action='SUPPORT_SESSION_REVOKE'",
                Integer.class, last)).isOne();
    }

    @Test
    void consoleCanReadOnlyApprovedProjectionUntilRevoked() {
        var tenant = fixture();
        String employee = tenantLogin(tenant, "pref-admin");
        String owner = login(false);
        String requestId = approved(tenant);
        long supplierId = tenant.tenant() + 96000;
        jdbc.update("""
                INSERT INTO sup_supplier (id, tenant_id, organization_id, supplier_code, supplier_name,
                    supplier_type, remark, created_by, updated_by)
                VALUES (?, ?, ?, 'SUP-HTTP', '支持验收供应商', 'MANUFACTURER', 'PRIVATE', ?, ?)
                """, supplierId, tenant.tenant(), tenant.first(), tenant.admin(), tenant.admin());
        String reservation = "/api/console/support-requests/" + requestId + "/sessions";
        assertThat(request("POST", reservation, owner, null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        save(employee, tenant.role(), 0, true);
        ok(decide(employee, requestId, "approve", "http-session-" + requestId, decision(detail(employee, requestId))));
        assertThat(request("POST", reservation, login(true), null, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        String sessionId = ok(request("POST", reservation, owner, null, null)).path("sessionId").asText();
        String session = "/api/console/support-sessions/" + sessionId;
        String browser = "A".repeat(43);
        var issued = request("POST", session + "/entry-token", owner, null, Map.of("browserSecret", browser));
        String entry = ok(issued).path("entryToken").asText();
        assertThat(issued.getHeaders().getCacheControl()).contains("no-store");
        var exchange = Map.of("browserSecret", browser, "entryToken", entry);
        String access = ok(request("POST", session + "/exchange", owner, null, exchange)).path("accessSecret").asText();
        assertThat(request("POST", session + "/exchange", owner, null, exchange).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        String read = session + "/tenants/" + tenant.tenant() + "/suppliers/" + supplierId + "/read";
        var secret = Map.of("accessSecret", access);
        var result = ok(request("POST", read, owner, null, secret));
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.path("name").asText()).isEqualTo("支持验收供应商");
        assertThat(result.path("code").asText()).isEqualTo("SUP-HTTP");
        assertThat(request("POST", read, employee, null, secret).getStatusCode().value()).isIn(401, 403);
        String incorrectSecret = "B".repeat(43);
        assertThat(request("POST", read, owner, null, Map.of("accessSecret", incorrectSecret)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        long wrongTenantId = tenant.tenant() + 1;
        String wrongTenant = session + "/tenants/" + wrongTenantId + "/suppliers/" + supplierId + "/read";
        assertThat(request("POST", wrongTenant, owner, null, secret).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ok(request("POST", session + "/revoke", owner, null, null));
        assertThat(request("POST", read, owner, null, secret).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE action='SUPPORT_SUPPLIER_READ' AND object_id=?",
                Integer.class, supplierId)).isOne();
        var denied = jdbc.queryForList("""
                SELECT tenant_id, operator_id, trace_id, after_summary
                FROM sys_audit_event
                WHERE action='SUPPORT_SUPPLIER_READ_DENIED' AND object_id=?
                """, supplierId);
        Long operator = jdbc.queryForObject("SELECT platform_operator_id FROM plt_support_session WHERE id=?",
                Long.class, sessionId);
        assertThat(denied).hasSize(3).allSatisfy(event -> {
            assertThat(event.get("tenant_id")).isEqualTo(0L);
            assertThat(event.get("operator_id")).isEqualTo(operator);
            assertThat((String) event.get("trace_id")).isNotBlank();
            assertThat(event.get("after_summary").toString()).contains("DENIED", sessionId)
                    .doesNotContain(access, incorrectSecret, entry, browser, "SUP-HTTP", "PRIVATE");
        });
        assertThat(denied).anySatisfy(event ->
                assertThat(event.get("after_summary").toString()).contains(Long.toString(wrongTenantId)));
    }
}

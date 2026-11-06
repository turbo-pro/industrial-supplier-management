package io.github.turbopro.ism.bootstrap;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "ism.support.sessions.enabled=true")
class SupportSessionHttpIT extends TenantSupportIT {
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
        ok(request("POST", session + "/revoke", owner, null, null));
        assertThat(request("POST", read, owner, null, secret).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE action='SUPPORT_SUPPLIER_READ' AND object_id=?",
                Integer.class, supplierId)).isOne();
    }
}

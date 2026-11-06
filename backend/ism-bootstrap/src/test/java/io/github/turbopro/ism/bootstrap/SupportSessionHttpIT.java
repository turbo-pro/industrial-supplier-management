package io.github.turbopro.ism.bootstrap;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "ism.support.sessions.enabled=true")
class SupportSessionHttpIT extends TenantSupportIT {
    @Test
    void independentAuditorSeesMetadataWithoutOperatorCapabilities() {
        var tenant = fixture();
        String employee = tenantLogin(tenant, "pref-admin"), owner = login(false);
        String platformAdmin = login(true);
        String auditorId = ok(request("POST", "/api/console/users", platformAdmin, "create-auditor",
                Map.of("username","support-auditor","displayName","独立审计员","initialPassword","Console#Pass123","roleCode","PLATFORM_SUPPORT_AUDITOR"))).path("id").asText();
        jdbc.update("UPDATE plt_user SET force_password_change=0 WHERE id=?", auditorId);
        String auditor = http.postForEntity("/api/console/auth/login", Map.of("username","support-auditor","password","Console#Pass123","deviceId","audit-tests"), com.fasterxml.jackson.databind.JsonNode.class)
                .getBody().path("data").path("accessToken").asText();
        String requestId = approved(tenant);
        save(employee, tenant.role(), 0, true);
        ok(decide(employee, requestId, "approve", "audit-" + requestId, decision(detail(employee, requestId))));
        String reservation = "/api/console/support-requests/" + requestId + "/sessions";
        String sessionId = ok(request("POST", reservation, owner, null, null)).path("sessionId").asText();
        String console = "/api/console/support-sessions/" + sessionId;
        String browser = "A".repeat(43);
        String entry = ok(request("POST", console + "/entry-token", owner, null, Map.of("browserSecret",browser))).path("entryToken").asText();
        String access = ok(request("POST", console + "/exchange", owner, null, Map.of("browserSecret",browser,"entryToken",entry))).path("accessSecret").asText();
        ok(request("POST", console + "/tenants/" + tenant.tenant() + "/suppliers/1/read", owner, null, Map.of("accessSecret",access)));
        String sessions = "/api/console/support-audit/sessions";
        var response = request("GET", sessions, auditor, null, null);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        var rows = ok(response).path("items");
        assertThat(rows.findValuesAsText("id")).contains(sessionId);
        assertThat(rows.toString()).doesNotContain(access, entry, browser, "scope", "reason");
        var logs = ok(request("GET", sessions + "/" + sessionId + "/access-logs", auditor, null, null)).path("items");
        assertThat(logs.size()).isOne();
        assertThat(logs.get(0).path("result").asText()).isEqualTo("NOT_VISIBLE");
        assertThat(request("GET", sessions + "?page=101", auditor, null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        for (String denied : java.util.List.of(owner, employee)) {
            assertThat(request("GET", sessions, denied, null, null).getStatusCode().value()).isIn(401,403);
            assertThat(request("GET", sessions + "/" + sessionId + "/access-logs", denied, null, null).getStatusCode().value()).isIn(401,403);
        }
        for (String path : java.util.List.of(reservation, console + "/entry-token", console + "/terminate", console + "/revoke")) {
            assertThat(request("POST", path, auditor, null, Map.of("browserSecret",browser,"reason","unauthorized"))
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(request("GET", "/api/suppliers/1", auditor, null, null).getStatusCode().value()).isIn(401,403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_role_permission WHERE role_id=1003",Integer.class)).isOne();
        var preview = ok(request("GET", "/api/console/users/" + auditorId + "/role-impact?roleCode=PLATFORM_SUPPORT&version=0", platformAdmin, null, null));
        assertThat(preview.path("canApply").asBoolean()).isTrue();
        ok(request("PUT", "/api/console/users/" + auditorId + "/role", platformAdmin, "auditor-to-support", Map.of("roleCode","PLATFORM_SUPPORT","version",0)));
        assertThat(request("GET", sessions, auditor, null, null).getStatusCode().value()).isIn(401,403);

    }

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
    void consoleCanReadOnlyApprovedProjectionUntilRevoked(CapturedOutput output) {
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
        var mismatch = request("POST", wrongTenant, owner, null, secret);
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(mismatch.getBody().toString()).doesNotContain("TENANT_MISMATCH", Long.toString(tenant.tenant()));
        assertThat(request("POST", wrongTenant, owner, null, Map.of("accessSecret", incorrectSecret)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
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
        assertThat(denied).hasSize(4).allSatisfy(event -> {
            assertThat(event.get("tenant_id")).isEqualTo(0L);
            assertThat(event.get("operator_id")).isEqualTo(operator);
            assertThat((String) event.get("trace_id")).isNotBlank();
            assertThat(event.get("after_summary").toString()).contains("DENIED", sessionId)
                    .doesNotContain(access, incorrectSecret, entry, browser, "SUP-HTTP", "PRIVATE");
        });
        assertThat(denied).anySatisfy(event ->
                assertThat(event.get("after_summary").toString()).contains(Long.toString(wrongTenantId)));
        var alerts = output.getOut().lines().filter(line -> line.contains("SUPPORT_TENANT_MISMATCH")).toList();
        assertThat(alerts).singleElement().satisfies(line -> assertThat(line)
                .contains(sessionId, "expectedTenantId=" + tenant.tenant(), "requestedTenantId=" + wrongTenantId)
                .doesNotContain(access, incorrectSecret, entry, browser));
        var accessLog = ok(request("GET", session + "/access-logs", owner, null, null)).path("items");
        assertThat(accessLog.size()).isEqualTo(2);
        assertThat(accessLog.get(0).path("result").asText()).isEqualTo("DENIED");
    }
}

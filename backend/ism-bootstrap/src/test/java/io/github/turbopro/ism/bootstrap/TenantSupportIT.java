package io.github.turbopro.ism.bootstrap;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.iam.access.AccessModels;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot;
import io.github.turbopro.ism.platform.support.SupportSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class TenantSupportIT extends SiteProjectSupport {
 JsonNode ok(ResponseEntity<JsonNode> r){assertThat(r.getStatusCode()).as("%s",r.getBody()).isEqualTo(HttpStatus.OK);return r.getBody().path("data");}
 String approved(Fixture f){String owner=login(false),reviewer=login(true);var c=ok(request("POST","/api/console/tenants/"+f.tenant()+"/support-requests",owner,UUID.randomUUID().toString(),Map.of("ticketNo","TENANT-HELP","reason","基础字段只读排查","profileCode","SUPPLIER_BASIC_READ_V1","durationMinutes",60)));String id=c.path("id").asText();ok(request("POST","/api/console/support-requests/"+id+"/submit",owner,UUID.randomUUID().toString(),Map.of("version",0,"comment","申请支持")));ok(request("POST","/api/console/support-requests/"+id+"/approve",reviewer,UUID.randomUUID().toString(),Map.of("version",1,"comment","独立平台审批")));return id;}
 Map<String,Object> policy(long role,int version,boolean enabled){return Map.of("enabled",enabled,"roleId",Long.toString(role),"fields",List.of("code","name"),"maxMinutes",10,"version",version);}
 JsonNode save(String token,long role,int version,boolean enabled){return ok(request("PUT","/api/tenant-support/policy",token,UUID.randomUUID().toString(),policy(role,version,enabled)));}
 JsonNode detail(String token,String id){return ok(request("GET","/api/tenant-support/requests/"+id,token,null,null));}
 Map<String,Object> decision(JsonNode d){var b=new HashMap<String,Object>();b.put("requestVersion",d.path("version").asInt());b.put("policyVersion",d.path("policyVersion").asInt());b.put("decisionVersion",d.path("consent").path("version").asInt());b.put("comment","租户明确决定");if(!d.path("scopeHash").isNull()&&!d.path("scopeHash").isMissingNode())b.put("scopeHash",d.path("scopeHash").asText());if(!d.path("proposedExpiresAt").isNull()&&!d.path("proposedExpiresAt").isMissingNode())b.put("expiresAt",d.path("proposedExpiresAt").asText());return b;}
 ResponseEntity<JsonNode> decide(String token,String id,String action,String key,Map<String,Object> b){return request("POST","/api/tenant-support/requests/"+id+"/"+action,token,key,b);}
 JsonNode authorization(String id){return ok(request("GET","/api/console/support-requests/"+id+"/authorization",login(true),null,null));}
 long role(Fixture f,String type,Set<String> orgs){try(var scope=TenantContext.open(f.tenant(),f.admin())){var r=access.createRole(new AccessModels.CreateRole("SUPPORT_BOUND","只读支持上界"));access.grantRole(Long.parseLong(r.id()),new AccessModels.GrantRole(0,Set.of("supplier:master:view"),Set.of("SUPPLIER_MASTER"),List.of(new AccessModels.DataScopeGrant("supplier:master",type,orgs))));return Long.parseLong(r.id());}}
 void grant(Fixture f,long role,String type,Set<String> orgs,boolean read){int v=jdbc.queryForObject("SELECT version FROM iam_role WHERE tenant_id=? AND id=?",Integer.class,f.tenant(),role);try(var scope=TenantContext.open(f.tenant(),f.admin())){access.grantRole(role,new AccessModels.GrantRole(v,read?Set.of("supplier:master:view"):Set.of(),Set.of("SUPPLIER_MASTER"),List.of(new AccessModels.DataScopeGrant("supplier:master",type,orgs))));}}
 @Test void defaultDisabledAndCrossRealmCrossTenantAreDenied(){var f=fixture();String t=tenantLogin(f,"pref-admin"),id=approved(f);assertThat(ok(request("GET","/api/tenant-support/policy",t,null,null)).path("enabled").asBoolean()).isFalse();assertThat(authorization(id).path("reason").asText()).isEqualTo("POLICY_DISABLED");assertThat(decide(t,id,"approve",UUID.randomUUID().toString(),decision(detail(t,id))).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);var other=fixture();String foreign=tenantLogin(other,"pref-admin");assertThat(request("GET","/api/tenant-support/requests/"+id,foreign,null,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);assertThat(request("PUT","/api/tenant-support/policy",t,UUID.randomUUID().toString(),policy(other.role(),0,true)).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(request("GET","/api/tenant-support/policy",login(true),null,null).getStatusCode().value()).isIn(401,403);assertThat(request("GET","/api/tenant-support/policy",tenantLogin(f,"pref-reader"),null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);}
 @Test void approvalFreezesUpperBoundAndCurrentRoleCanOnlyNarrowIt(){var f=fixture();String t=tenantLogin(f,"pref-admin"),id=approved(f);long role=role(f,"ORGANIZATION_SET",Set.of(Long.toString(f.first())));save(t,role,0,true);var d=detail(t,id);var approved=ok(decide(t,id,"approve","approve-"+id,decision(d)));assertThat(approved.path("consent").path("reviewerId").asText()).isEqualTo(Long.toString(f.admin()));var a=authorization(id);assertThat(a.path("authorizationReady").asBoolean()).isTrue();assertThat(a.path("sessionEnabled").asBoolean()).isFalse();assertThat(a.path("scope").path("fields").toString()).contains("code","name").doesNotContain("mobile");grant(f,role,"TENANT_ALL",Set.of(),true);var expanded=authorization(id);assertThat(expanded.path("scope").path("tenantAll").asBoolean()).isFalse();assertThat(expanded.path("scope").path("organizations").size()).isEqualTo(2);grant(f,role,"ORGANIZATION_SET",Set.of(Long.toString(f.department())),true);assertThat(authorization(id).path("scope").path("organizations").size()).isOne();grant(f,role,"ORGANIZATION_SET",Set.of(Long.toString(f.department())),false);assertThat(authorization(id).path("authorizationReady").asBoolean()).isFalse();}
 @Test void staleRolePreviewCannotApproveAndUserDependentScopesAreNotDelegated(){var f=fixture();String t=tenantLogin(f,"pref-admin"),id=approved(f);long role=role(f,"ORGANIZATION_SET",Set.of(Long.toString(f.first())));save(t,role,0,true);var stale=decision(detail(t,id));grant(f,role,"TENANT_ALL",Set.of(),true);assertThat(decide(t,id,"approve","stale-"+id,stale).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);grant(f,role,"CREATED",Set.of(),true);assertThat(request("PUT","/api/tenant-support/policy",t,UUID.randomUUID().toString(),policy(role,1,true)).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(detail(t,id).path("canApprove").asBoolean()).isFalse();}
 @Test void revokeAndPolicyChangesDoNotAutomaticallyRestorePreviousConsent(){var f=fixture();String t=tenantLogin(f,"pref-admin"),id=approved(f);save(t,f.role(),0,true);ok(decide(t,id,"approve","ap-"+id,decision(detail(t,id))));var b=decision(detail(t,id));ok(decide(t,id,"revoke","rv-"+id,b));assertThat(authorization(id).path("reason").asText()).isEqualTo("REVOKED");assertThat(decide(t,id,"approve","again-"+id,decision(detail(t,id))).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);save(t,f.role(),1,true);assertThat(authorization(id).path("reason").asText()).isEqualTo("TENANT_REVIEW_REQUIRED");ok(decide(t,id,"approve","new-policy-"+id,decision(detail(t,id))));assertThat(detail(t,id).path("history").size()).isEqualTo(2);save(t,f.role(),2,false);assertThat(authorization(id).path("authorizationReady").asBoolean()).isFalse();save(t,f.role(),3,true);assertThat(authorization(id).path("authorizationReady").asBoolean()).isFalse();}
 @Test void expirationAndFieldTamperingFailClosed(){var f=fixture();String t=tenantLogin(f,"pref-admin"),id=approved(f);var bad=new HashMap<>(policy(f.role(),0,true));bad.put("fields",List.of("password","mobile"));assertThat(request("PUT","/api/tenant-support/policy",t,UUID.randomUUID().toString(),bad).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);save(t,f.role(),0,true);ok(decide(t,id,"approve","expire-ap-"+id,decision(detail(t,id))));jdbc.update("UPDATE plt_support_tenant_decision SET expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE request_id=?",id);assertThat(authorization(id).path("reason").asText()).isEqualTo("EXPIRED");}
 @Test void operatorRequestAndModuleRevocationAndReceiptReplayRemainBounded(){var f=fixture();String t=tenantLogin(f,"pref-admin"),id=approved(f);save(t,f.role(),0,true);var b=decision(detail(t,id));String key="replay-"+id;ok(decide(t,id,"approve",key,b));ok(decide(t,id,"approve",key,b));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_support_tenant_decision WHERE request_id=?",Integer.class,id)).isOne();jdbc.update("UPDATE plt_user SET manual_locked=1 WHERE id=790002");assertThat(authorization(id).path("reason").asText()).isEqualTo("OPERATOR_UNAVAILABLE");jdbc.update("UPDATE plt_user SET manual_locked=0 WHERE id=790002");assertThat(authorization(id).path("authorizationReady").asBoolean()).isTrue();jdbc.update("UPDATE plt_package_module pm JOIN plt_tenant t ON t.package_version_id=pm.package_version_id JOIN plt_module m ON m.id=pm.module_id SET pm.enabled=0 WHERE t.id=? AND m.module_code='SUPPLIER'",f.tenant());assertThat(authorization(id).path("authorizationReady").asBoolean()).isFalse();jdbc.update("UPDATE plt_package_module pm JOIN plt_tenant t ON t.package_version_id=pm.package_version_id JOIN plt_module m ON m.id=pm.module_id SET pm.enabled=1 WHERE t.id=? AND m.module_code='SUPPLIER'",f.tenant());var current=ok(request("GET","/api/console/support-requests/"+id,login(false),null,null));ok(request("POST","/api/console/support-requests/"+id+"/cancel",login(false),UUID.randomUUID().toString(),Map.of("version",current.path("version").asInt(),"comment","取消不再支持")));assertThat(authorization(id).path("authorizationReady").asBoolean()).isFalse();assertThat(detail(t,id).path("status").asText()).isEqualTo("CANCELLED");}
 @Test void competingDecisionsAndAuditRollbackKeepOneDurableOutcome()throws Exception{var f=fixture();String t=tenantLogin(f,"pref-admin"),id=approved(f);save(t,f.role(),0,true);var b=decision(detail(t,id));trigger("CREATE TRIGGER ts_audit_fail BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.action='TENANT_SUPPORT_APPROVE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test tenant support rollback'; END IF; END");try{assertThat(decide(t,id,"approve","retry-"+id,b).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);}finally{trigger("DROP TRIGGER ts_audit_fail");}assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_support_tenant_decision WHERE request_id=?",Integer.class,id)).isZero();var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->{gate.await();return decide(t,id,"approve","retry-"+id,b).getStatusCode().value();});var c=pool.submit(()->{gate.await();return decide(t,id,"reject","reject-"+id,b).getStatusCode().value();});gate.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),c.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_support_tenant_decision WHERE request_id=?",Integer.class,id)).isOne();}

 @Autowired SupportSessionService supportSessions;
 @Autowired io.github.turbopro.ism.platform.support.SupportSupplierReadService supportReads;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

 @Test void pendingSessionBindsOwnerConsentAndFrozenScope() throws Exception {
  var f = fixture();
  String tenantToken = tenantLogin(f, "pref-admin");
  String id = approved(f);
  long requestId = Long.parseLong(id);
  long owner = jdbc.queryForObject("SELECT platform_operator_id FROM plt_support_request WHERE id=?", Long.class, requestId);
  var permissions = new PermissionSnapshot(Set.of("platform:support:request", "platform:support:view", "platform:support:review"), Map.of(), Set.of());
  try (var auth = AuthorizationContext.open(permissions); var actor = TenantContext.open(0, owner)) {
   assertThatThrownBy(() -> supportSessions.createPending(requestId)).hasMessageContaining("支持授权当前不可用");
  }
  long roleId = role(f, "ORGANIZATION_SET", Set.of(Long.toString(f.first())));
  save(tenantToken, roleId, 0, true);
  var approved = ok(decide(tenantToken, id, "approve", "reserve-approve-" + id, decision(detail(tenantToken, id))));
  try (var auth = AuthorizationContext.open(permissions); var actor = TenantContext.open(0, owner + 1000)) {
   assertThatThrownBy(() -> supportSessions.createPending(requestId))
       .isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
  }
  long sessionId;
  try (var auth = AuthorizationContext.open(permissions); var actor = TenantContext.open(0, owner)) {
   sessionId = supportSessions.createPending(requestId);
  }
  assertThat(jdbc.queryForObject("""
      SELECT COUNT(*) FROM plt_support_session
      WHERE id=? AND request_id=? AND decision_id=? AND platform_operator_id=? AND target_tenant_id=?
        AND status='PENDING' AND expires_at > created_at
        AND JSON_CONTAINS(granted_scope_json, JSON_ARRAY('code','name'), '$.fields')
        AND JSON_LENGTH(granted_scope_json, '$.fields')=2
      """, Integer.class, sessionId, requestId, Long.parseLong(approved.path("consent").path("id").asText()), owner, f.tenant())).isOne();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE action='SUPPORT_SESSION_RESERVE' AND object_id=?",
      Integer.class, sessionId)).isOne();
  String browser = "A".repeat(43);
  String entry;
  long revokedSession;
  String revokedEntry;
  try (var auth = AuthorizationContext.open(permissions); var actor = TenantContext.open(0, owner)) {
   entry = supportSessions.issueEntry(sessionId, browser);
   assertThatThrownBy(() -> supportSessions.issueEntry(sessionId, browser)).hasMessageContaining("不可用");
   assertThatThrownBy(() -> supportSessions.exchangeEntry(sessionId, entry, "B".repeat(43))).hasMessageContaining("不可用");
   revokedSession = supportSessions.createPending(requestId);
   revokedEntry = supportSessions.issueEntry(revokedSession, browser);
   long expiredSession = supportSessions.createPending(requestId);
   String expired = supportSessions.issueEntry(expiredSession, browser);
   jdbc.update("UPDATE plt_support_session SET entry_expires_at=UTC_TIMESTAMP(3) WHERE id=?", expiredSession);
   assertThatThrownBy(() -> supportSessions.exchangeEntry(expiredSession, expired, browser)).hasMessageContaining("不可用");
  }
  assertThat(jdbc.queryForObject("SELECT entry_digest FROM plt_support_session WHERE id=?", String.class, sessionId))
      .hasSize(64).isNotEqualTo(entry);
  var start = new CountDownLatch(1);
  var workers = Executors.newFixedThreadPool(2);
  Callable<String> redeem = () -> {
   start.await();
   try (var auth = AuthorizationContext.open(permissions); var actor = TenantContext.open(0, owner)) {
    return supportSessions.exchangeEntry(sessionId, entry, browser);
   } catch (io.github.turbopro.ism.common.api.error.ApiException e) {
    assertThat(e).hasMessageContaining("不可用");
    return null;
   }
  };
  String accessToken;
  try {
   var first = workers.submit(redeem);
   var second = workers.submit(redeem);
   start.countDown();
   String firstResult = first.get(30, TimeUnit.SECONDS);
   String secondResult = second.get(30, TimeUnit.SECONDS);
   assertThat((firstResult == null) != (secondResult == null)).isTrue();
   accessToken = firstResult == null ? secondResult : firstResult;
  } finally {
   workers.shutdownNow();
  }
  assertThat(jdbc.queryForObject("""
      SELECT COUNT(*) FROM plt_support_session WHERE id=? AND status='ACTIVE'
        AND entry_digest IS NULL AND browser_digest IS NULL AND activated_at IS NOT NULL
      """, Integer.class, sessionId)).isOne();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE action='SUPPORT_SESSION_ACTIVATE' AND object_id=?",
      Integer.class, sessionId)).isOne();
  var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
  transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
  long visibleSupplier = f.tenant() + 95000;
  long hiddenSupplier = visibleSupplier + 1;
  jdbc.update("""
      INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,
        supplier_type,remark,created_by,updated_by)
      VALUES (?,?,?,'SUP-VISIBLE','授权供应商','MANUFACTURER','PRIVATE_REMARK',?,?),
             (?,?,?,'SUP-HIDDEN','范围外供应商','MANUFACTURER','PRIVATE_REMARK',?,?)
      """, visibleSupplier, f.tenant(), f.first(), f.admin(), f.admin(),
      hiddenSupplier, f.tenant(), f.headquarters(), f.admin(), f.admin());
  grant(f, roleId, "TENANT_ALL", Set.of(), true);
  try (var auth = AuthorizationContext.open(permissions); var actor = TenantContext.open(0, owner)) {
   var scope = transaction.execute(status -> supportSessions.requireAccess(sessionId, f.tenant(), accessToken));
   assertThat(supportReads.read(sessionId, f.tenant(), accessToken, visibleSupplier))
       .containsExactlyInAnyOrderEntriesOf(Map.of("code", "SUP-VISIBLE", "name", "授权供应商"));
   assertThat(supportReads.read(sessionId, f.tenant(), accessToken, hiddenSupplier)).isEmpty();
   assertThat(scope.tenantAll()).isFalse();
   assertThat(scope.organizations()).containsExactlyInAnyOrder(Long.toString(f.first()), Long.toString(f.department()));
   assertThatThrownBy(() -> transaction.execute(status -> supportSessions.requireAccess(sessionId, f.tenant() + 1, accessToken)))
       .hasMessageContaining("不可用");
   assertThatThrownBy(() -> transaction.execute(status -> supportSessions.requireAccess(sessionId, f.tenant(), browser)))
       .hasMessageContaining("不可用");
  }
  grant(f, roleId, "ORGANIZATION_SET", Set.of(Long.toString(f.department())), true);
  try (var auth = AuthorizationContext.open(permissions); var actor = TenantContext.open(0, owner)) {
   assertThat(transaction.execute(status -> supportSessions.requireAccess(sessionId, f.tenant(), accessToken)).organizations())
       .containsExactly(Long.toString(f.department()));
   assertThat(supportReads.read(sessionId, f.tenant(), accessToken, visibleSupplier)).isEmpty();
   supportSessions.revoke(sessionId);
   assertThatThrownBy(() -> supportReads.read(sessionId, f.tenant(), accessToken, visibleSupplier))
       .hasMessageContaining("不可用");
   assertThatThrownBy(() -> transaction.execute(status -> supportSessions.requireAccess(sessionId, f.tenant(), accessToken)))
       .hasMessageContaining("不可用");
  }
  assertThat(jdbc.queryForList("""
      SELECT JSON_UNQUOTE(JSON_EXTRACT(after_summary, '$.result'))
      FROM sys_audit_event
      WHERE tenant_id=0 AND operator_id=? AND action='SUPPORT_SUPPLIER_READ'
        AND trace_id IS NOT NULL AND JSON_UNQUOTE(JSON_EXTRACT(after_summary, '$.sessionId'))=?
        AND JSON_UNQUOTE(JSON_EXTRACT(after_summary, '$.targetTenantId'))=?
      """, String.class, owner, Long.toString(sessionId), Long.toString(f.tenant())))
      .containsExactlyInAnyOrder("READ", "NOT_VISIBLE", "NOT_VISIBLE");
  ok(decide(tenantToken, id, "revoke", "reserve-revoke-" + id, decision(detail(tenantToken, id))));
  try (var auth = AuthorizationContext.open(permissions); var actor = TenantContext.open(0, owner)) {
   assertThatThrownBy(() -> supportSessions.createPending(requestId)).hasMessageContaining("支持授权当前不可用");
   assertThatThrownBy(() -> supportSessions.exchangeEntry(revokedSession, revokedEntry, browser)).hasMessageContaining("不可用");
   assertThatThrownBy(() -> transaction.execute(status -> supportSessions.requireAccess(sessionId, f.tenant(), accessToken)))
       .hasMessageContaining("不可用");
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plt_support_session WHERE request_id=? AND status='REVOKED' AND revoked_at IS NOT NULL AND access_digest IS NULL",
      Integer.class, requestId)).isOne();
 }
}

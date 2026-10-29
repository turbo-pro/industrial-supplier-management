package io.github.turbopro.ism.bootstrap;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SiteContextIT extends SitePreferenceSupport {
    ResponseEntity<JsonNode> issue(String bearer,String site){var body=new HashMap<String,Object>();body.put("siteId",site);return request("POST","/api/site-context",bearer,null,body);}
    ResponseEntity<JsonNode> use(String bearer,String context,String path){var headers=new HttpHeaders();headers.setBearerAuth(bearer);headers.set("X-Site-Context",context);return http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);}
    String token(ResponseEntity<JsonNode> response){assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);return response.getBody().path("data").path("contextToken").asText();}
    @Test void explicitPreferredAndUniqueChoicesAreServerValidated(){
        var f=fixture();String admin=tenantLogin(f,"pref-admin");
        assertThat(issue(admin,null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        var explicit=issue(admin,Long.toString(f.second()));String context=token(explicit);
        assertThat(explicit.getBody().path("data").path("selectionReason").asText()).isEqualTo("EXPLICIT");
        assertThat(use(admin,context,"/api/site-context").getBody().path("data").path("site").path("id").asText()).isEqualTo(Long.toString(f.second()));
        assertThat(save(admin,"preferred",f.first(),0).getStatusCode()).isEqualTo(HttpStatus.OK);
        var preferred=issue(admin,null);assertThat(preferred.getBody().path("data").path("selectionReason").asText()).isEqualTo("PREFERRED");
        assertThat(preferred.getBody().path("data").path("site").path("id").asText()).isEqualTo(Long.toString(f.first()));
        var unique=issue(tenantLogin(f,"pref-reader"),null);token(unique);assertThat(unique.getBody().path("data").path("selectionReason").asText()).isEqualTo("ONLY_SITE");
        assertThat(request("GET","/api/site-context",admin,null,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request("GET","/api/organizations/current",admin,null,null).getBody().path("data").path("id").asText()).isEqualTo(Long.toString(f.headquarters()));
    }
    @Test void contextIsNotBearerAndCannotCrossLoginUserOrTenant(){
        var f=fixture();String admin=tenantLogin(f,"pref-admin"),context=token(issue(admin,Long.toString(f.first())));
        String later=tenantLogin(f,"pref-admin");assertThat(use(later,context,"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(use(tenantLogin(f,"pref-reader"),context,"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var other=fixture();assertThat(use(tenantLogin(other,"pref-admin"),context,"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request("GET","/api/organizations/current",context,null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(use(later,later,"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String current=token(issue(later,Long.toString(f.first())));assertThat(use(later,current.substring(0,current.length()-8)+"tampered","/api/site-context").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(use(later,current,"/api/organizations/current").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
    @Test void scopeMembershipAndAncestorRevocationAreRechecked(){
        var f=fixture();String admin=tenantLogin(f,"pref-admin"),context=token(issue(admin,Long.toString(f.first())));
        narrow(f,"ORGANIZATION_SET",f.second());assertThat(use(admin,context,"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        narrow(f,"TENANT_ALL",0);assertThat(use(admin,context,"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM iam_user_organization WHERE tenant_id=? AND user_id=?",f.tenant(),f.admin());assertThat(use(admin,context,"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var second=fixture();String t=tenantLogin(second,"pref-admin"),c=token(issue(t,Long.toString(second.first())));
        jdbc.update("UPDATE iam_organization SET status='DISABLED' WHERE tenant_id=? AND id=?",second.tenant(),second.headquarters());assertThat(use(t,c,"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void actionRevocationAndAmbiguousHeadersAreRejected(){
        var f=fixture();String t=tenantLogin(f,"pref-admin"),c=token(issue(t,Long.toString(f.first())));
        var headers=new HttpHeaders();headers.setBearerAuth(t);headers.add("X-Site-Context",c);headers.add("X-Site-Context",c);
        assertThat(http.exchange("/api/site-context",HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(use(t,"x".repeat(4097),"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String console=login(true);assertThat(use(console,c,"/api/console/tenants").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(request("GET","/api/console/tenants",console,null,null).getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM iam_role_permission WHERE tenant_id=? AND role_id=? AND permission_id=3070",f.tenant(),f.role());
        assertThat(use(t,c,"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(issue(t,Long.toString(f.first())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void invalidPreferredChoiceDoesNotSilentlyChooseOtherSite(){var f=fixture();String t=tenantLogin(f,"pref-admin");assertThat(save(t,"set",f.first(),0).getStatusCode()).isEqualTo(HttpStatus.OK);narrow(f,"ORGANIZATION_SET",f.second());assertThat(issue(t,null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(issue(t,Long.toString(f.second())).getStatusCode()).isEqualTo(HttpStatus.OK);}
    @Test void logoutAndAuditFailureDoNotDeliverUsableContexts()throws Exception{
        var f=fixture();var login=http.postForEntity("/api/auth/login",Map.of("tenantCode",f.code(),"username","pref-admin","password","Site#Initial123","deviceId","logout-context"),JsonNode.class).getBody().path("data");
        String bearer=login.path("accessToken").asText(),context=token(issue(bearer,Long.toString(f.first())));
        assertThat(request("POST","/api/auth/logout",bearer,null,Map.of("refreshToken",login.path("refreshToken").asText())).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(use(bearer,context,"/api/site-context").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String active=tenantLogin(f,"pref-admin");trigger("CREATE TRIGGER site_context_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.action='SITE_CONTEXT_ISSUE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic context audit failure'; END IF; END");
        try{var failed=issue(active,Long.toString(f.first()));assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);assertThat(failed.getBody().toString()).doesNotContain("contextToken");}finally{trigger("DROP TRIGGER site_context_audit_failure");}
        var issued=issue(active,Long.toString(f.first()));String value=token(issued);
        var audits=jdbc.queryForList("SELECT after_summary FROM sys_audit_event WHERE tenant_id=? AND action='SITE_CONTEXT_ISSUE'",String.class,f.tenant());assertThat(audits.toString()).doesNotContain(value,active,"contextToken","ath");
    }
}

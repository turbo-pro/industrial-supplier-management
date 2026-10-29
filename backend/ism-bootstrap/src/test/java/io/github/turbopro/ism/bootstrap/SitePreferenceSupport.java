package io.github.turbopro.ism.bootstrap;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.iam.organization.*;
import io.github.turbopro.ism.iam.access.*;
import io.github.turbopro.ism.platform.tenant.TenantModels;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
abstract class SitePreferenceSupport extends SiteInitializationSupport {
    @Autowired OrganizationService organizations;@Autowired AccessService access;
    record Fixture(long tenant,String code,long admin,long headquarters,long first,long second,long department,long role,String reader){}
    Fixture fixture(){
        long id=pending();tenants.initialize(id,new TenantModels.InitializeTenant("pref-admin","首选场站管理员","Site#Initial123","首选总部","MULTI_SITE","工厂","第一工厂"));
        long admin=jdbc.queryForObject("SELECT id FROM iam_user WHERE tenant_id=?",Long.class,id),hq=jdbc.queryForObject("SELECT organization_id FROM iam_user_organization WHERE tenant_id=? AND user_id=?",Long.class,id,admin);
        long first=jdbc.queryForObject("SELECT default_site_id FROM plt_tenant WHERE id=?",Long.class,id),second,department;String reader;
        try(var context=TenantContext.open(id,admin)){
            second=Long.parseLong(organizations.create(new OrganizationModels.CreateOrganization(Long.toString(hq),"SECOND-SITE","第二工厂","SITE",1)).id());
            department=Long.parseLong(organizations.create(new OrganizationModels.CreateOrganization(Long.toString(first),"WORKSHOP","第一车间","DEPARTMENT",0)).id());
            var role=access.createRole(new AccessModels.CreateRole("PREF_READER","首选场站只读"));
            access.grantRole(Long.parseLong(role.id()),new AccessModels.GrantRole(role.version(),Set.of("iam:site:view","iam:menu:view"),Set.of("SYSTEM_MANAGEMENT","SITE_PREFERENCE"),List.of(new AccessModels.DataScopeGrant("iam:site","TENANT_ALL",Set.of()))));
            reader=access.createUser(new AccessModels.CreateUser("pref-reader","只读人员","Site#Initial123",Long.toString(department),Set.of(role.id()))).id();
        }
        jdbc.update("UPDATE iam_user SET force_password_change=0 WHERE tenant_id=?",id);
        long role=jdbc.queryForObject("SELECT id FROM iam_role WHERE tenant_id=? AND role_code='TENANT_ADMIN'",Long.class,id);
        String code=jdbc.queryForObject("SELECT tenant_code FROM plt_tenant WHERE id=?",String.class,id);
        return new Fixture(id,code,admin,hq,first,second,department,role,reader);
    }
    String tenantLogin(Fixture f,String user){var response=http.postForEntity("/api/auth/login",Map.of("tenantCode",f.code(),"username",user,"password","Site#Initial123","deviceId","site-preference"),JsonNode.class);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);return response.getBody().path("data").path("accessToken").asText();}
    ResponseEntity<JsonNode> request(String method,String path,String token,String key,Object body){var h=new HttpHeaders();h.setBearerAuth(token);if(key!=null)h.set("Idempotency-Key",key);return http.exchange(path,HttpMethod.valueOf(method),new HttpEntity<>(body,h),JsonNode.class);}
    ResponseEntity<JsonNode> save(String token,String key,Long site,int version){var body=new HashMap<String,Object>();body.put("siteId",site==null?null:site.toString());body.put("version",version);return request("PUT","/api/site-preferences",token,key,body);}
    void narrow(Fixture f,String type,long org){jdbc.update("UPDATE iam_role_data_scope SET scope_type=? WHERE tenant_id=? AND role_id=? AND resource_code='iam:site'",type,f.tenant(),f.role());jdbc.update("DELETE FROM iam_role_data_scope_organization WHERE tenant_id=? AND role_id=? AND resource_code='iam:site'",f.tenant(),f.role());if("ORGANIZATION_SET".equals(type))jdbc.update("INSERT INTO iam_role_data_scope_organization(tenant_id,role_id,resource_code,organization_id) VALUES(?,?,'iam:site',?)",f.tenant(),f.role(),org);}
    void trigger(String sql)throws Exception{try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.createStatement()){statement.execute(sql);}}
}

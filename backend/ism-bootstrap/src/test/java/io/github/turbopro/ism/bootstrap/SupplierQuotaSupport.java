package io.github.turbopro.ism.bootstrap;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.platform.packageplan.PackagePlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
abstract class SupplierQuotaSupport extends SiteProjectSupport {
    @Override long pending(){long id=super.pending();jdbc.update("INSERT INTO plt_package_module(package_version_id,module_id,enabled,quota_json) SELECT t.package_version_id,m.id,1,JSON_OBJECT() FROM plt_tenant t CROSS JOIN plt_module m WHERE t.id=? AND m.module_code='RESTRICTION'",id);return id;}
    String scopedWriter(Fixture f){
        try(var scope=io.github.turbopro.ism.common.infrastructure.tenant.TenantContext.open(f.tenant(),f.admin())){
            var role=access.createRole(new io.github.turbopro.ism.iam.access.AccessModels.CreateRole("QUOTA_SCOPED","额度范围操作员"));
            access.grantRole(Long.parseLong(role.id()),new io.github.turbopro.ism.iam.access.AccessModels.GrantRole(role.version(),Set.of("supplier:master:view","supplier:master:create"),Set.of("SUPPLIER_MASTER"),List.of(new io.github.turbopro.ism.iam.access.AccessModels.DataScopeGrant("supplier:master","ORGANIZATION_SET",Set.of(Long.toString(f.first()))))));
            access.createUser(new io.github.turbopro.ism.iam.access.AccessModels.CreateUser("quota-scoped","额度范围操作员","Site#Initial123",Long.toString(f.first()),Set.of(role.id())));
        }
        jdbc.update("UPDATE iam_user SET force_password_change=0 WHERE tenant_id=? AND username='quota-scoped'",f.tenant());return tenantLogin(f,"quota-scoped");
    }
    @Autowired PackagePlanService plans;
    void limit(Fixture f,long n){jdbc.update("UPDATE plt_package_module pm JOIN plt_tenant t ON t.package_version_id=pm.package_version_id JOIN plt_module m ON m.id=pm.module_id SET pm.quota_json=JSON_OBJECT('SUPPLIER_COUNT',?) WHERE t.id=? AND m.module_code='SUPPLIER'",n,f.tenant());}
    Map<String,Object> supplier(Fixture f,String code){return Map.of("code",code,"name","额度供应商"+code,"type","MANUFACTURER","countryCode","CN","riskLevel","LOW","organizationId",Long.toString(f.headquarters()),"contacts",List.of(),"version",0);}
    ResponseEntity<JsonNode> create(Fixture f,String token,String code){return request("POST","/api/suppliers",token,null,supplier(f,code));}
    JsonNode ok(ResponseEntity<JsonNode> r){assertThat(r.getStatusCode()).as("%s",r.getBody()).isEqualTo(HttpStatus.OK);return r.getBody().path("data");}
    JsonNode usage(Fixture f){return ok(request("GET","/api/console/tenants/"+f.tenant()+"/quota-usage?quotaCode=SUPPLIER_COUNT",login(true),null,null));}
    void seed(Fixture f,long offset,long org,String state,boolean deleted){jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,status,deleted,created_by,updated_by) VALUES(?,?,?,?,?,'MANUFACTURER',?,?,?,?)",f.tenant()+offset,f.tenant(),org,"SEED-"+offset,"保留档案"+offset,state,deleted?1:0,f.admin(),f.admin());}
}

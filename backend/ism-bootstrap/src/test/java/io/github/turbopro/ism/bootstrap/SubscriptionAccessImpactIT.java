package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.platform.packageplan.PackagePlanModels;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SubscriptionAccessImpactIT extends SubscriptionAssignmentIT {
    String target(Fixture f,int number,boolean supplier){
        long p=jdbc.queryForObject("SELECT v.package_id FROM plt_package_version v JOIN plt_tenant t ON t.package_version_id=v.id WHERE t.id=?",Long.class,f.tenant());
        List<PackagePlanModels.ModuleGrant> grants=new ArrayList<>();grants.add(new PackagePlanModels.ModuleGrant("PROJECT",true,Map.of()));
        if(supplier)grants.add(new PackagePlanModels.ModuleGrant("SUPPLIER",true,Map.of("SUPPLIER_COUNT",10L)));
        var v=plans.createVersion(p,new PackagePlanModels.CreateVersion(number,"授权影响"+number,null,grants));plans.publish(Long.parseLong(v.id()),0,790001);return v.id();
    }
    JsonNode impact(Fixture f,String version){return preview(f,version,login(true)).path("authorizationImpact");}
    JsonNode menus(String token){return ok(request("GET","/api/navigation/menus",token,null,null));}
    @Test void downgradeRestorationUsesLiveJwtAndPreservesOriginalRoleBindings(){
        var f=fixture();String user=tenantLogin(f,"pref-admin"),reader=tenantLogin(f,"pref-reader"),console=login(true);
        String down=target(f,2,false),up=target(f,3,true);
        int roles=count("iam_role_menu",f.tenant()),permissions=count("iam_role_permission",f.tenant()),orgs=count("iam_organization",f.tenant());
        assertThat(menus(user).toString()).contains("SUPPLIER_MASTER");
        var before=preview(f,down,console);var lost=before.path("authorizationImpact");
        assertThat(lost.path("removed").path("menus").asLong()).isPositive();assertThat(lost.path("removed").path("actions").asLong()).isPositive();
        assertThat(lost.path("items").findValuesAsText("code")).contains("SUPPLIER_MASTER","supplier:master:view");
        assertThat(lost.toString()).doesNotContain("pref-admin","pref-reader","password","quota-scoped");
        ok(assign(f,console,"impact-down-"+f.tenant(),body(down,before,false)));
        assertThat(menus(user).toString()).doesNotContain("SUPPLIER_MASTER").contains("SYSTEM_MANAGEMENT");
        assertThat(request("GET","/api/suppliers",user,null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var restore=preview(f,up,console);assertThat(restore.path("authorizationImpact").path("added").path("menus").asLong()).isPositive();
        ok(assign(f,console,"impact-up-"+f.tenant(),body(up,restore,false)));
        assertThat(menus(user).toString()).contains("SUPPLIER_MASTER");assertThat(request("GET","/api/suppliers",user,null,null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(menus(reader).toString()).doesNotContain("SUPPLIER_MASTER");assertThat(request("GET","/api/suppliers",reader,null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(count("iam_role_menu",f.tenant())).isEqualTo(roles);assertThat(count("iam_role_permission",f.tenant())).isEqualTo(permissions);assertThat(count("iam_organization",f.tenant())).isEqualTo(orgs);
    }
    @Test void unchangedAndCoreModulesDoNotGenerateFalseLosses(){var f=fixture();String original=jdbc.queryForObject("SELECT package_version_id FROM plt_tenant WHERE id=?",String.class,f.tenant());var i=impact(f,original);assertThat(i.path("total").asLong()).isZero();assertThat(i.path("items")).isEmpty();var removed=impact(f,target(f,2,false));for(var row:removed.path("items"))assertThat(row.path("moduleCode").asText()).isNotIn("SYSTEM","COMMON","MESSAGE","WORKFLOW");}
    @Test void inactiveCatalogRowsAreExcludedAndFieldPermissionsRemainDistinct(){var f=fixture();String down=target(f,2,false);jdbc.update("INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status,module_code) VALUES(990001,'test:inactive','不应展示的已停用项','ACTION','DISABLED','SUPPLIER'),(990002,'test:field','受限字段目录','FIELD','ACTIVE','SUPPLIER')");try{var i=impact(f,down);assertThat(i.path("items").findValuesAsText("code")).contains("test:field").doesNotContain("test:inactive");assertThat(i.path("removed").path("fields").asLong()).isPositive();}finally{jdbc.update("DELETE FROM sys_permission WHERE id IN(990001,990002)");}}
    @Test void boundedDetailsKeepFullCountsAndDeterministicOrdering(){var f=fixture();String down=target(f,2,false);for(int n=0;n<215;n++)jdbc.update("INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status,module_code) VALUES(?,?,?,'ACTION','ACTIVE','SUPPLIER')",991000+n,"bounded:"+n,"边界目录"+n);try{var a=impact(f,down);var b=impact(f,down);assertThat(a.path("total").asLong()).isGreaterThan(200);assertThat(a.path("limit").asInt()).isEqualTo(200);assertThat(a.path("truncated").asBoolean()).isTrue();assertThat(a.path("items").size()).isEqualTo(200);assertThat(a).isEqualTo(b);}finally{jdbc.update("DELETE FROM sys_permission WHERE id BETWEEN 991000 AND 991214");}}
    @Test void consoleReadonlyAndTenantTokensCannotReadControlPlaneImpact(){var f=fixture();String down=target(f,2,false);for(String t:List.of(login(false),tenantLogin(f,"pref-admin"))){assertThat(request("POST","/api/console/tenants/"+f.tenant()+"/subscription-preview?packageVersionId="+down,t,null,null).getStatusCode().value()).isIn(401,403);}}
    @Test void globallyDisabledModuleIsNotReportedAsRestored(){var f=fixture();String up=target(f,2,true);jdbc.update("UPDATE plt_module SET status='DISABLED' WHERE module_code='SUPPLIER'");try{var i=impact(f,up);assertThat(i.path("items").findValuesAsText("moduleCode")).doesNotContain("SUPPLIER");}finally{jdbc.update("UPDATE plt_module SET status='ACTIVE' WHERE module_code='SUPPLIER'");}}
}

package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.iam.organization.*;
import io.github.turbopro.ism.platform.tenant.TenantModels;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class SiteUpgradeIT extends SiteInitializationSupport {
    @Autowired io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationGrantLoader grants;
    @Autowired OrganizationService organizations;@Autowired OperationIdGenerator ids;
    record Ready(long tenant,long site,long admin,long department,long leaf,long project){}
    Ready ready(){
        long id=pending();tenants.initialize(id,new TenantModels.InitializeTenant("site-admin","原管理员","Site#Initial123",null,"SINGLE_SITE","工厂","原工厂"));
        long site=jdbc.queryForObject("SELECT default_site_id FROM plt_tenant WHERE id=?",Long.class,id);
        long admin=jdbc.queryForObject("SELECT id FROM iam_user WHERE tenant_id=?",Long.class,id);
        long department,leaf;
        try(var context=TenantContext.open(id,admin)){
            department=Long.parseLong(organizations.create(new OrganizationModels.CreateOrganization(Long.toString(site),"OLD-DEPT","原部门","DEPARTMENT",0)).id());
            leaf=Long.parseLong(organizations.create(new OrganizationModels.CreateOrganization(Long.toString(department),"OLD-LEAF","原班组","DEPARTMENT",0)).id());
        }
        long supplier=ids.nextId(),project=ids.nextId();
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(?,?,?,'OLD-SUP','原供应商','MANUFACTURER',?,?)",supplier,id,site,admin,admin);
        jdbc.update("INSERT INTO prj_project(id,tenant_id,organization_id,supplier_id,project_code,project_name,project_type,planned_start_date,planned_end_date,manager_id,created_by,updated_by) VALUES(?,?,?,?,'OLD-PROJECT','历史项目','CONSTRUCTION','2026-01-01','2026-12-31',?,?,?)",project,id,site,supplier,admin,admin,admin);
        return new Ready(id,site,admin,department,leaf,project);
    }
    List<Object> history(Ready r){return List.of(jdbc.queryForList("SELECT * FROM prj_project WHERE tenant_id=? ORDER BY id",r.tenant()),jdbc.queryForList("SELECT * FROM sup_supplier WHERE tenant_id=? ORDER BY id",r.tenant()),jdbc.queryForList("SELECT * FROM iam_user WHERE tenant_id=? ORDER BY id",r.tenant()),jdbc.queryForList("SELECT * FROM iam_user_organization WHERE tenant_id=? ORDER BY user_id,organization_id",r.tenant()),jdbc.queryForList("SELECT * FROM iam_user_context WHERE tenant_id=? ORDER BY user_id",r.tenant()),jdbc.queryForList("SELECT * FROM iam_user_role WHERE tenant_id=? ORDER BY user_id,role_id",r.tenant()),jdbc.queryForList("SELECT * FROM iam_role_data_scope WHERE tenant_id=? ORDER BY role_id,resource_code",r.tenant()),jdbc.queryForList("SELECT * FROM iam_role_data_scope_organization WHERE tenant_id=? ORDER BY role_id,resource_code,organization_id",r.tenant()));}
    ResponseEntity<JsonNode> upgrade(long id,String token,String key,int version){return upgradeBody(id,token,key,Map.of("headquartersName","新增总部","version",version));}
    ResponseEntity<JsonNode> upgradeBody(long id,String token,String key,Object body){var headers=new HttpHeaders();headers.setBearerAuth(token);headers.set("Idempotency-Key",key);return http.exchange("/api/console/tenants/"+id+"/upgrade-to-multi-site",HttpMethod.POST,new HttpEntity<>(body,headers),JsonNode.class);}
    long headquarters(Ready r){return jdbc.queryForObject("SELECT id FROM iam_organization WHERE tenant_id=? AND parent_id IS NULL AND organization_type='HEADQUARTERS'",Long.class,r.tenant());}
    void assertUpgraded(Ready r){
        assertThat(jdbc.queryForObject("SELECT operation_mode FROM plt_tenant WHERE id=?",String.class,r.tenant())).isEqualTo("MULTI_SITE");
        assertThat(jdbc.queryForObject("SELECT default_site_id FROM plt_tenant WHERE id=?",Long.class,r.tenant())).isEqualTo(r.site());
        assertThat(jdbc.queryForObject("SELECT site_label FROM plt_tenant WHERE id=?",String.class,r.tenant())).isEqualTo("工厂");
        long hq=headquarters(r);
        assertThat(jdbc.queryForObject("SELECT parent_id FROM iam_organization WHERE tenant_id=? AND id=?",Long.class,r.tenant(),r.site())).isEqualTo(hq);
        assertThat(jdbc.queryForObject("SELECT organization_code FROM iam_organization WHERE tenant_id=? AND id=?",String.class,r.tenant(),r.site())).isEqualTo("MAIN_SITE");
        assertThat(jdbc.queryForObject("SELECT depth FROM iam_organization_closure WHERE tenant_id=? AND ancestor_id=? AND descendant_id=?",Integer.class,r.tenant(),hq,r.leaf())).isEqualTo(3);
        assertThat(count("iam_organization_closure",r.tenant())).isEqualTo(10);
    }
    @Test void preservesHistoricalOwnershipAndReplaysOnce(){var r=ready();var before=history(r);String token=login(true);var response=upgrade(r.tenant(),token,"preserve",1);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);assertUpgraded(r);assertThat(history(r)).isEqualTo(before);assertThat(upgrade(r.tenant(),token,"preserve",1).getBody().path("data")).isEqualTo(response.getBody().path("data"));assertThat(count("iam_organization",r.tenant())).isEqualTo(4);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE action='TENANT_SITE_UPGRADE' AND object_id=?",Integer.class,r.tenant())).isOne();}
    @Test void rejectsUnauthorizedStaleAndInvalidRequests(){var r=ready();String token=login(true);assertThat(upgrade(r.tenant(),login(false),"denied",1).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);assertThat(upgrade(r.tenant(),token,"stale",0).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(upgradeBody(r.tenant(),token,"blank",Map.of("version",1,"headquartersName","")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);assertThat(upgradeBody(r.tenant(),token,"missing-version",Map.of("headquartersName","总部")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);assertThat(count("iam_organization",r.tenant())).isEqualTo(3);}
    @Test void blocksLegacyMultiSuspendedAndForeignStructures(){String token=login(true);long legacy=pending();tenants.initialize(legacy,new TenantModels.InitializeTenant("old-admin","旧管理员","Site#Initial123","旧总部"));assertThat(upgrade(legacy,token,"legacy",1).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);long multi=pending();tenants.initialize(multi,new TenantModels.InitializeTenant("multi-admin","管理员","Site#Initial123","原总部","MULTI_SITE","工厂","原工厂"));assertThat(upgrade(multi,token,"multi",1).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);var suspended=ready();tenants.suspend(suspended.tenant());assertThat(upgrade(suspended.tenant(),token,"suspended",2).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);var owner=ready();var foreign=ready();var before=history(foreign);jdbc.update("UPDATE plt_tenant SET default_site_id=? WHERE id=?",foreign.site(),owner.tenant());assertThat(upgrade(owner.tenant(),token,"foreign",1).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(history(foreign)).isEqualTo(before);assertThat(count("iam_organization",owner.tenant())).isEqualTo(3);}
    @Test void refusesBrokenClosureWithoutRepair(){var r=ready();jdbc.update("DELETE FROM iam_organization_closure WHERE tenant_id=? AND ancestor_id=? AND descendant_id=?",r.tenant(),r.site(),r.leaf());assertThat(upgrade(r.tenant(),login(true),"broken",1).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(count("iam_organization_closure",r.tenant())).isEqualTo(5);assertThat(count("iam_organization",r.tenant())).isEqualTo(3);}
    @Test void auditFailureRollsBackAndOriginalRequestRetries()throws Exception{var r=ready();var before=history(r);String token=login(true);trigger("CREATE TRIGGER site_upgrade_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.action='TENANT_SITE_UPGRADE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic upgrade audit failure'; END IF; END");try{assertThat(upgrade(r.tenant(),token,"rollback",1).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);assertThat(jdbc.queryForObject("SELECT operation_mode FROM plt_tenant WHERE id=?",String.class,r.tenant())).isEqualTo("SINGLE_SITE");assertThat(jdbc.queryForObject("SELECT parent_id FROM iam_organization WHERE id=?",Long.class,r.site())).isNull();assertThat(count("iam_organization",r.tenant())).isEqualTo(3);assertThat(history(r)).isEqualTo(before);}finally{trigger("DROP TRIGGER site_upgrade_audit_failure");}assertThat(upgrade(r.tenant(),token,"rollback",1).getStatusCode()).isEqualTo(HttpStatus.OK);assertUpgraded(r);}
    @Test void competingVersionsCreateOnlyOneHeadquarters()throws Exception{var r=ready();String token=login(true);var first=CompletableFuture.supplyAsync(()->upgrade(r.tenant(),token,"race-one",1));var second=CompletableFuture.supplyAsync(()->upgrade(r.tenant(),token,"race-two",1));assertThat(List.of(first.get(30,TimeUnit.SECONDS).getStatusCode(),second.get(30,TimeUnit.SECONDS).getStatusCode())).containsExactlyInAnyOrder(HttpStatus.OK,HttpStatus.CONFLICT);assertUpgraded(r);}
    @Test void creationAndUpgradeWaitForSharedStructureLock()throws Exception{
        var r=ready();String token=login(true);CompletableFuture<ResponseEntity<JsonNode>> upgrade;CompletableFuture<String> create;
        try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword())){
            connection.setAutoCommit(false);try(var statement=connection.createStatement()){
                statement.executeQuery("SELECT id FROM iam_tenant WHERE id="+r.tenant()+" FOR UPDATE").close();
                upgrade=CompletableFuture.supplyAsync(()->upgrade(r.tenant(),token,"create-race",1));
                create=CompletableFuture.supplyAsync(()->{try(var context=TenantContext.open(r.tenant(),r.admin())){return organizations.create(new OrganizationModels.CreateOrganization(Long.toString(r.site()),"NEW-DEPT","并发新部门","DEPARTMENT",1)).id();}});
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);long waits=0;
                while(waits<2&&System.nanoTime()<deadline){try(var result=statement.executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits")){result.next();waits=result.getLong(1);}if(waits<2)Thread.sleep(20);}
                assertThat(waits).as("Both writers must actually wait for the same structural root").isGreaterThanOrEqualTo(2);
            }finally{connection.commit();}
        }
        assertThat(upgrade.get(30,TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);long department=Long.parseLong(create.get(30,TimeUnit.SECONDS));
        assertThat(jdbc.queryForObject("SELECT depth FROM iam_organization_closure WHERE tenant_id=? AND ancestor_id=? AND descendant_id=?",Integer.class,r.tenant(),headquarters(r),department)).isEqualTo(2);
    }
    @Test void originalSiteGrantDoesNotExpandToNewHeadquartersOrSibling(){
        var r=ready();long role=jdbc.queryForObject("SELECT role_id FROM iam_user_role WHERE tenant_id=? AND user_id=?",Long.class,r.tenant(),r.admin());
        jdbc.update("UPDATE iam_role_data_scope SET scope_type='ORGANIZATION_SET' WHERE tenant_id=? AND role_id=? AND resource_code='iam:user'",r.tenant(),role);
        jdbc.update("INSERT INTO iam_role_data_scope_organization(tenant_id,role_id,resource_code,organization_id) VALUES(?,?,'iam:user',?)",r.tenant(),role,r.site());
        try(var context=TenantContext.open(r.tenant(),r.admin())){assertThat(grants.load(r.tenant(),r.admin()).dataScope("iam:user").organizationIds()).containsExactlyInAnyOrder(r.site(),r.department(),r.leaf());}
        assertThat(upgrade(r.tenant(),login(true),"scope-preserved",1).getStatusCode()).isEqualTo(HttpStatus.OK);
        try(var context=TenantContext.open(r.tenant(),r.admin())){
            long hq=headquarters(r);long sibling=Long.parseLong(organizations.create(new OrganizationModels.CreateOrganization(Long.toString(hq),"SECOND-SITE","新场站","SITE",1)).id());
            var after=grants.load(r.tenant(),r.admin());
            assertThat(after.dataScope("iam:user").organizationIds()).containsExactlyInAnyOrder(r.site(),r.department(),r.leaf()).doesNotContain(hq,sibling);
            assertThat(after.dataScope("iam:organization").type()).isEqualTo(io.github.turbopro.ism.common.infrastructure.authorization.DataScope.Type.TENANT_ALL);
        }
    }
    void trigger(String sql)throws Exception{try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.createStatement()){statement.execute(sql);}}
}

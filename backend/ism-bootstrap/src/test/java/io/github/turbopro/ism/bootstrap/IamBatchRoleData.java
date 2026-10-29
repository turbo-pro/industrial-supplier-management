package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.iam.access.*;
import io.github.turbopro.ism.platform.packageplan.*;
import io.github.turbopro.ism.platform.tenant.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

final class IamBatchRoleData {
    static final String PASSWORD="Batch#Pass123";
    private static final AtomicInteger IDS=new AtomicInteger();
    record Fixture(long tenant,String code,long admin,String baseRole,String targetRole,String first,String second,String unchanged) {}
    static Fixture seed(JdbcTemplate jdbc,TenantService tenants,PackagePlanService plans,AccessService access) {
        int n=IDS.incrementAndGet();
        var plan=plans.create(new PackagePlanModels.CreatePackage("BATCH_ROLE_"+n,"批量角色验收"));
        var draft=plans.createVersion(Long.parseLong(plan.id()),new PackagePlanModels.CreateVersion(1,"验收版",LocalDateTime.now(ZoneOffset.UTC).minusDays(1),List.of(new PackagePlanModels.ModuleGrant("SUPPLIER",true,Map.of("SUPPLIER_COUNT",100L)))));
        var published=plans.publish(Long.parseLong(draft.id()),draft.version(),9001);
        String code="BATCH-ROLE-"+n;
        var tenant=tenants.create(new TenantModels.CreateTenant(code,"批量角色租户","Asia/Shanghai","zh-CN",published.id()));
        long id=Long.parseLong(tenant.id());
        tenants.initialize(id,new TenantModels.InitializeTenant("batch-admin","验收管理员",PASSWORD,"验收组织"));
        long admin=jdbc.queryForObject("SELECT id FROM iam_user WHERE tenant_id=? AND username='batch-admin'",Long.class,id);
        long org=jdbc.queryForObject("SELECT id FROM iam_organization WHERE tenant_id=? AND parent_id IS NULL",Long.class,id);
        try(var context=TenantContext.open(id,admin)) {
            var base=access.createRole(new AccessModels.CreateRole("BASE","原角色"));
            var target=access.createRole(new AccessModels.CreateRole("TARGET","目标角色"));
            access.grantRole(Long.parseLong(base.id()),new AccessModels.GrantRole(base.version(),Set.of("iam:user:view"),Set.of(),List.of()));
            access.grantRole(Long.parseLong(target.id()),new AccessModels.GrantRole(target.version(),Set.of("iam:user:view","iam:role:view"),Set.of(),List.of()));
            var first=access.createUser(new AccessModels.CreateUser("batch-one","账号一",PASSWORD,Long.toString(org),Set.of(base.id())));
            var second=access.createUser(new AccessModels.CreateUser("batch-two","账号二",PASSWORD,Long.toString(org),Set.of(base.id())));
            var unchanged=access.createUser(new AccessModels.CreateUser("batch-same","不变账号",PASSWORD,Long.toString(org),Set.of(target.id())));
            // Synthetic pre-provisioned test accounts; password-change behavior is covered separately.
            jdbc.update("UPDATE iam_user SET force_password_change=0 WHERE tenant_id=?",id);
            return new Fixture(id,code,admin,base.id(),target.id(),first.id(),second.id(),unchanged.id());
        }
    }
}

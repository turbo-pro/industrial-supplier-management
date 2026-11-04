package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.turbopro.ism.platform.packageplan.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class SupplierQuotaIT extends SupplierQuotaSupport {
    @Test void invalidQuotaReadsFailBeforeOpeningDomainContext(){String t=login(true);
        for(String id:List.of("-1","9223372036854775807"))assertThat(request("GET","/api/console/tenants/"+id+"/quota-usage?quotaCode=SUPPLIER_COUNT",t,null,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var f=fixture();assertThat(request("GET","/api/console/tenants/"+f.tenant()+"/quota-usage?quotaCode=bad-code",t,null,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void lastCapacityIsSerializedAndOnlySuccessfulCreationIsCounted()throws Exception{
        var f=fixture();limit(f,1);String token=tenantLogin(f,"pref-admin");var pool=Executors.newFixedThreadPool(2);try{var gate=new CountDownLatch(1);var one=pool.submit(()->{gate.await();return create(f,token,"RACE-A");});var two=pool.submit(()->{gate.await();return create(f,token,"RACE-B");});gate.countDown();var responses=List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS));assertThat(responses.stream().map(r->r.getStatusCode().value()).toList()).containsExactlyInAnyOrder(200,422);}finally{pool.shutdownNow();}
        assertThat(count("sup_supplier",f.tenant())).isOne();assertThat(jdbc.queryForObject("SELECT used_value FROM plt_quota_usage WHERE tenant_id=? AND quota_code='SUPPLIER_COUNT'",Long.class,f.tenant())).isOne();assertThat(usage(f).path("hardLimited").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_CREATE'",Integer.class,f.tenant())).isOne();
    }
    @Test void zeroMissingAndExpiredSubscriptionHaveNoCreationCapacity(){
        var f=fixture();String token=tenantLogin(f,"pref-admin");assertThat(create(f,tenantLogin(f,"pref-reader"),"READONLY").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);assertThat(create(f,token,"MISSING").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);limit(f,0);assertThat(create(f,token,"ZERO").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);limit(f,2);ok(create(f,token,"FIRST"));
        jdbc.update("UPDATE plt_module SET status='DISABLED' WHERE module_code='SUPPLIER'");try{assertThat(create(f,token,"DISABLED-MODULE").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);assertThat(usage(f).path("limit").asLong()).isZero();}finally{jdbc.update("UPDATE plt_module SET status='ACTIVE' WHERE module_code='SUPPLIER'");}
        jdbc.update("UPDATE plt_subscription SET effective_to=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE tenant_id=?",f.tenant());assertThat(create(f,token,"EXPIRED").getStatusCode().value()).isIn(403,422);assertThat(usage(f).path("limit").asLong()).isZero();assertThat(usage(f).path("used").asLong()).isOne();assertThat(count("sup_supplier",f.tenant())).isOne();
    }
    @Test void wholeTenantCountIncludesRetainedStatesAndIgnoresScopeAndStaleLedger(){
        var f=fixture();limit(f,3);seed(f,30001,f.second(),"DRAFT",false);seed(f,30002,f.second(),"SUSPENDED",false);seed(f,30003,f.second(),"EXITED",false);seed(f,30004,f.second(),"ACTIVE",true);
        String token=scopedWriter(f);assertThat(ok(request("GET","/api/suppliers",token,null,null)).path("total").asInt()).isZero();var scopedBody=new HashMap<>(supplier(f,"SCOPE-BYPASS"));scopedBody.put("organizationId",Long.toString(f.first()));assertThat(request("POST","/api/suppliers",token,null,scopedBody).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(usage(f).path("used").asLong()).isEqualTo(3);assertThat(usage(f).path("remaining").asLong()).isZero();
        var foreign=fixture();limit(foreign,1);ok(create(foreign,tenantLogin(foreign,"pref-admin"),"FOREIGN"));assertThat(usage(foreign).path("used").asLong()).isOne();assertThat(usage(f).path("used").asLong()).isEqualTo(3);
    }
    @Test void auditAndDuplicateFailureRollBackReservationAndAllowRetry()throws Exception{
        var f=fixture();limit(f,2);String token=tenantLogin(f,"pref-admin");trigger("CREATE TRIGGER sq_audit_fail BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.action='SUPPLIER_CREATE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test supplier quota rollback'; END IF; END");try{assertThat(create(f,token,"RETRY").getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);}finally{trigger("DROP TRIGGER sq_audit_fail");}
        assertThat(count("sup_supplier",f.tenant())).isZero();assertThat(count("plt_quota_usage",f.tenant())).isZero();ok(create(f,token,"RETRY"));assertThat(create(f,token,"RETRY").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(jdbc.queryForObject("SELECT used_value FROM plt_quota_usage WHERE tenant_id=?",Long.class,f.tenant())).isOne();ok(create(f,token,"SECOND"));assertThat(usage(f).path("used").asLong()).isEqualTo(2);
    }
    @Test void fullQuotaPreservesReadEditDisableAndExitApplication(){
        var f=fixture();limit(f,1);String token=tenantLogin(f,"pref-admin");var s=ok(create(f,token,"FULL"));String id=s.path("id").asText();ok(request("GET","/api/suppliers/"+id,token,null,null));var edit=new HashMap<>(supplier(f,"FULL"));edit.put("name","满额仍可编辑");ok(request("PUT","/api/suppliers/"+id,token,null,edit));var activated=ok(request("POST","/api/suppliers/"+id+"/status",token,null,Map.of("status","ACTIVE","version",1)));assertThat(activated.path("version").asInt()).isEqualTo(2);ok(request("POST","/api/suppliers/"+id+"/status",token,null,Map.of("status","SUSPENDED","reason","满额后停用","version",2)));
        assertThat(create(f,token,"NO-RELEASE").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);assertThat(usage(f).path("used").asLong()).isOne();ok(request("GET","/api/suppliers/"+id+"/exit-readiness",token,null,null));
        long file=f.tenant()+80001;jdbc.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(?,?,?,'quota-exit.pdf','application/pdf',1,?,'LOCAL',?,'ACTIVE')",file,f.tenant(),f.admin(),"a".repeat(64),"test/quota-exit-"+f.tenant());
        var exit=ok(request("POST","/api/suppliers/"+id+"/exit-applications",token,null,Map.of("type","NORMAL","reason","满额不阻断退出处置","evidenceFileId",Long.toString(file))));assertThat(exit.path("status").asText()).isEqualTo("SUBMITTED");
    }
    @Test void liveUsageBlocksDowngradeEvenWhenQuotaLedgerIsMissing(){
        var f=fixture();limit(f,10);seed(f,40001,f.headquarters(),"ACTIVE",false);seed(f,40002,f.headquarters(),"ACTIVE",false);long p=jdbc.queryForObject("SELECT package_id FROM plt_package_version v JOIN plt_tenant t ON t.package_version_id=v.id WHERE t.id=?",Long.class,f.tenant());var v=plans.createVersion(p,new PackagePlanModels.CreateVersion(2,"低容量",null,List.of(new PackagePlanModels.ModuleGrant("SUPPLIER",true,Map.of("SUPPLIER_COUNT",1L)))));plans.publish(Long.parseLong(v.id()),0,790001);
        try(var scope=io.github.turbopro.ism.common.infrastructure.tenant.TenantContext.open(0,790001)){assertThat(plans.preview(f.tenant(),Long.parseLong(v.id())).quotaChanges().get("SUPPLIER_COUNT").usedValue()).isEqualTo(2);assertThatThrownBy(()->plans.assign(f.tenant(),Long.parseLong(v.id()),new PackagePlanModels.AssignSubscription(v.id(),LocalDateTime.now(ZoneOffset.UTC),null,Map.of()))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);}
        assertThat(count("plt_quota_usage",f.tenant())).isZero();assertThat(usage(f).path("used").asLong()).isEqualTo(2);

    }
}

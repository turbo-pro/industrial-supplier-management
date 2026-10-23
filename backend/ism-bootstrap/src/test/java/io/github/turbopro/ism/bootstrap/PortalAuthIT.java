package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.supplier.*;
import io.github.turbopro.ism.iam.portal.*;
import io.github.turbopro.ism.iam.auth.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"ism.portal.registration-enabled=true","ism.portal.authentication-enabled=true","ism.portal.password-recovery-enabled=true","ism.print.worker-enabled=false","ism.subscription-reminders.enabled=false","ism.integration.finance.mode=MANUAL"})
@AutoConfigureMockMvc @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PortalAuthIT {
    static final java.nio.file.Path CERTIFICATE_STORAGE=certificateStorage();
    static java.nio.file.Path certificateStorage(){try{return java.nio.file.Files.createTempDirectory("ism-portal-certificates-");}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    @org.junit.jupiter.api.AfterAll static void cleanCertificateStorage()throws Exception{try(var paths=java.nio.file.Files.walk(CERTIFICATE_STORAGE)){for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);}}
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("portal_auth").withUsername("ism").withPassword("ism");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("ism.storage.local-root",()->CERTIFICATE_STORAGE.toString());}
    @Autowired JdbcTemplate jdbc;@Autowired PortalInvitationService invites;@Autowired PortalRegistrationService registrations;
    @Autowired PortalAuthService auth;@Autowired MockMvc mvc;@Autowired JwtTokenService internalTokens;
    @Autowired PortalAccountManagementService administration;
    @Autowired ExitApplicationService exits;
    @Autowired ExitArchiveService archives;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired PortalSessionRetentionService retention;
    @Autowired PortalSessionRetentionMapper retentionMapper;
    @Autowired PortalPasswordRecoveryService passwordRecovery;
    @Autowired io.github.turbopro.ism.integration.finance.ManualClearanceMapper finance;
    @Autowired ProfileChangeService changes;
    @Autowired io.github.turbopro.ism.resource.file.FileService certificateFiles;
    @Autowired io.github.turbopro.ism.qualification.PortalQualificationService portalCertificates;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper profileJson;
    static final AtomicLong IDS=new AtomicLong(20900);
    record Fixture(long tenant,long supplier,long account) {String code(){return "AUTH-"+tenant;}}
    Fixture fixture(){
        long t=IDS.incrementAndGet(),s=t*100;
        jdbc.update("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status) VALUES(?,?,?,'ACTIVE')",t,"AUTH-"+t,"门户登录测试");
        jdbc.update("INSERT INTO plt_package(id,package_code,package_name,status) VALUES(?,?,?,'ACTIVE')",s+2,"AUTH-"+t,"门户套餐");
        jdbc.update("INSERT INTO plt_package_version(id,package_id,version_no,version_name,status) VALUES(?,?,1,'门户版本','PUBLISHED')",s+3,s+2);
        jdbc.update("INSERT INTO plt_package_module(package_version_id,module_id,enabled,quota_json) SELECT ?,id,1,JSON_OBJECT() FROM plt_module WHERE module_code='SUPPLIER'",s+3);
        jdbc.update("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status,package_version_id) VALUES(?,?,?,'ACTIVE','READY',?)",t,"AUTH-"+t,"门户测试",s+3);
        jdbc.update("INSERT INTO iam_organization(id,tenant_id,organization_code,organization_name,organization_type,status) VALUES(?,?,'HQ','总部','HEADQUARTERS','ACTIVE')",s+1,t);
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(?,?,?,'SUP','供应商','MANUFACTURER',9,9)",s,t,s+1);
        PortalInvitationModels.Issued invite;
        try(var tenant=TenantContext.open(t,9);var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:update"),Map.of("supplier:master",DataScope.all()),Set.of()))){invite=invites.issue(s,new PortalInvitationModels.Issue(0,"联系人","portal@example.test",24));}
        var registered=registrations.register(new PortalRegistrationModels.Register(invite.token(),"Portal.User","Portal#Pass123"));
        return new Fixture(t,s,Long.parseLong(registered.accountId()));
    }
    PortalAuthModels.Session login(Fixture f){return auth.login(new PortalAuthModels.Login(f.code(),"Portal.User","Portal#Pass123"));}
    void me(String token,int expected)throws Exception{mvc.perform(get("/api/portal/auth/me").header("Authorization","Bearer "+token)).andExpect(status().is(expected));}
    @Test void profileReturnsOnlyBoundEnterpriseAndAllowlistedFields()throws Exception{
        var f=fixture();var foreign=fixture();long other=f.supplier()+20;
        jdbc.update("UPDATE sup_supplier SET supplier_name='本企业制造',industry='化工',address='工业园区一号',risk_level='HIGH',remark='PRIVATE_RISK_NOTE',legal_representative='PRIVATE_PERSON' WHERE id=?",f.supplier());
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(?,?,?,'OTHER','PRIVATE_OTHER_SUPPLIER','TRADER',9,9)",other,f.tenant(),f.supplier()+1);
        jdbc.update("INSERT INTO sup_purchase_category(id,tenant_id,category_code,category_name,status) VALUES(?,?,'CHEMICAL','化工原料','ACTIVE'),(?,?,'OLD','PRIVATE_INACTIVE','INACTIVE'),(?,?,'OTHER','PRIVATE_UNASSIGNED','ACTIVE'),(?,?,'CHEMICAL','PRIVATE_FOREIGN','ACTIVE')",f.supplier()+60,f.tenant(),f.supplier()+61,f.tenant(),f.supplier()+62,f.tenant(),foreign.supplier()+60,foreign.tenant());
        jdbc.update("INSERT INTO sup_supplier_purchase_category(tenant_id,supplier_id,category_id) VALUES(?,?,?),(?,?,?),(?,?,?),(?,?,?)",f.tenant(),f.supplier(),f.supplier()+60,f.tenant(),f.supplier(),f.supplier()+61,f.tenant(),other,f.supplier()+62,foreign.tenant(),foreign.supplier(),foreign.supplier()+60);
        String token=login(f).accessToken();
        for(long selected:List.of(other,foreign.supplier())){
            var response=mvc.perform(get("/api/portal/supplier-profile").header("Authorization","Bearer "+token).header("X-Tenant-Id",foreign.tenant())
                .param("supplierId",Long.toString(selected)).param("tenantId",Long.toString(foreign.tenant())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.supplier.id").value(Long.toString(f.supplier())))
                .andExpect(jsonPath("$.data.supplier.name").value("本企业制造"))
                .andExpect(jsonPath("$.data.purchaseCategories.length()").value(1))
                .andExpect(jsonPath("$.data.purchaseCategories[0].name").value("化工原料")).andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("PRIVATE_","riskLevel","remark","contacts","legalRepresentative","organizationId","createdBy","password","token");
            var data=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("data");
            var fields=new HashSet<String>();data.get("supplier").fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("id","code","name","shortName","unifiedSocialCreditCode","type","industry","countryCode","province","city","address","establishedDate","website","status","version");
        }
        try(var tenant=TenantContext.open(f.tenant(),f.account())){
            assertThatThrownBy(io.github.turbopro.ism.common.infrastructure.portal.PortalIdentityContext::require).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        }
    }
    @Test void profileRequiresLivePortalSessionAndRejectsEmployeeRealm()throws Exception{
        var f=fixture();String token=login(f).accessToken();
        mvc.perform(get("/api/portal/supplier-profile")).andExpect(status().isUnauthorized());
        String employee=internalTokens.createAccessToken(new AuthModels.AuthUser(9,f.tenant(),"employee","内部员工","unused","ACTIVE",false,null,false,0,0,null),"not-a-portal-session");
        mvc.perform(get("/api/portal/supplier-profile").header("Authorization","Bearer "+employee)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/portal/supplier-profile").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.purchaseCategories.length()").value(0));
        mvc.perform(post("/api/portal/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/portal/supplier-profile").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void profileRechecksAccountTenantSupplierAndPendingExitEveryRequest()throws Exception{
        var f=fixture();String token=login(f).accessToken();
        jdbc.update("UPDATE iam_portal_account SET status='DISABLED' WHERE id=?",f.account());profileStatus(token,401);
        jdbc.update("UPDATE iam_portal_account SET status='ACTIVE' WHERE id=?",f.account());
        jdbc.update("UPDATE plt_tenant SET status='SUSPENDED' WHERE id=?",f.tenant());profileStatus(token,401);
        jdbc.update("UPDATE plt_tenant SET status='ACTIVE' WHERE id=?",f.tenant());
        jdbc.update("UPDATE sup_supplier SET status='SUSPENDED' WHERE id=?",f.supplier());profileStatus(token,401);
        jdbc.update("UPDATE sup_supplier SET status='ACTIVE' WHERE id=?",f.supplier());profileStatus(token,200);
        jdbc.update("UPDATE sup_supplier SET deleted=1 WHERE id=?",f.supplier());profileStatus(token,401);
        jdbc.update("UPDATE sup_supplier SET deleted=0 WHERE id=?",f.supplier());
        var application=prepareExit(f);profileStatus(token,401);
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=exitGrants()){
            exits.cancel(f.supplier(),Long.parseLong(application.id()),new ExitModels.Version(application.version()));
        }
        profileStatus(token,200);
    }
    void profileStatus(String token,int expected)throws Exception{
        mvc.perform(get("/api/portal/supplier-profile").header("Authorization","Bearer "+token)).andExpect(status().is(expected));
    }
    void enableCertificates(Fixture f){jdbc.update("INSERT INTO plt_package_module(package_version_id,module_id,enabled,quota_json) SELECT ?,id,1,JSON_OBJECT() FROM plt_module WHERE module_code='QUALIFICATION'",f.supplier()+3);}
    long certificate(Fixture f,int offset,String status,int days,boolean permanent){
        long id=f.supplier()+offset,type=f.supplier()+80;
        jdbc.update("INSERT IGNORE INTO qua_qualification_type(id,tenant_id,type_code,type_name,category,created_by,updated_by) VALUES(?,?,'SAFETY','安全生产许可证','CHEMICAL',9,9)",type,f.tenant());
        String fileId;
        try(var tenant=TenantContext.open(f.tenant(),9)){fileId=certificateFiles.createGenerated("证照原件-"+id+".pdf","application/pdf",("CERTIFICATE-BYTES-"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8)).id();}
        jdbc.update("INSERT INTO qua_supplier_qualification(id,tenant_id,organization_id,supplier_id,qualification_type_id,certificate_no,issuing_authority,effective_date,expiry_date,permanent_flag,warning_days,file_id,status,verification_comment,revoked_reason,created_by,updated_by) VALUES(?,?,?,?,?,?,?,DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),IF(?=1,NULL,DATE_ADD(CURRENT_DATE,INTERVAL ? DAY)),?,30,?,?, 'PRIVATE_REVIEW_NOTE','PRIVATE_REVOCATION_NOTE',9,9)",id,f.tenant(),f.supplier()+1,f.supplier(),type,"CERT_%_"+id,"应急管理部门",permanent,days,permanent,Long.parseLong(fileId),status);
        return id;
    }
    @Test void portalCertificatesOnlyExposePublishedOwnRecordsAndWhitelistedFields()throws Exception{
        var f=fixture();enableCertificates(f);var foreign=fixture();long own=certificate(f,81,"VALID",60,false),draft=certificate(f,82,"DRAFT",60,false),rejected=certificate(f,83,"REJECTED",60,false),other=certificate(foreign,81,"VALID",60,false);
        long sibling=f.supplier()+20; jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(?,?,?,'SIBLING','其他企业','TRADER',9,9)",sibling,f.tenant(),f.supplier()+1);
        long siblingCert=certificate(f,84,"VALID",60,false);jdbc.update("UPDATE qua_supplier_qualification SET supplier_id=? WHERE id=?",sibling,siblingCert);
        String token=login(f).accessToken();
        var response=mvc.perform(get("/api/portal/qualifications").header("Authorization","Bearer "+token).header("X-Tenant-Id",foreign.tenant()).param("tenantId",Long.toString(foreign.tenant())).param("supplierId",Long.toString(sibling)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value(Long.toString(own))).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("PRIVATE_","fileId","objectKey","verificationComment","revokedReason","createdBy","organizationId","verifiedBy");
        var fields=new HashSet<String>();profileJson.readTree(response).get("data").get("items").get(0).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id","typeCode","typeName","certificateNo","issuingAuthority","issueDate","effectiveDate","expiryDate","permanent","status","daysUntilExpiry","updatedAt");
        mvc.perform(get("/api/portal/qualifications/"+own).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.typeCode").value("SAFETY"));
        for(long hidden:List.of(draft,rejected,other,siblingCert,Long.MAX_VALUE))for(String suffix:List.of("","/content"))mvc.perform(get("/api/portal/qualifications/"+hidden+suffix).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
    }
    @Test void portalCertificatesShareExpiryRulesAndSupportLiteralSearchAndPagination()throws Exception{
        var f=fixture();enableCertificates(f);certificate(f,81,"VALID",60,false);certificate(f,82,"VALID",30,false);certificate(f,83,"VALID",0,false);certificate(f,84,"VALID",-1,false);certificate(f,85,"VALID",0,true);certificate(f,86,"REVOKED",-1,false);certificate(f,87,"SUPERSEDED",-1,false);
        jdbc.update("UPDATE qua_supplier_qualification SET certificate_no='PLAIN' WHERE id=?",f.supplier()+87);
        String token=login(f).accessToken();
        for(var entry:Map.of("VALID",2,"EXPIRING",2,"EXPIRED",1,"REVOKED",1,"SUPERSEDED",1).entrySet())mvc.perform(get("/api/portal/qualifications").header("Authorization","Bearer "+token).param("status",entry.getKey())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(entry.getValue()));
        var first=profileJson.readTree(mvc.perform(get("/api/portal/qualifications").header("Authorization","Bearer "+token).param("size","2")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        var second=profileJson.readTree(mvc.perform(get("/api/portal/qualifications").header("Authorization","Bearer "+token).param("size","2").param("page","1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        assertThat(first.get("total").asInt()).isEqualTo(7);assertThat(first.get("items").get(0).get("id").asText()).isNotEqualTo(second.get("items").get(0).get("id").asText());
        mvc.perform(get("/api/portal/qualifications").header("Authorization","Bearer "+token).param("keyword","%")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(6));
        mvc.perform(get("/api/portal/qualifications").header("Authorization","Bearer "+token).param("keyword","CERT_%_missing")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/portal/qualifications/"+(f.supplier()+85)).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.daysUntilExpiry").isEmpty());
        mvc.perform(get("/api/portal/qualifications/"+(f.supplier()+84)).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.daysUntilExpiry").value(-1));
        for(var params:List.of(Map.of("page","-1"),Map.of("page","10001"),Map.of("size","101"),Map.of("size","0"),Map.of("status","DRAFT"),Map.of("keyword","x".repeat(101)))){var request=get("/api/portal/qualifications").header("Authorization","Bearer "+token);params.forEach(request::param);mvc.perform(request).andExpect(status().isBadRequest());}
    }
    @Test void portalCertificateDownloadsAreAttachmentOnlyAuditedAndDoNotGrantGenericFiles()throws Exception{
        var f=fixture();enableCertificates(f);long id=certificate(f,81,"REVOKED",-2,false);String token=login(f).accessToken();
        mvc.perform(get("/api/portal/qualifications/"+id+"/content").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Type","application/octet-stream"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment; filename*=UTF-8''")))
            .andExpect(content().bytes(("CERTIFICATE-BYTES-"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND operator_id=? AND action='PORTAL_QUALIFICATION_DOWNLOAD_OPEN'",Long.class,f.tenant(),f.account())).isEqualTo(1);
        long fileId=jdbc.queryForObject("SELECT file_id FROM qua_supplier_qualification WHERE id=?",Long.class,id);
        mvc.perform(get("/api/files/"+fileId+"/content").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE res_file_object SET object_key='missing-certificate-original' WHERE id=?",fileId);
        mvc.perform(get("/api/portal/qualifications/"+id+"/content").header("Authorization","Bearer "+token)).andExpect(status().isServiceUnavailable());
        jdbc.update("UPDATE res_file_object SET status='DELETED' WHERE id=?",fileId);
        mvc.perform(get("/api/portal/qualifications/"+id+"/content").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='PORTAL_QUALIFICATION_DOWNLOAD_OPEN'",Long.class,f.tenant())).isEqualTo(1);
    }
    @Test void portalCertificatesEnforceLiveModuleAndAuthenticationRealms()throws Exception{
        var f=fixture();long id=certificate(f,81,"VALID",60,false);String token=login(f).accessToken();
        String employee=internalTokens.createAccessToken(new AuthModels.AuthUser(9,f.tenant(),"employee","内部员工","unused","ACTIVE",false,null,false,0,0,null),"not-a-portal-session");
        for(String path:List.of("/api/portal/qualifications","/api/portal/qualifications/"+id,"/api/portal/qualifications/"+id+"/content")){
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization","Bearer "+employee)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        }
        enableCertificates(f);
        mvc.perform(get("/api/portal/qualifications").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        jdbc.update("UPDATE plt_package_module pm JOIN plt_module m ON m.id=pm.module_id SET pm.enabled=0 WHERE pm.package_version_id=? AND m.module_code='QUALIFICATION'",f.supplier()+3);
        mvc.perform(get("/api/portal/qualifications/"+id+"/content").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        jdbc.update("UPDATE iam_portal_account SET status='DISABLED' WHERE id=?",f.account());
        mvc.perform(get("/api/portal/qualifications").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void portalCertificateDownloadRejectsSessionRevokedAfterAuthenticationAndRollsBackAuditFailure()throws Exception{
        var f=fixture();enableCertificates(f);long id=certificate(f,81,"VALID",60,false);String token=login(f).accessToken();var principal=auth.authenticate(token);
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='PORTAL_QUALIFICATION_DOWNLOAD_OPEN' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='certificate audit failure'; END IF; END");
        try{assertThatThrownBy(()->portalAction(principal,()->portalCertificates.download(id))).isInstanceOf(RuntimeException.class).hasStackTraceContaining("certificate audit failure");}finally{drop();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='PORTAL_QUALIFICATION_DOWNLOAD_OPEN'",Long.class,f.tenant())).isZero();
        auth.logout(principal);
        assertThatThrownBy(()->portalAction(principal,()->portalCertificates.download(id))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
    }
    ProfileChangeModels.Submit changeCommand(int version,String shortName){return new ProfileChangeModels.Submit(UUID.randomUUID().toString(),version,new ProfileChangeModels.Fields(shortName,"化工制造","CN","浙江","杭州","工业园二号","https://supplier.example.test"),"企业地址及业务信息更新");}
    <T> T portalAction(PortalAuthModels.Principal p,java.util.function.Supplier<T> action){
        try(var tenant=TenantContext.open(p.tenantId(),p.accountId());var portal=io.github.turbopro.ism.common.infrastructure.portal.PortalIdentityContext.open(p.tenantId(),p.accountId(),p.supplierId(),p.sessionId())){return action.get();}
    }
    ProfileChangeModels.View submitChange(String token,ProfileChangeModels.Submit command){return portalAction(auth.authenticate(token),()->changes.submit(command));}
    AuthorizationContext.Scope changeGrants(boolean review,DataScope scope){return AuthorizationContext.open(new PermissionSnapshot(review?Set.of("supplier:master:view","supplier:change:review"):Set.of("supplier:master:view"),Map.of("supplier:master",scope),Set.of()));}
    ProfileChangeModels.View reviewChange(Fixture f,ProfileChangeModels.View request,ProfileChangeModels.Decision decision){try(var tenant=TenantContext.open(f.tenant(),10);var grants=changeGrants(true,DataScope.all())){return changes.review(f.supplier(),Long.parseLong(request.id()),new ProfileChangeModels.Review(decision,"独立审核说明",request.version()));}}
    @Test void profileChangeHttpSubmissionIsIdempotentAndDoesNotEditTheMaster()throws Exception{
        var f=fixture();String token=login(f).accessToken();var command=changeCommand(0,"门户申请简称");String payload=profileJson.writeValueAsString(command);
        String first=mvc.perform(post("/api/portal/profile-changes").header("Authorization","Bearer "+token).header("X-Tenant-Id","999").contentType("application/json").content(payload))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.status").value("SUBMITTED")).andReturn().getResponse().getContentAsString();
        String id=profileJson.readTree(first).get("data").get("id").asText();
        var missingVersion=(com.fasterxml.jackson.databind.node.ObjectNode)profileJson.readTree(payload);missingVersion.remove("baseVersion");
        mvc.perform(post("/api/portal/profile-changes").header("Authorization","Bearer "+token).contentType("application/json").content(profileJson.writeValueAsString(missingVersion))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/portal/profile-changes/"+id+"/cancel").header("Authorization","Bearer "+token).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/portal/profile-changes").header("Authorization","Bearer "+token).contentType("application/json").content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_profile_change WHERE tenant_id=?",Long.class,f.tenant())).isOne();
        assertThat(jdbc.queryForObject("SELECT version FROM sup_supplier WHERE id=?",Integer.class,f.supplier())).isZero();
        assertThat(jdbc.queryForObject("SELECT short_name FROM sup_supplier WHERE id=?",String.class,f.supplier())).isNull();
        var different=new ProfileChangeModels.Submit(command.requestKey(),0,command.fields(),"不同内容");
        mvc.perform(post("/api/portal/profile-changes").header("Authorization","Bearer "+token).contentType("application/json").content(profileJson.writeValueAsString(different))).andExpect(status().isConflict());
        mvc.perform(get("/api/portal/profile-changes").header("Authorization","Bearer "+token).param("supplierId","999")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].canCancel").value(true));
        mvc.perform(post("/api/portal/profile-changes").contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
    }
    @Test void profileChangeApprovalHasIndependentPermissionScopeAndAtomicAllowlistUpdate()throws Exception{
        var f=fixture();var foreign=fixture();String token=login(f).accessToken();
        jdbc.update("UPDATE sup_supplier SET remark='PRIVATE_NOTE',risk_level='HIGH' WHERE id=?",f.supplier());
        var request=submitChange(token,changeCommand(0,"已批准简称"));
        try(var tenant=TenantContext.open(f.tenant(),10);var grants=changeGrants(false,DataScope.all())){
            assertThat(changes.employeeList(f.supplier(),0,20).canReview()).isFalse();
            assertThatThrownBy(()->changes.review(f.supplier(),Long.parseLong(request.id()),new ProfileChangeModels.Review(ProfileChangeModels.Decision.APPROVE,"无权限",0))).isInstanceOfSatisfying(io.github.turbopro.ism.common.api.error.ApiException.class,e->assertThat(e.errorCode()).isEqualTo(io.github.turbopro.ism.common.api.error.CommonErrorCode.FORBIDDEN));
        }
        try(var tenant=TenantContext.open(f.tenant(),10);var grants=changeGrants(true,DataScope.none())){assertThatThrownBy(()->changes.review(f.supplier(),Long.parseLong(request.id()),new ProfileChangeModels.Review(ProfileChangeModels.Decision.APPROVE,"范围外",0))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);}
        try(var tenant=TenantContext.open(foreign.tenant(),10);var grants=changeGrants(true,DataScope.all())){assertThatThrownBy(()->changes.employeeList(f.supplier(),0,20)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);}
        assertThat(reviewChange(f,request,ProfileChangeModels.Decision.APPROVE).status()).isEqualTo("APPROVED");
        mvc.perform(get("/api/portal/supplier-profile").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.supplier.shortName").value("已批准简称")).andExpect(jsonPath("$.data.supplier.version").value(1));
        assertThat(jdbc.queryForObject("SELECT remark FROM sup_supplier WHERE id=?",String.class,f.supplier())).isEqualTo("PRIVATE_NOTE");
        assertThat(jdbc.queryForObject("SELECT risk_level FROM sup_supplier WHERE id=?",String.class,f.supplier())).isEqualTo("HIGH");
        assertThatThrownBy(()->reviewChange(f,request,ProfileChangeModels.Decision.APPROVE)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_PROFILE_CHANGE_APPROVE'",Long.class,f.tenant())).isOne();
    }
    @Test void staleMasterCannotBeOverwrittenAndRejectedRequestCanBeResubmittedAndCancelled(){
        var f=fixture();String token=login(f).accessToken();var request=submitChange(token,changeCommand(0,"待审简称"));
        jdbc.update("UPDATE sup_supplier SET short_name='内部更新',version=version+1 WHERE id=?",f.supplier());
        assertThatThrownBy(()->reviewChange(f,request,ProfileChangeModels.Decision.APPROVE)).isInstanceOfSatisfying(io.github.turbopro.ism.common.api.error.ApiException.class,e->assertThat(e.errorCode()).isEqualTo(io.github.turbopro.ism.common.api.error.CommonErrorCode.CONFLICT));
        assertThat(jdbc.queryForObject("SELECT short_name FROM sup_supplier WHERE id=?",String.class,f.supplier())).isEqualTo("内部更新");
        assertThat(reviewChange(f,request,ProfileChangeModels.Decision.REJECT).status()).isEqualTo("REJECTED");
        var fresh=submitChange(token,changeCommand(1,"重新申请"));var p=auth.authenticate(token);
        assertThat(portalAction(p,()->changes.cancel(Long.parseLong(fresh.id()),fresh.version())).status()).isEqualTo("CANCELLED");
        assertThatThrownBy(()->portalAction(p,()->changes.cancel(Long.parseLong(fresh.id()),0))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT short_name FROM sup_supplier WHERE id=?",String.class,f.supplier())).isEqualTo("内部更新");
    }
    @Test void profileChangeCancellationRequiresTheCreatorAndCorrectEnterprise(){
        var f=fixture();String token=login(f).accessToken();var request=submitChange(token,changeCommand(0,"申请"));
        registrations.register(new PortalRegistrationModels.Register(pendingInvite(f),"Another.User","Portal#Pass123"));
        String other=auth.login(new PortalAuthModels.Login(f.code(),"Another.User","Portal#Pass123")).accessToken();
        var p=auth.authenticate(other);
        assertThat(portalAction(p,()->changes.portalList(0,20)).items().get(0).canCancel()).isFalse();
        assertThatThrownBy(()->portalAction(p,()->changes.cancel(Long.parseLong(request.id()),0))).isInstanceOfSatisfying(io.github.turbopro.ism.common.api.error.ApiException.class,e->assertThat(e.errorCode()).isEqualTo(io.github.turbopro.ism.common.api.error.CommonErrorCode.FORBIDDEN));
        var foreign=fixture();var stranger=auth.authenticate(login(foreign).accessToken());
        assertThat(portalAction(stranger,()->changes.portalList(0,20)).total()).isZero();
        assertThatThrownBy(()->portalAction(stranger,()->changes.cancel(Long.parseLong(request.id()),0))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
    }
    @Test void portalChangeWriteRechecksRevocationAfterFilterAuthentication(){
        var f=fixture();var p=auth.authenticate(login(f).accessToken());auth.logout(p);
        assertThatThrownBy(()->portalAction(p,()->changes.submit(changeCommand(0,"撤销后提交")))).isInstanceOfSatisfying(io.github.turbopro.ism.common.api.error.ApiException.class,e->assertThat(e.errorCode()).isEqualTo(io.github.turbopro.ism.common.api.error.CommonErrorCode.UNAUTHENTICATED));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_profile_change WHERE tenant_id=?",Long.class,f.tenant())).isZero();
    }
    @Test void profileChangeAuditFailureRollsBackSubmissionAndApproval()throws Exception{
        var f=fixture();String token=login(f).accessToken();var command=changeCommand(0,"事务回滚验证");
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='SUPPLIER_PROFILE_CHANGE_SUBMIT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='profile audit failure'; END IF; END");
        try{assertThatThrownBy(()->submitChange(token,command)).isInstanceOf(RuntimeException.class);}finally{drop();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_profile_change WHERE tenant_id=?",Long.class,f.tenant())).isZero();
        var request=submitChange(token,command);
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='SUPPLIER_PROFILE_CHANGE_APPROVE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='profile audit failure'; END IF; END");
        try{assertThatThrownBy(()->reviewChange(f,request,ProfileChangeModels.Decision.APPROVE)).isInstanceOf(RuntimeException.class);}finally{drop();}
        assertThat(jdbc.queryForObject("SELECT version FROM sup_supplier WHERE id=?",Integer.class,f.supplier())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM sup_profile_change WHERE id=?",String.class,Long.parseLong(request.id()))).isEqualTo("SUBMITTED");
        assertThat(reviewChange(f,request,ProfileChangeModels.Decision.APPROVE).status()).isEqualTo("APPROVED");
    }
    @Test void concurrentPortalChangesLeaveExactlyOnePendingRequest()throws Exception{
        var f=fixture();var p=auth.authenticate(login(f).accessToken());var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try{
            var jobs=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int n=0;n<2;n++){var command=changeCommand(0,"并发简称"+n);jobs.add(pool.submit(()->{start.await();try{portalAction(p,()->changes.submit(command));return true;}catch(io.github.turbopro.ism.common.api.error.ApiException e){assertThat(e.errorCode()).isEqualTo(io.github.turbopro.ism.common.api.error.CommonErrorCode.CONFLICT);return false;}}));}
            start.countDown();assertThat(List.of(jobs.get(0).get(20,java.util.concurrent.TimeUnit.SECONDS),jobs.get(1).get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_profile_change WHERE tenant_id=? AND status='SUBMITTED'",Long.class,f.tenant())).isOne();
    }
    @Test void concurrentExactRequestReplayReturnsOneApplicationWithoutDuplicateAudit()throws Exception{
        var f=fixture();var p=auth.authenticate(login(f).accessToken());var command=changeCommand(0,"并发同键");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try{
            java.util.concurrent.Callable<String> submit=()->{start.await();return portalAction(p,()->changes.submit(command)).id();};
            var first=pool.submit(submit);var second=pool.submit(submit);start.countDown();
            assertThat(first.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_profile_change WHERE tenant_id=?",Long.class,f.tenant())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_PROFILE_CHANGE_SUBMIT'",Long.class,f.tenant())).isOne();
    }
    @Test void invalidWebsitesNoopAndPendingExitDoNotChangeEnterprise(){
        var f=fixture();String token=login(f).accessToken();
        for(String website:List.of("javascript:alert(1)","relative-path","https://user:password@example.test")){
            var command=new ProfileChangeModels.Submit(UUID.randomUUID().toString(),0,new ProfileChangeModels.Fields("新简称","","CN","","","",website),"更正");
            assertThatThrownBy(()->submitChange(token,command)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        }
        assertThatThrownBy(()->submitChange(token,new ProfileChangeModels.Submit(UUID.randomUUID().toString(),0,new ProfileChangeModels.Fields("","","CN","","","",""),"无变化"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        var request=submitChange(token,changeCommand(0,"待审"));prepareExit(f);
        assertThatThrownBy(()->reviewChange(f,request,ProfileChangeModels.Decision.APPROVE)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(reviewChange(f,request,ProfileChangeModels.Decision.REJECT).status()).isEqualTo("REJECTED");
    }
    AuthorizationContext.Scope recoveryGrants(){return AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:portal:recover"),Map.of("supplier:master",DataScope.all()),Set.of()));}
    io.github.turbopro.ism.common.infrastructure.portal.PortalPasswordRecoveryAdministration.Issued issueRecovery(Fixture f,int version){
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=recoveryGrants()){return administration.issueRecovery(f.supplier(),f.account(),version,"已通过合同联系人线下核实身份",true);}
    }
    @Test void recoveryCapabilityResetsLockedPasswordAndRevokesAllSessions()throws Exception{
        var f=fixture();String one=login(f).accessToken(),two=login(f).accessToken();var grant=issueRecovery(f,0);
        assertThat(grant.token()).matches("[A-Za-z0-9_-]{43}");assertThat(grant.toString()).doesNotContain(grant.token());
        me(one,200); // Issuance alone never claims to revoke sessions or change the password.
        jdbc.update("UPDATE iam_portal_account SET failed_count=5,locked_until=UTC_TIMESTAMP(3)+INTERVAL 15 MINUTE WHERE id=?",f.account());
        mvc.perform(post("/api/portal/auth/reset-password").header("X-Tenant-Id","99999").contentType("application/json").content("{\"recoveryToken\":\""+grant.token()+"\",\"newPassword\":\"Recovered#Pass456\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        me(one,401);me(two,401);
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT failed_count FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_password_recovery WHERE tenant_id=?",String.class,f.tenant())).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM iam_portal_password_recovery WHERE tenant_id=?",String.class,f.tenant())).hasSize(64).isNotEqualTo(grant.token());
        assertThatThrownBy(()->passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(grant.token(),"Another#Pass789"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        me(auth.login(new PortalAuthModels.Login(f.code(),"Portal.User","Recovered#Pass456")).accessToken(),200);
    }
    @Test void recoveryRequiresDedicatedPermissionScopeVersionAndIdentityVerification(){
        var f=fixture();var other=fixture();
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=exitGrants()){
            assertThatThrownBy(()->administration.issueRecovery(f.supplier(),f.account(),0,"确认身份",true)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        }
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=recoveryGrants()){
            assertThatThrownBy(()->administration.issueRecovery(other.supplier(),other.account(),0,"确认身份",true)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            assertThatThrownBy(()->administration.issueRecovery(f.supplier(),other.account(),0,"确认身份",true)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            assertThatThrownBy(()->administration.issueRecovery(f.supplier(),f.account(),0,"尚未核查",false)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        }
        var grant=issueRecovery(f,0);assertThatThrownBy(()->issueRecovery(f,0)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=recoveryGrants()){
            var state=administration.recoveryState(f.supplier(),f.account());assertThat(state.status()).isEqualTo("PENDING");assertThat(state.toString()).doesNotContain(grant.token());
        }
    }
    @Test void recoveryExpiryReplacementRevocationAndAccountChangesInvalidateGrants(){
        var f=fixture();var first=issueRecovery(f,0);
        jdbc.update("UPDATE iam_portal_password_recovery SET expires_at=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE tenant_id=?",f.tenant());
        assertThatThrownBy(()->passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(first.token(),"Recovered#Pass456"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        var second=issueRecovery(f,1);assertThat(second.token()).isNotEqualTo(first.token());
        assertThatThrownBy(()->passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(first.token(),"Recovered#Pass456"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=recoveryGrants()){administration.revokeRecovery(f.supplier(),f.account(),2,"凭证疑似泄漏");}
        assertThatThrownBy(()->passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(second.token(),"Recovered#Pass456"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        var third=issueRecovery(f,3);manage(f,4,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.REVOKE_SESSIONS);
        assertThatThrownBy(()->passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(third.token(),"Recovered#Pass456"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        login(f);
    }
    @Test void recoveryAuditFailureRollsBackConsumptionPasswordAndSessions()throws Exception{
        var f=fixture();String token=login(f).accessToken();var grant=issueRecovery(f,0);
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='PORTAL_RECOVERY_RESET' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected recovery audit failure'; END IF; END");
        try{assertThatThrownBy(()->passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(grant.token(),"Recovered#Pass456"))).isInstanceOf(RuntimeException.class).hasStackTraceContaining("injected recovery audit failure");}finally{drop();}
        me(token,200);login(f);
        assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_password_recovery WHERE tenant_id=?",String.class,f.tenant())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isEqualTo(1);
        passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(grant.token(),"Recovered#Pass456"));me(token,401);
    }
    @Test void recoveryIssuanceAuditFailureRestoresPreviousGrantAndVersion()throws Exception{
        var f=fixture();var first=issueRecovery(f,0);
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='PORTAL_RECOVERY_ISSUE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected recovery issue failure'; END IF; END");
        try{assertThatThrownBy(()->issueRecovery(f,1)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("injected recovery issue failure");}finally{drop();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_password_recovery WHERE tenant_id=?",Long.class,f.tenant())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isEqualTo(1);
        passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(first.token(),"Recovered#Pass456"));
    }
    @Test void recoveryRejectsWeakSameAndOversizePasswordsWithoutConsumingGrant(){
        var f=fixture();var grant=issueRecovery(f,0);
        for(String password:List.of("weak","Portal#Pass123","Aa1!"+"中".repeat(24)))assertThatThrownBy(()->passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(grant.token(),password))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_password_recovery WHERE tenant_id=?",String.class,f.tenant())).isEqualTo("PENDING");
        passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(grant.token(),"Recovered#Pass456"));
    }
    @Test void concurrentRecoveryRedemptionHasOnlyOneWinner()throws Exception{
        var f=fixture();var grant=issueRecovery(f,0);var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Boolean> reset=()->{start.await();try{passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(grant.token(),"Recovered#Pass456"));return true;}catch(io.github.turbopro.ism.common.api.error.ApiException rejected){return false;}};
        try{var one=pool.submit(reset);var two=pool.submit(reset);start.countDown();assertThat(List.of(one.get(15,java.util.concurrent.TimeUnit.SECONDS),two.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);}
        finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='PORTAL_RECOVERY_RESET'",Long.class,f.tenant())).isEqualTo(1);
    }
    @Test void recoveryCannotBypassPendingOrApprovedExit(){
        for(boolean approve:List.of(false,true)){
            var f=fixture();var grant=issueRecovery(f,0);var application=prepareExit(f);if(approve)approveExit(f,application);
            assertThatThrownBy(()->passwordRecovery.reset(new PortalPasswordRecoveryService.Reset(grant.token(),"Recovered#Pass456"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            assertThatThrownBy(()->issueRecovery(f,1)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        }
    }

    void oldSessions(Fixture f,int count){
        var rows=new ArrayList<Object[]>();
        for(int i=0;i<count;i++){long id=800000000000L+f.supplier()*1000+i;rows.add(new Object[]{id,f.tenant(),f.account(),String.format("%064x",id)});}
        jdbc.batchUpdate("INSERT INTO iam_portal_session(id,tenant_id,account_id,token_hash,token_version,expires_at,created_at) VALUES(?,?,?,?,0,'2000-01-02','2000-01-01')",rows);
    }
    int clean(Fixture f){try(var context=TenantContext.openSystem(f.tenant())){return retention.cleanBatch();}}
    @Test void retentionUsesStrictCutoffAndRejectsCrossTenantMapperArguments(){
        var f=fixture();oldSessions(f,4);var cutoff=java.time.LocalDateTime.of(2001,1,1,0,0);
        var ids=jdbc.queryForList("SELECT id FROM iam_portal_session WHERE tenant_id=? ORDER BY id",Long.class,f.tenant());
        jdbc.update("UPDATE iam_portal_session SET expires_at=? WHERE id=?",cutoff,ids.get(0));
        jdbc.update("UPDATE iam_portal_session SET revoked_at=? WHERE id=?",cutoff,ids.get(1));
        jdbc.update("UPDATE iam_portal_session SET revoked_at=? WHERE id=?",cutoff.minusNanos(1000000),ids.get(2));
        jdbc.update("UPDATE iam_portal_session SET expires_at=? WHERE id=?",cutoff.minusNanos(1000000),ids.get(3));
        try(var context=TenantContext.openSystem(f.tenant())){
            assertThatThrownBy(()->retentionMapper.lockBatch(f.tenant()+1,cutoff,100)).hasStackTraceContaining("Tenant parameter does not match authenticated tenant");
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
                assertThat(retentionMapper.lockBatch(f.tenant(),cutoff,100)).containsExactlyInAnyOrder(ids.get(2),ids.get(3));
                // Deletion must repeat the predicate, even if a selected row has changed in this transaction.
                jdbc.update("UPDATE iam_portal_session SET expires_at=? WHERE id=?",cutoff.plusDays(1),ids.get(2));
                assertThat(retentionMapper.deleteBatch(f.tenant(),cutoff,List.of(ids.get(2),ids.get(3)))).isEqualTo(1);
            });
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE tenant_id=?",Long.class,f.tenant())).isEqualTo(3);
    }
    @Test void retentionOnlyDeletesOldExpiredSessionsWithinSystemTenant()throws Exception{
        var f=fixture();var other=fixture();String live=login(f).accessToken();oldSessions(f,5);oldSessions(other,1);
        var old=jdbc.queryForList("SELECT id FROM iam_portal_session WHERE tenant_id=? AND expires_at<'2001-01-01' ORDER BY id",Long.class,f.tenant());
        jdbc.update("UPDATE iam_portal_session SET revoked_at='2000-01-03' WHERE id=?",old.get(0));
        jdbc.update("UPDATE iam_portal_session SET revoked_at=UTC_TIMESTAMP(3) WHERE id=?",old.get(1));
        jdbc.update("UPDATE iam_portal_session SET expires_at=UTC_TIMESTAMP(3)-INTERVAL 89 DAY WHERE id=?",old.get(2));
        jdbc.update("UPDATE iam_portal_session SET expires_at=UTC_TIMESTAMP(3)+INTERVAL 1 DAY,revoked_at='2000-01-03' WHERE id=?",old.get(3));
        assertThat(retention.candidates(0)).contains(f.tenant(),other.tenant());
        try(var context=TenantContext.open(f.tenant(),9)){
            assertThatThrownBy(retention::cleanBatch).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->retention.candidates(0)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(clean(f)).isEqualTo(2);assertThat(clean(f)).isZero();me(live,200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE tenant_id=?",Long.class,other.tenant())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='PORTAL_SESSION_CLEANUP' AND operator_id=0",Long.class,f.tenant())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_account WHERE id=?",String.class,f.account())).isEqualTo("ACTIVE");
        assertThat(TenantContext.current()).isEmpty();
    }
    @Test void retentionBatchesSkipRowsLockedByAnotherNode()throws Exception{
        var f=fixture();oldSessions(f,201);
        var locked=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{
            var holder=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status->{
                assertThat(jdbc.queryForList("SELECT id FROM iam_portal_session WHERE tenant_id=? ORDER BY expires_at,id LIMIT 100 FOR UPDATE",Long.class,f.tenant())).hasSize(100);
                locked.countDown();
                try{if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("retention lock timed out");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return true;
            }));
            assertThat(locked.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(clean(f)).isEqualTo(100); // Must finish while the first 100 are still locked.
            release.countDown();assertThat(holder.get(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(clean(f)).isEqualTo(100);assertThat(clean(f)).isEqualTo(1);assertThat(clean(f)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE tenant_id=?",Long.class,f.tenant())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='PORTAL_SESSION_CLEANUP'",Long.class,f.tenant())).isEqualTo(3);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
    }
    @Test void retentionAuditFailureRollsBackDeletionAndAllowsRetry()throws Exception{
        var f=fixture();oldSessions(f,2);
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='PORTAL_SESSION_CLEANUP' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected retention audit failure'; END IF; END");
        try{assertThatThrownBy(()->clean(f)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("injected retention audit failure");}finally{drop();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE tenant_id=?",Long.class,f.tenant())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='PORTAL_SESSION_CLEANUP'",Long.class,f.tenant())).isZero();
        assertThat(clean(f)).isEqualTo(2);assertThat(clean(f)).isZero();
    }

    @Test void passwordChangeRevokesEverySessionAndRequiresNewPassword()throws Exception{
        var f=fixture();var first=login(f);var second=login(f);
        mvc.perform(post("/api/portal/auth/password").header("Authorization","Bearer "+first.accessToken()).contentType("application/json")
                .content("{\"currentPassword\":\"Portal#Pass123\",\"newPassword\":\"Changed#Pass456\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        me(first.accessToken(),401);me(second.accessToken(),401);
        assertThatThrownBy(()->login(f)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        var fresh=auth.login(new PortalAuthModels.Login(f.code(),"Portal.User","Changed#Pass456"));me(fresh.accessToken(),200);
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT token_version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE account_id=? AND revoked_at IS NOT NULL",Long.class,f.account())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='PORTAL_PASSWORD_CHANGE'",Long.class,f.tenant())).isEqualTo(1);
        assertThat(new PortalAuthModels.ChangePassword("Portal#Pass123","Changed#Pass456").toString()).doesNotContain("Pass123","Pass456");
        mvc.perform(post("/api/portal/auth/password").contentType("application/json").content("{\"currentPassword\":\"Portal#Pass123\",\"newPassword\":\"Changed#Pass456\"}")).andExpect(status().isUnauthorized());
    }
    @Test void passwordChangeValidatesStrengthAndPersistsWrongPasswordLockout()throws Exception{
        var f=fixture();String token=login(f).accessToken();var principal=auth.authenticate(token);
        for(String password:List.of("weak","Portal#Pass123","Aa1!"+"中".repeat(24))){
            assertThatThrownBy(()->auth.changePassword(principal,new PortalAuthModels.ChangePassword("Portal#Pass123",password))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        }
        assertThat(jdbc.queryForObject("SELECT failed_count FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
        for(int i=0;i<5;i++)assertThatThrownBy(()->auth.changePassword(principal,new PortalAuthModels.ChangePassword("Incorrect#Pass123","Changed#Pass456"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT failed_count FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isEqualTo(5);
        assertThatThrownBy(()->auth.changePassword(principal,new PortalAuthModels.ChangePassword("Portal#Pass123","Changed#Pass456"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
        me(token,200);
        jdbc.update("UPDATE iam_portal_account SET locked_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?",f.account());
        auth.changePassword(principal,new PortalAuthModels.ChangePassword("Portal#Pass123","Changed#Pass456"));me(token,401);
    }
    @Test void passwordChangeRechecksSessionAfterFilterAuthentication(){
        var f=fixture();var principal=auth.authenticate(login(f).accessToken());
        manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.REVOKE_SESSIONS);
        assertThatThrownBy(()->auth.changePassword(principal,new PortalAuthModels.ChangePassword("Portal#Pass123","Changed#Pass456"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='PORTAL_PASSWORD_CHANGE'",Long.class,f.tenant())).isZero();
        login(f);
    }
    @Test void passwordAuditFailureRollsBackPasswordVersionsAndAllSessions()throws Exception{
        var f=fixture();String first=login(f).accessToken();String second=login(f).accessToken();var principal=auth.authenticate(first);
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='PORTAL_PASSWORD_CHANGE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected password audit failure'; END IF; END");
        try{assertThatThrownBy(()->auth.changePassword(principal,new PortalAuthModels.ChangePassword("Portal#Pass123","Changed#Pass456"))).isInstanceOf(RuntimeException.class).hasStackTraceContaining("injected password audit failure");}finally{drop();}
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
        assertThat(jdbc.queryForObject("SELECT token_version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
        me(first,200);me(second,200);login(f);
        auth.changePassword(principal,new PortalAuthModels.ChangePassword("Portal#Pass123","Changed#Pass456"));me(first,401);me(second,401);
    }

    @Test void httpLoginReturnsBoundIdentityAndCannotCrossInternalRealms()throws Exception{
        var f=fixture();var response=mvc.perform(post("/api/portal/auth/login").contentType("application/json").content("{\"tenantCode\":\""+f.code()+"\",\"username\":\"PORTAL.USER\",\"password\":\"Portal#Pass123\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.user.accountId").value(Long.toString(f.account()))).andReturn();
        String token=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("data").path("accessToken").asText();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        mvc.perform(get("/api/portal/auth/me").header("Authorization","Bearer "+token).header("X-Tenant-Id","999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(Long.toString(f.tenant()))).andExpect(jsonPath("$.data.supplierId").value(Long.toString(f.supplier()))).andExpect(jsonPath("$.data.permissions").doesNotExist());
        mvc.perform(get("/api/suppliers").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/console/tenants").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        String internal=internalTokens.createAccessToken(new AuthModels.AuthUser(9,f.tenant(),"employee","内部员工","unused","ACTIVE",false,null,false,0,0,null),"not-a-portal-session");
        me(internal,401);mvc.perform(get("/api/portal/auth/me")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT token_hash FROM iam_portal_session WHERE account_id=?",String.class,f.account())).isNotEqualTo(token).hasSize(64);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_refresh_token WHERE tenant_id=?",Long.class,f.tenant())).isZero();
        assertThat(TenantContext.current()).isEmpty();
    }
    @Test void logoutOnlyRevokesCurrentSessionAndRejectsReplay()throws Exception{
        var f=fixture();var first=login(f);var second=login(f);
        mvc.perform(post("/api/portal/auth/logout").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isOk());
        me(first.accessToken(),401);me(second.accessToken(),200);
        mvc.perform(post("/api/portal/auth/logout").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE account_id=? AND revoked_at IS NOT NULL",Long.class,f.account())).isEqualTo(1);
        var principal=auth.authenticate(second.accessToken());auth.logout(principal);auth.logout(principal);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='PORTAL_LOGOUT'",Long.class,f.tenant())).isEqualTo(2);
        assertThat(first.toString()).doesNotContain(first.accessToken());
    }
    @Test void accountVersionAndDomainChangesAreRecheckedOnEveryRequest()throws Exception{
        var f=fixture();String token=login(f).accessToken();
        jdbc.update("UPDATE iam_portal_account SET status='DISABLED' WHERE id=?",f.account());me(token,401);
        jdbc.update("UPDATE iam_portal_account SET status='ACTIVE' WHERE id=?",f.account());me(token,200);
        jdbc.update("UPDATE sup_supplier SET status='SUSPENDED' WHERE id=?",f.supplier());me(token,401);
        jdbc.update("UPDATE sup_supplier SET status='DRAFT' WHERE id=?",f.supplier());me(token,200);
        jdbc.update("UPDATE plt_tenant SET status='SUSPENDED' WHERE id=?",f.tenant());me(token,401);
        jdbc.update("UPDATE plt_tenant SET status='ACTIVE' WHERE id=?",f.tenant());
        jdbc.update("UPDATE iam_tenant SET status='SUSPENDED' WHERE id=?",f.tenant());me(token,401);
        jdbc.update("UPDATE iam_tenant SET status='ACTIVE' WHERE id=?",f.tenant());
        jdbc.update("UPDATE plt_package_module SET enabled=0 WHERE package_version_id=?",f.supplier()+3);me(token,401);
        jdbc.update("UPDATE plt_package_module SET enabled=1 WHERE package_version_id=?",f.supplier()+3);me(token,200);
        jdbc.update("UPDATE iam_portal_account SET token_version=token_version+1 WHERE id=?",f.account());me(token,401);
    }
    @Test void badPasswordsPersistLockoutAndExpiredLockAllowsRecovery(){
        var f=fixture();var wrong=new PortalAuthModels.Login(f.code(),"portal.user","Incorrect#Pass123");
        for(int i=0;i<5;i++)assertThatThrownBy(()->auth.login(wrong)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT failed_count FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isEqualTo(5);
        assertThatThrownBy(()->login(f)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE account_id=?",Long.class,f.account())).isZero();
        jdbc.update("UPDATE iam_portal_account SET locked_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?",f.account());
        login(f);assertThat(jdbc.queryForObject("SELECT failed_count FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
    }
    @Test void expiredRevokedAndUnknownTokensCannotAuthenticate()throws Exception{
        var f=fixture();String token=login(f).accessToken();
        jdbc.update("UPDATE iam_portal_session SET expires_at=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE account_id=?",f.account());me(token,401);me("Z".repeat(43),401);
        String another=login(f).accessToken();jdbc.update("UPDATE iam_portal_session SET revoked_at=UTC_TIMESTAMP(3) WHERE account_id=?",f.account());me(another,401);
    }
    @Test void auditFailureRollsBackLoginAndLogout()throws Exception{
        var f=fixture();String trigger="CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action IN ('PORTAL_LOGIN','PORTAL_LOGOUT') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected portal auth audit failure'; END IF; END";
        install(trigger);
        try{assertThatThrownBy(()->login(f)).isInstanceOf(RuntimeException.class);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE account_id=?",Long.class,f.account())).isZero();}
        finally{drop();}
        String token=login(f).accessToken();install(trigger);
        try{mvc.perform(post("/api/portal/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isInternalServerError());me(token,200);}
        finally{drop();}
        mvc.perform(post("/api/portal/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk());me(token,401);
    }
    io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Account manage(Fixture f,int version,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action action){
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view","supplier:master:update"),Map.of("supplier:master",DataScope.all()),Set.of()))){
            return administration.change(f.supplier(),f.account(),version,action,"账号安全维护");
        }
    }
    @Test void administrationDisablesRestoresAndPermanentlyRevokesAllDevices()throws Exception{
        var f=fixture();var first=login(f);var second=login(f);
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view"),Map.of("supplier:master",DataScope.all()),Set.of()))){
            var list=administration.list(f.supplier(),1,20);
            assertThat(list.canManage()).isFalse();assertThat(list.accounts().total()).isEqualTo(1);
            assertThat(list.accounts().items().get(0).liveSessions()).isEqualTo(2);
        }
        var disabled=manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE);
        assertThat(disabled.status()).isEqualTo("DISABLED");assertThat(disabled.version()).isEqualTo(1);assertThat(disabled.liveSessions()).isZero();
        me(first.accessToken(),401);me(second.accessToken(),401);assertThatThrownBy(()->login(f)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        manage(f,1,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.ENABLE);
        me(first.accessToken(),401);var third=login(f);me(third.accessToken(),200);
        manage(f,2,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.REVOKE_SESSIONS);
        me(third.accessToken(),401);me(login(f).accessToken(),200);
        assertThatThrownBy(()->manage(f,2,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action LIKE 'SUPPLIER_PORTAL_ACCOUNT_%'",Long.class,f.tenant())).isEqualTo(3);
    }
    @Test void administrationRequiresActionScopeAndCorrectTenantAndSupplier(){
        var f=fixture();var other=fixture();
        try(var tenant=TenantContext.open(f.tenant(),9)){
            assertThatThrownBy(()->administration.list(f.supplier(),1,20)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            try(var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view"),Map.of("supplier:master",DataScope.all()),Set.of()))){
                assertThatThrownBy(()->administration.change(f.supplier(),f.account(),0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE,"越权")).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                assertThatThrownBy(()->administration.list(other.supplier(),1,20)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                assertThatThrownBy(()->administration.list(f.supplier(),0,20)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            }
            try(var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view"),Map.of("supplier:master",DataScope.none()),Set.of()))){
                assertThatThrownBy(()->administration.list(f.supplier(),1,20)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            }
            try(var grants=AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:update"),Map.of("supplier:master",DataScope.all()),Set.of()))){
                assertThatThrownBy(()->administration.change(f.supplier(),other.account(),0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE,"错绑")).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            }
        }
    }
    @Test void administrationAuditFailureRollsBackStatusVersionAndSessionRevocation()throws Exception{
        var f=fixture();var session=login(f);
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action LIKE 'SUPPLIER_PORTAL_ACCOUNT_%' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected account audit failure'; END IF; END");
        try{assertThatThrownBy(()->manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE)).isInstanceOf(RuntimeException.class);}
        finally{drop();}
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_account WHERE id=?",String.class,f.account())).isEqualTo("ACTIVE");me(session.accessToken(),200);
        assertThat(manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE).status()).isEqualTo("DISABLED");
    }
    @Test void concurrentAdministrationWithSameVersionHasOnlyOneWinner()throws Exception{
        var f=fixture();var token=login(f).accessToken();
        var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var first=pool.submit(()->{start.await();try{manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE);return true;}catch(io.github.turbopro.ism.common.api.error.ApiException conflict){assertThat(conflict.errorCode()).isEqualTo(io.github.turbopro.ism.common.api.error.CommonErrorCode.CONFLICT);return false;}});
            var second=pool.submit(()->{start.await();try{manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.REVOKE_SESSIONS);return true;}catch(io.github.turbopro.ism.common.api.error.ApiException conflict){assertThat(conflict.errorCode()).isEqualTo(io.github.turbopro.ism.common.api.error.CommonErrorCode.CONFLICT);return false;}});
            start.countDown();assertThat(List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isEqualTo(1);me(token,401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action LIKE 'SUPPLIER_PORTAL_ACCOUNT_%'",Long.class,f.tenant())).isEqualTo(1);
    }
    @Test void unavailableSupplierStillAllowsDisableButNeverEnable(){
        var f=fixture();jdbc.update("UPDATE sup_supplier SET status='SUSPENDED' WHERE id=?",f.supplier());
        manage(f,0,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.DISABLE);
        assertThatThrownBy(()->manage(f,1,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.ENABLE)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        manage(f,1,io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Action.REVOKE_SESSIONS);
    }
    AuthorizationContext.Scope exitGrants(){return AuthorizationContext.open(new PermissionSnapshot(Set.of("supplier:master:view","supplier:master:update","supplier:exit:create","supplier:exit:review"),Map.of("supplier:master",DataScope.all()),Set.of()));}
    ExitModels.View prepareExit(Fixture f){
        long file=f.supplier()+50;
        jdbc.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(?,?,9,'exit.pdf','application/pdf',1,?,'LOCAL','test/exit','ACTIVE')",file,f.tenant(),"e".repeat(64));
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=exitGrants()){
            finance.insert(f.tenant(),f.supplier(),file,"隔离测试人工财务证据",9);finance.review(f.tenant(),f.supplier(),"APPROVED","独立复核",10,0);
            return exits.create(f.supplier(),new ExitModels.Create(ExitModels.Type.NORMAL,"合作结束",Long.toString(file)));
        }
    }
    ExitModels.View approveExit(Fixture f,ExitModels.View application){
        try(var tenant=TenantContext.open(f.tenant(),10);var grants=exitGrants()){
            return exits.review(f.supplier(),Long.parseLong(application.id()),new ExitModels.Review(ExitModels.Decision.APPROVE,"独立核验通过",application.version()));
        }
    }
    String pendingInvite(Fixture f){
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=exitGrants()){
            return invites.issue(f.supplier(),new PortalInvitationModels.Issue(1,"新联系人","next@example.test",24)).token();
        }
    }
    @Test void registrationAndLoginWaitForExitCommitAndCannotLeakAccess()throws Exception{
        for(boolean register:List.of(false,true))exitContention(register,false);
    }
    @Test void registrationAndLoginWaitForExitRollbackAndRecoverOnlyAfterCancellation()throws Exception{
        for(boolean register:List.of(false,true))exitContention(register,true);
    }
    @Test void registrationHoldingSupplierLockFinishesBeforeExitSubmissionAndApprovalRecoversIt()throws Exception{
        var f=fixture();String invitation=pendingInvite(f);long file=f.supplier()+50;
        jdbc.update("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(?,?,9,'exit.pdf','application/pdf',1,?,'LOCAL','test/registration-first-exit','ACTIVE')",file,f.tenant(),"d".repeat(64));
        try(var tenant=TenantContext.open(f.tenant(),9);var grants=exitGrants()){
            finance.insert(f.tenant(),f.supplier(),file,"隔离测试人工财务证据",9);finance.review(f.tenant(),f.supplier(),"APPROVED","独立复核",10,0);
        }
        String trigger="portal_auth_registration_wait";String barrier="portal_auth_registration_"+f.supplier();
        install("CREATE TRIGGER "+trigger+" BEFORE INSERT ON iam_portal_account FOR EACH ROW BEGIN IF NEW.username='concurrent.user' THEN DO GET_LOCK('"+barrier+"',0); DO SLEEP(15); DO RELEASE_LOCK('"+barrier+"'); END IF; END");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var registrationStarted=new java.util.concurrent.CountDownLatch(1);
        try{
            var registration=pool.submit(()->{registrationStarted.countDown();return registrations.register(new PortalRegistrationModels.Register(invitation,"Concurrent.User","Portal#Pass123"));});
            assertThat(registrationStarted.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(awaitRegistrationBarrier(barrier,10)).as("real registration insert reaches the database trigger").isTrue();
            assertThat(supplierRowLockCount()).as("registration transaction retains its supplier row lock while the account insert is blocked; locks="+supplierLockSnapshot()+", engine="+jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sup_supplier'",String.class)).isPositive();
            var exitStarted=new java.util.concurrent.CountDownLatch(1);
            var exitSubmission=pool.submit(()->{
                exitStarted.countDown();
                try(var tenant=TenantContext.open(f.tenant(),9);var grants=exitGrants()){
                    return exits.create(f.supplier(),new ExitModels.Create(ExitModels.Type.NORMAL,"并发退出申请",Long.toString(file)));
                }
            });
            assertThat(exitStarted.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            boolean supplierWait=awaitSupplierLockWait(10);
            if(!supplierWait&&exitSubmission.isDone()){
                try{var early=exitSubmission.get();throw new AssertionError("exit submission completed before the registration transaction released the supplier row: "+early);}
                catch(java.util.concurrent.ExecutionException failure){throw new AssertionError("exit submission failed before reaching the supplier lock",failure.getCause());}
            }
            assertThat(supplierWait).as("exit submission waits behind the registration supplier lock; requestDone="+exitSubmission.isDone()+", trxLockWaits="+lockWaitCount()).isTrue();
            assertThat(registration.isDone()).isFalse();assertThat(exitSubmission.isDone()).isFalse();
            var registered=registration.get(25,java.util.concurrent.TimeUnit.SECONDS);
            drop(trigger);
            var application=exitSubmission.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(application.status()).isEqualTo("SUBMITTED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_account WHERE tenant_id=? AND status='ACTIVE'",Long.class,f.tenant())).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT status FROM sup_portal_invitation WHERE tenant_id=? AND recipient_email='next@example.test'",String.class,f.tenant())).isEqualTo("ACCEPTED");

            approveExit(f,application);
            assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_account WHERE tenant_id=? AND id=?",String.class,f.tenant(),Long.parseLong(registered.accountId()))).isEqualTo("DISABLED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_account WHERE tenant_id=? AND status='ACTIVE'",Long.class,f.tenant())).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM sup_portal_invitation WHERE tenant_id=? AND recipient_email='next@example.test'",String.class,f.tenant())).isEqualTo("ACCEPTED");
            assertThatThrownBy(()->auth.login(new PortalAuthModels.Login(f.code(),"Concurrent.User","Portal#Pass123"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_PORTAL_ACCOUNT_EXIT_RECOVERY'",Long.class,f.tenant())).isEqualTo(2);
        }finally{
            try{drop(trigger);}catch(Exception ignored){}
            pool.shutdownNow();assertThat(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }
    boolean awaitRegistrationBarrier(String barrier,int timeoutSeconds)throws Exception{
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(timeoutSeconds);
        try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.prepareStatement("SELECT IS_USED_LOCK(?) IS NOT NULL")){
            statement.setString(1,barrier);
            while(System.nanoTime()<deadline){
                try(var rows=statement.executeQuery()){rows.next();if(rows.getBoolean(1))return true;}
                Thread.sleep(25);
            }
            return false;
        }
    }
    boolean awaitSupplierLockWait(int timeoutSeconds)throws Exception{
        try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.createStatement()){
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(timeoutSeconds);
            while(System.nanoTime()<deadline){
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND l.ENGINE=w.ENGINE WHERE l.OBJECT_SCHEMA='portal_auth' AND l.OBJECT_NAME='sup_supplier'")){
                    rows.next();if(rows.getLong(1)>0)return true;
                }
                Thread.sleep(25);
            }
            return false;
        }
    }
    long lockWaitCount()throws Exception{
        try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state='LOCK WAIT'")){
            rows.next();return rows.getLong(1);
        }
    }
    long supplierRowLockCount()throws Exception{
        try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM performance_schema.data_locks WHERE OBJECT_SCHEMA='portal_auth' AND OBJECT_NAME='sup_supplier' AND LOCK_STATUS='GRANTED' AND LOCK_TYPE='RECORD'")){
            rows.next();return rows.getLong(1);
        }
    }
    String supplierLockSnapshot()throws Exception{
        try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COALESCE(GROUP_CONCAT(CONCAT(LOCK_TYPE,':',LOCK_MODE,':',LOCK_STATUS,':',COALESCE(INDEX_NAME,'')) ORDER BY LOCK_TYPE),'<none>') FROM performance_schema.data_locks WHERE OBJECT_SCHEMA='portal_auth' AND OBJECT_NAME='sup_supplier'")){
            rows.next();return rows.getString(1);
        }
    }
    void exitContention(boolean register,boolean rollback)throws Exception{
        var f=fixture();String oldToken=login(f).accessToken();String invitation=pendingInvite(f);var application=prepareExit(f);
        var prepared=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var approval=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status->{
                approveExit(f,application);prepared.countDown();
                try{if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("approval release timed out");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                if(rollback)status.setRollbackOnly();return true;
            }));
            assertThat(prepared.await(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var request=pool.submit(()->{
                assertThatThrownBy(()->{
                    if(register)registrations.register(new PortalRegistrationModels.Register(invitation,"Concurrent.User","Portal#Pass123"));
                    else login(f);
                }).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
                return true;
            });
            // Observe an actual InnoDB supplier-row wait, not a timing-only Future assertion.
            try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.createStatement()){
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
                while(System.nanoTime()<deadline){
                    try(var rows=statement.executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND l.ENGINE=w.ENGINE WHERE l.OBJECT_SCHEMA='portal_auth' AND l.OBJECT_NAME='sup_supplier'")){
                        rows.next();if(rows.getLong(1)>0){waiting=true;break;}
                    }
                    Thread.sleep(25);
                }
                assertThat(waiting).as("portal request must wait for the exit supplier lock").isTrue();
            }
            assertThat(request.isDone()).isFalse();release.countDown();
            assertThat(approval.get(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(request.get(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_account WHERE tenant_id=?",Long.class,f.tenant())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE tenant_id=?",Long.class,f.tenant())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE tenant_id=? AND revoked_at IS NULL",Long.class,f.tenant())).isEqualTo(rollback?1:0);
            if(rollback){
                me(oldToken,401); // Pending exit still blocks access despite rollback of approval.
                try(var tenant=TenantContext.open(f.tenant(),9);var grants=exitGrants()){
                    exits.cancel(f.supplier(),Long.parseLong(application.id()),new ExitModels.Version(application.version()));
                }
                me(oldToken,200);
                if(register)registrations.register(new PortalRegistrationModels.Register(invitation,"Concurrent.User","Portal#Pass123"));
                else login(f);
            }else{
                me(oldToken,401);
                assertThat(jdbc.queryForObject("SELECT status FROM sup_supplier WHERE id=?",String.class,f.supplier())).isEqualTo("EXITED");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=? AND status='REVOKED'",Long.class,f.tenant())).isEqualTo(1);
            }
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
    }
    @Test void approvedExitRecoversEveryLocalAccountAndInvitationWithoutClaimingExternalSuccess()throws Exception{
        var f=fixture();String token=login(f).accessToken();login(f);String invitation=pendingInvite(f);
        // Force a second keyset page, including already-disabled accounts.
        for(int n=0;n<100;n++)jdbc.update("INSERT INTO iam_portal_account(id,tenant_id,supplier_id,username,display_name,email,password_hash,status) SELECT ?,tenant_id,supplier_id,?,'批量账号',?,password_hash,? FROM iam_portal_account WHERE id=?",900000000L+f.supplier()*1000+n,"batch."+n,"batch"+n+"@example.test",n%2==0?"ACTIVE":"DISABLED",f.account());
        long otherSupplier=f.supplier()+20,otherAccount=f.supplier()+21;
        jdbc.update("INSERT INTO sup_supplier(id,tenant_id,organization_id,supplier_code,supplier_name,supplier_type,created_by,updated_by) VALUES(?,?,?,'OTHER','其他供应商','MANUFACTURER',9,9)",otherSupplier,f.tenant(),f.supplier()+1);
        jdbc.update("INSERT INTO iam_portal_account(id,tenant_id,supplier_id,username,display_name,email,password_hash) SELECT ?,tenant_id,?,'other.user','其他联系人','other@example.test',password_hash FROM iam_portal_account WHERE id=?",otherAccount,otherSupplier,f.account());
        var other=auth.login(new PortalAuthModels.Login(f.code(),"other.user","Portal#Pass123"));
        var application=prepareExit(f);var closed=approveExit(f,application);
        assertThat(closed.status()).isEqualTo("BUSINESS_CLOSED");assertThat(closed.result().accessRecoveryStatus()).isEqualTo("NOT_VERIFIED");
        assertThat(closed.events()).anySatisfy(event->{assertThat(event.action()).isEqualTo("LOCAL_PORTAL_RECOVERY");assertThat(event.comment()).contains("处理账号 101","撤销会话记录 2","撤销有效未消费邀请 1");});
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_account WHERE tenant_id=? AND supplier_id=? AND status='DISABLED' AND version=1 AND token_version=1",Long.class,f.tenant(),f.supplier())).isEqualTo(101);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_PORTAL_ACCOUNT_EXIT_RECOVERY'",Long.class,f.tenant())).isEqualTo(101);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=? AND supplier_id=? AND status='REVOKED' AND revoked_by=10",Long.class,f.tenant(),f.supplier())).isEqualTo(1);
        me(token,401);me(other.accessToken(),200);
        try(var tenant=TenantContext.open(f.tenant(),10);var grants=exitGrants()){
            assertThat(archives.get(f.supplier(),Long.parseLong(application.id())).integrityVerified()).isTrue();
        }
        // Counterfactual isolated-DB restore proves permanent revocation, not just the EXITED gate.
        jdbc.update("UPDATE sup_supplier SET status='DRAFT' WHERE id=?",f.supplier());me(token,401);
        assertThatThrownBy(()->registrations.register(new PortalRegistrationModels.Register(invitation,"Cannot.Rejoin","Portal#Pass123"))).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
        assertThatThrownBy(()->approveExit(f,application)).isInstanceOf(io.github.turbopro.ism.common.api.error.ApiException.class);
    }
    @Test void exitAuditFailureRollsBackAccountsInvitationsApprovalAndArchive()throws Exception{
        var f=fixture();String token=login(f).accessToken();pendingInvite(f);var application=prepareExit(f);
        install("CREATE TRIGGER portal_auth_audit_failure BEFORE INSERT ON sys_audit_event FOR EACH ROW BEGIN IF NEW.tenant_id="+f.tenant()+" AND NEW.action='SUPPLIER_EXIT_BUSINESS_CLOSE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected exit final audit failure'; END IF; END");
        try{assertThatThrownBy(()->approveExit(f,application)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("injected exit final audit failure");}finally{drop();}
        assertThat(jdbc.queryForObject("SELECT status FROM sup_supplier WHERE id=?",String.class,f.supplier())).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT status FROM sup_exit_application WHERE id=?",String.class,Long.parseLong(application.id()))).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_portal_session WHERE account_id=? AND revoked_at IS NULL",Long.class,f.account())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=? AND supplier_id=? AND status='PENDING'",Long.class,f.tenant(),f.supplier())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_result WHERE application_id=?",Long.class,Long.parseLong(application.id()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_archive WHERE application_id=?",Long.class,Long.parseLong(application.id()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_access_recovery_task WHERE application_id=?",Long.class,Long.parseLong(application.id()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_exit_event WHERE application_id=? AND action='LOCAL_PORTAL_RECOVERY'",Long.class,Long.parseLong(application.id()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_PORTAL_ACCOUNT_EXIT_RECOVERY'",Long.class,f.tenant())).isZero();
        approveExit(f,application);me(token,401);
    }
    @Test void cancellingOrRejectingExitNeverPermanentlyRecoversLocalAccounts()throws Exception{
        for(boolean reject:List.of(false,true)){
            var f=fixture();String token=login(f).accessToken();pendingInvite(f);var application=prepareExit(f);
            assertThat(jdbc.queryForObject("SELECT version FROM iam_portal_account WHERE id=?",Integer.class,f.account())).isZero();
            try(var tenant=TenantContext.open(f.tenant(),reject?10:9);var grants=exitGrants()){
                if(reject)exits.review(f.supplier(),Long.parseLong(application.id()),new ExitModels.Review(ExitModels.Decision.REJECT,"继续合作",application.version()));
                else exits.cancel(f.supplier(),Long.parseLong(application.id()),new ExitModels.Version(application.version()));
            }
            me(token,200);
            assertThat(jdbc.queryForObject("SELECT status FROM iam_portal_account WHERE id=?",String.class,f.account())).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sup_portal_invitation WHERE tenant_id=? AND supplier_id=? AND status='PENDING'",Long.class,f.tenant(),f.supplier())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action='SUPPLIER_PORTAL_ACCOUNT_EXIT_RECOVERY'",Long.class,f.tenant())).isZero();
        }
    }
    void install(String trigger)throws Exception{var result=MYSQL.execInContainer("mysql","--delimiter=//","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e",trigger+"//");assertThat(result.getExitCode()).as(result.getStderr()).isZero();}
    void drop()throws Exception{var result=MYSQL.execInContainer("mysql","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e","DROP TRIGGER portal_auth_audit_failure");assertThat(result.getExitCode()).as(result.getStderr()).isZero();}
    void drop(String trigger)throws Exception{var result=MYSQL.execInContainer("mysql","-uroot","-p"+MYSQL.getPassword(),MYSQL.getDatabaseName(),"-e","DROP TRIGGER IF EXISTS "+trigger);assertThat(result.getExitCode()).as(result.getStderr()).isZero();}
}

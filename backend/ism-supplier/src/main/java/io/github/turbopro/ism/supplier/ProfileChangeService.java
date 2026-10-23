package io.github.turbopro.ism.supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;

@Service
public class ProfileChangeService {
    private final ProfileChangeMapper mapper;private final SupplierMapper suppliers;private final PortalWriteGuard guard;
    private final OperationIdGenerator ids;private final AuditService audit;private final ObjectMapper json;
    public ProfileChangeService(ProfileChangeMapper mapper,SupplierMapper suppliers,PortalWriteGuard guard,OperationIdGenerator ids,AuditService audit,ObjectMapper json){this.mapper=mapper;this.suppliers=suppliers;this.guard=guard;this.ids=ids;this.audit=audit;this.json=json;}
    @Transactional(readOnly=true) public ProfileChangeModels.Page portalList(int page,int size){var i=PortalIdentityContext.require();return page(i.tenantId(),i.supplierId(),i.accountId(),page,size,false);}
    @Transactional(readOnly=true) public ProfileChangeModels.Page employeeList(long supplierId,int page,int size){employee(supplierId,false);return page(TenantContext.require().tenantId(),supplierId,0,page,size,AuthorizationContext.require().hasAction("supplier:change:review"));}
    @Transactional public ProfileChangeModels.View submit(ProfileChangeModels.Submit command){
        guard.lockCurrent();var i=PortalIdentityContext.require();var supplier=suppliers.findForNewBusiness(i.tenantId(),i.supplierId());
        var existing=mapper.byKey(i.tenantId(),i.supplierId(),command.requestKey());
        if(existing!=null){
            if(existing.createdBy()!=i.accountId()||existing.baseVersion()!=command.baseVersion()||!read(existing.afterJson()).equals(command.fields())||!existing.reason().equals(command.reason().trim()))throw conflict("提交标识已被其他内容使用");
            return view(existing,i.accountId());
        }
        if(supplier.version()!=command.baseVersion())throw conflict("企业档案已更新，请刷新后重新填写");
        var before=ProfileChangeModels.fields(supplier);
        if(before.equals(command.fields()))throw invalid("没有实际资料变更");
        validateWebsite(command.fields().website());
        long id=ids.nextId();
        try{mapper.insert(id,i.tenantId(),i.supplierId(),command.requestKey(),supplier.version(),write(before),write(command.fields()),command.reason().trim(),i.accountId());}
        catch(DuplicateKeyException duplicate){throw conflict("本企业已有待审核资料变更，请先处理或撤回");}
        record("SUPPLIER_PROFILE_CHANGE_SUBMIT",id,i.supplierId());return view(mapper.lock(i.tenantId(),i.supplierId(),id),i.accountId());
    }
    @Transactional public ProfileChangeModels.View cancel(long id,int version){
        guard.lockCurrent();var i=PortalIdentityContext.require();var row=require(i.tenantId(),i.supplierId(),id,version);
        if(row.createdBy()!=i.accountId())throw new ApiException(CommonErrorCode.FORBIDDEN,"只能撤回本人提交的申请");
        finish(i.tenantId(),i.supplierId(),id,"CANCELLED","门户申请人撤回",version);record("SUPPLIER_PROFILE_CHANGE_CANCEL",id,i.supplierId());
        return view(mapper.lock(i.tenantId(),i.supplierId(),id),i.accountId());
    }
    @Transactional public ProfileChangeModels.View review(long supplierId,long id,ProfileChangeModels.Review command){
        var supplier=employee(supplierId,true);var i=TenantContext.require();var row=require(i.tenantId(),supplierId,id,command.version());
        if(command.decision()==ProfileChangeModels.Decision.APPROVE){
            if(!Set.of("DRAFT","ACTIVE").contains(supplier.status())||!suppliers.currentPendingExits(i.tenantId(),supplierId).isEmpty())throw invalid("企业不可用或正在退出，不能批准资料变更");
            if(supplier.version()!=row.baseVersion()||!ProfileChangeModels.fields(supplier).equals(read(row.beforeJson())))throw conflict("企业档案已更新，请驳回后重新申请，不能覆盖当前档案");
            if(mapper.apply(i.tenantId(),supplierId,i.actorId(),row.baseVersion(),read(row.afterJson()))!=1)throw conflict("企业档案已更新");
        }
        finish(i.tenantId(),supplierId,id,command.decision()==ProfileChangeModels.Decision.APPROVE?"APPROVED":"REJECTED",command.comment().trim(),command.version());
        record("SUPPLIER_PROFILE_CHANGE_"+command.decision(),id,supplierId);return view(mapper.lock(i.tenantId(),supplierId,id),0);
    }
    private SupplierModels.SupplierRow employee(long supplierId,boolean review){
        var grants=AuthorizationContext.require();if(!grants.hasAction(review?"supplier:change:review":"supplier:master:view"))throw new ApiException(CommonErrorCode.FORBIDDEN);
        var i=TenantContext.require();var row=review?suppliers.findForNewBusiness(i.tenantId(),supplierId):suppliers.find(i.tenantId(),supplierId);
        if(row==null||!grants.dataScope("supplier:master").allows(new DataTarget(row.organizationId(),null,row.createdBy(),row.createdBy()),i.actorId()))throw new ApiException(CommonErrorCode.NOT_FOUND);
        return row;
    }
    private ProfileChangeModels.Row require(long tenant,long supplier,long id,int version){var row=mapper.lock(tenant,supplier,id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);if(row.version()!=version||!"SUBMITTED".equals(row.status()))throw conflict("申请已被处理，请刷新");return row;}
    private void finish(long tenant,long supplier,long id,String state,String comment,int version){if(mapper.finish(tenant,supplier,id,state,TenantContext.require().actorId(),comment,version)!=1)throw conflict("申请已被处理");}
    private ProfileChangeModels.Page page(long tenant,long supplier,long actor,int page,int size,boolean canReview){if(page<0||page>10000||size<1||size>100)throw invalid("分页参数无效");return new ProfileChangeModels.Page(mapper.count(tenant,supplier),page,size,canReview,mapper.list(tenant,supplier,Math.multiplyExact(page,size),size).stream().map(r->view(r,actor)).toList());}
    private ProfileChangeModels.View view(ProfileChangeModels.Row r,long actor){return new ProfileChangeModels.View(Long.toString(r.id()),r.requestKey(),r.baseVersion(),read(r.beforeJson()),read(r.afterJson()),r.reason(),r.status(),r.createdAt(),r.reviewedAt(),r.reviewComment(),r.version(),actor==r.createdBy()&&"SUBMITTED".equals(r.status()));}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private ProfileChangeModels.Fields read(String value){try{return json.readValue(value,ProfileChangeModels.Fields.class);}catch(JsonProcessingException e){throw new IllegalStateException("Invalid persisted profile change",e);}}
    private void record(String action,long id,long supplier){audit.append(new AuditService.AuditCommand(action,"SUPPLIER_PROFILE_CHANGE",id,null,Map.of(),Map.of("supplierId",supplier),null,null));}
    private void validateWebsite(String value){if(value.isEmpty())return;try{var uri=URI.create(value);if(("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))&&uri.getHost()!=null&&uri.getUserInfo()==null)return;}catch(IllegalArgumentException ignored){}throw invalid("企业网站须为有效的 http/https 地址");}
    private ApiException conflict(String text){return new ApiException(CommonErrorCode.CONFLICT,text);}
    private ApiException invalid(String text){return new ApiException(CommonErrorCode.VALIDATION_FAILED,text);}
}

package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class BlacklistService {
    private final BlacklistMapper mapper;private final SupplierService suppliers;
    private final OperationIdGenerator ids;private final AuditService audit;
    public BlacklistService(BlacklistMapper mapper,SupplierService suppliers,OperationIdGenerator ids,AuditService audit){
        this.mapper=mapper;this.suppliers=suppliers;this.ids=ids;this.audit=audit;
    }
    public BlacklistModels.Page list(String supplierId,String status,int page,int size){
        var i=TenantContext.require();var scope=scope();Long supplier=supplierId==null||supplierId.isBlank()?null:id(supplierId);
        String state=null;if(status!=null&&!status.isBlank())try{state=BlacklistModels.Status.valueOf(status).name();}catch(Exception e){throw invalid("黑名单状态无效");}
        return new BlacklistModels.Page(mapper.count(i.tenantId(),supplier,state,scope.type().name(),scope.organizationIds(),i.actorId()),page,size,
            mapper.list(i.tenantId(),supplier,state,scope.type().name(),scope.organizationIds(),i.actorId(),Math.multiplyExact(page,size),size).stream().map(this::view).toList());
    }
    public BlacklistModels.View get(long caseId){return view(require(caseId));}
    @Transactional public BlacklistModels.View create(BlacklistModels.Create command){
        validateDates(command);
        var supplier=suppliers.get(id(command.supplierId()));
        if(supplier.status()==SupplierModels.Status.EXITED)throw invalid("已退出供应商不能发起黑名单");
        var i=TenantContext.require();long supplierId=id(command.supplierId());
        if(!scope().allows(new DataTarget(Long.parseLong(supplier.organizationId()),null,i.actorId(),i.actorId()),i.actorId()))throw new ApiException(CommonErrorCode.FORBIDDEN);
        // Serialize proposals for one supplier so concurrent drafts cannot bypass the open-case check.
        mapper.lockSupplier(i.tenantId(),supplierId);
        if(mapper.openCase(i.tenantId(),supplierId,RestrictionBusinessDate.today(),command.restrictionType().name())>0)throw new ApiException(CommonErrorCode.CONFLICT,"该供应商已有未结束的限制申请");
        long caseId=ids.nextId();mapper.insert(caseId,i.tenantId(),supplierId,Long.parseLong(supplier.organizationId()),supplier.code(),supplier.name(),command.restrictionType().name(),command.effectiveFrom(),command.effectiveUntil(),command.reason().trim(),blank(command.sourceRef()),i.actorId());
        event(caseId,"CREATE",null,"DRAFT",command.reason().trim());audit("SUPPLIER_BLACKLIST_CREATE",caseId,"DRAFT");
        return view(mapper.get(i.tenantId(),caseId));
    }
    @Transactional public BlacklistModels.View submit(long caseId,BlacklistModels.Version command){
        var before=require(caseId);if(!before.status().equals("DRAFT"))throw invalid("只有草稿可以提交审核");var i=TenantContext.require();
        if(mapper.submit(i.tenantId(),caseId,i.actorId(),command.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        event(caseId,"SUBMIT","DRAFT","SUBMITTED",null);audit("SUPPLIER_BLACKLIST_SUBMIT",caseId,"SUBMITTED");return view(mapper.get(i.tenantId(),caseId));
    }
    @Transactional public BlacklistModels.View review(long caseId,BlacklistModels.Review command){
        var before=require(caseId);if(!before.status().equals("SUBMITTED"))throw invalid("只有已提交申请可以审核");var i=TenantContext.require();
        if(before.createdBy()==i.actorId())throw invalid("申请人不能审核自己的黑名单申请");
        if(command.decision()==BlacklistModels.Decision.APPROVE && before.restrictionType()==BlacklistModels.RestrictionType.TEMPORARY
                && before.effectiveUntil().isBefore(RestrictionBusinessDate.today()))throw invalid("限制期限已过，不能批准");
        String next=command.decision()==BlacklistModels.Decision.APPROVE?"APPROVED":"REJECTED";
        if(command.decision()==BlacklistModels.Decision.APPROVE)mapper.lockSupplier(i.tenantId(),before.supplierId());
        if(mapper.review(i.tenantId(),caseId,command.decision().name(),command.comment().trim(),i.actorId(),command.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        event(caseId,"REVIEW_"+command.decision(),"SUBMITTED",next,command.comment().trim());audit("SUPPLIER_BLACKLIST_REVIEW",caseId,next);return view(mapper.get(i.tenantId(),caseId));
    }
    @Transactional public BlacklistModels.View revoke(long caseId,BlacklistModels.Revoke command){
        var before=require(caseId);if(!before.status().equals("APPROVED"))throw invalid("只有生效中的黑名单可以撤销");var i=TenantContext.require();
        if(mapper.revoke(i.tenantId(),caseId,command.reason().trim(),i.actorId(),command.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        event(caseId,"REVOKE","APPROVED","REVOKED",command.reason().trim());audit("SUPPLIER_BLACKLIST_REVOKE",caseId,"REVOKED");return view(mapper.get(i.tenantId(),caseId));
    }
    private BlacklistModels.Row require(long id){var i=TenantContext.require();var row=mapper.get(i.tenantId(),id);
        if(row==null||!scope().allows(new DataTarget(row.organizationId(),null,null,row.createdBy()),i.actorId()))throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
    private DataScope scope(){return AuthorizationContext.require().dataScope("supplier:master");}
    private BlacklistModels.View view(BlacklistModels.Row r){var i=TenantContext.require();return new BlacklistModels.View(Long.toString(r.id()),Long.toString(r.supplierId()),Long.toString(r.organizationId()),r.supplierCode(),r.supplierName(),r.restrictionType(),r.effectiveFrom(),r.effectiveUntil(),effective(r),r.reason(),r.sourceRef(),BlacklistModels.Status.valueOf(r.status()),r.reviewComment(),str(r.reviewedBy()),r.reviewedAt(),r.revokedReason(),str(r.revokedBy()),r.revokedAt(),Long.toString(r.createdBy()),r.version(),r.createdAt(),r.updatedAt(),mapper.events(i.tenantId(),r.id()).stream().map(e->new BlacklistModels.Event(Long.toString(e.id()),e.action(),e.fromStatus(),e.toStatus(),e.comment(),Long.toString(e.actorId()),e.createdAt())).toList());}
    private boolean effective(BlacklistModels.Row row){if(!"APPROVED".equals(row.status()))return false;if(row.restrictionType()!=BlacklistModels.RestrictionType.TEMPORARY)return true;var today=RestrictionBusinessDate.today();return !today.isBefore(row.effectiveFrom())&&!today.isAfter(row.effectiveUntil());}
    private void validateDates(BlacklistModels.Create command){
        if(command.restrictionType()!=BlacklistModels.RestrictionType.TEMPORARY){
            if(command.effectiveFrom()!=null||command.effectiveUntil()!=null)throw invalid("长期黑名单和观察名单不填写生效日期");
        }else if(command.effectiveFrom()==null||command.effectiveUntil()==null||command.effectiveFrom().isAfter(command.effectiveUntil())||command.effectiveUntil().isBefore(RestrictionBusinessDate.today()))
            throw invalid("限期限制必须填写有效的起止日期，结束日不能早于今天");
    }
    private void event(long caseId,String action,String from,String to,String comment){var i=TenantContext.require();mapper.event(ids.nextId(),i.tenantId(),caseId,action,from,to,comment,i.actorId());}
    private void audit(String action,long id,String status){audit.append(new AuditService.AuditCommand(action,"SUPPLIER_BLACKLIST",id,null,Map.of(),Map.of("status",status),null,null));}
    private long id(String value){try{long parsed=Long.parseLong(value);if(parsed<=0)throw new NumberFormatException();return parsed;}catch(Exception e){throw invalid("供应商 ID 无效");}}
    private String str(Long value){return value==null?null:Long.toString(value);}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private ApiException invalid(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
}

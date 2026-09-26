package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class AppealService {
    private final AppealMapper mapper;
    private final BlacklistMapper restrictions;
    private final BlacklistService cases;
    private final AppealEvidenceVerifier evidence;
    private final OperationIdGenerator ids;
    private final AuditService audit;
    public AppealService(AppealMapper mapper,BlacklistMapper restrictions,BlacklistService cases,
                        AppealEvidenceVerifier evidence,OperationIdGenerator ids,AuditService audit){
        this.mapper=mapper;this.restrictions=restrictions;this.cases=cases;this.evidence=evidence;this.ids=ids;this.audit=audit;
    }
    public AppealModels.Page list(long caseId,int page,int size){
        cases.get(caseId);
        var i=TenantContext.require();
        return new AppealModels.Page(mapper.count(i.tenantId(),caseId),page,size,
            mapper.list(i.tenantId(),caseId,Math.multiplyExact(page,size),size).stream().map(this::view).toList());
    }
    @Transactional public AppealModels.View create(long caseId,AppealModels.Create command){
        var restriction=lockCase(caseId);
        if(!restriction.status().equals("APPROVED")||restrictions.lifted(TenantContext.require().tenantId(),caseId)>0)throw invalid("只能对已批准且未解除的限制记录申诉");
        long fileId=parseId(command.evidenceFileId());
        if(!evidence.available(fileId))throw invalid("申诉证据不存在或不可用");
        var i=TenantContext.require();long id=ids.nextId();
        try{mapper.insert(id,i.tenantId(),caseId,command.reason().trim(),fileId,i.actorId());}
        catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"该限制已有待处理申诉");}
        recordAudit("RESTRICTION_APPEAL_SUBMIT",id,Map.of("caseId",caseId,"evidenceFileId",fileId,"status","SUBMITTED"));
        return view(mapper.get(i.tenantId(),caseId,id));
    }
    @Transactional public AppealModels.View review(long caseId,long appealId,AppealModels.Review command){
        var restriction=lockCase(caseId);
        var i=TenantContext.require();var before=mapper.get(i.tenantId(),caseId,appealId);
        if(before==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(!before.status().equals("SUBMITTED"))throw invalid("申诉已有处理结论，不能再次复核");
        if(before.createdBy()==i.actorId() || (restriction.reviewedBy()!=null && restriction.reviewedBy()==i.actorId()))
            throw invalid("申诉提交人和原限制批准人不能复核申诉");
        if(command.decision()==AppealModels.Decision.ACCEPT && !evidence.available(before.evidenceFileId()))
            throw invalid("申诉证据已不可用，不能确认成立");
        String next=command.decision()==AppealModels.Decision.ACCEPT?"ACCEPTED":"REJECTED";
        if(mapper.review(i.tenantId(),caseId,appealId,next,command.comment().trim(),i.actorId(),command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        recordAudit("RESTRICTION_APPEAL_"+command.decision(),appealId,
            Map.of("caseId",caseId,"status",next,"comment",command.comment().trim()));
        // Neither submission nor a favorable appeal conclusion changes the restriction.
        return view(mapper.get(i.tenantId(),caseId,appealId));
    }
    private BlacklistModels.Row lockCase(long caseId){
        var visible=cases.get(caseId);var i=TenantContext.require();
        restrictions.lockSupplier(i.tenantId(),Long.parseLong(visible.supplierId()));
        var row=restrictions.currentCase(i.tenantId(),caseId);
        if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        return row;
    }
    private AppealModels.View view(AppealModels.Row row){
        return new AppealModels.View(Long.toString(row.id()),Long.toString(row.caseId()),row.reason(),Long.toString(row.evidenceFileId()),
            row.status(),Long.toString(row.createdBy()),row.createdAt(),row.reviewedBy()==null?null:row.reviewedBy().toString(),
            row.reviewedAt(),row.reviewComment(),row.version());
    }
    private long parseId(String value){try{long id=Long.parseLong(value);if(id>0)return id;}catch(NumberFormatException ignored){}
        throw invalid("证据文件 ID 无效");}
    private ApiException invalid(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
    private void recordAudit(String action,long id,Map<String,Object> after){
        audit.append(new AuditService.AuditCommand(action,"RESTRICTION_APPEAL",id,null,Map.of(),after,null,null));
    }
}

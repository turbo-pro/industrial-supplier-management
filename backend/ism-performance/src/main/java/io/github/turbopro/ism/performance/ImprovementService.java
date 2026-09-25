package io.github.turbopro.ism.performance;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import io.github.turbopro.ism.resource.file.FileReferenceService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;

@Service
public class ImprovementService {
    private final ImprovementMapper mapper;
    private final PerformanceService evaluations;
    private final FileReferenceService files;
    private final OperationIdGenerator ids;
    private final AuditService audit;
    public ImprovementService(ImprovementMapper mapper,PerformanceService evaluations,FileReferenceService files,
                              OperationIdGenerator ids,AuditService audit) {
        this.mapper=mapper;this.evaluations=evaluations;this.files=files;this.ids=ids;this.audit=audit;
    }
    public ImprovementModels.View get(long evaluationId) {
        evaluations.get(evaluationId);
        var identity=TenantContext.require();
        var row=mapper.get(identity.tenantId(),evaluationId);
        return row==null?null:view(row);
    }
    @Transactional public ImprovementModels.View create(long evaluationId,ImprovementModels.Create command) {
        var evaluation=evaluations.get(evaluationId);
        if(evaluation.status()!=PerformanceModels.Status.APPROVED)throw invalid("只有已批准的绩效评价可以发起改进");
        if(command.dueDate().isBefore(LocalDate.now()))throw invalid("整改期限不能早于今天");
        var identity=TenantContext.require();long id=ids.nextId();
        try {mapper.insert(id,identity.tenantId(),evaluationId,command.rootCause().trim(),command.actionPlan().trim(),command.dueDate(),identity.actorId());}
        catch(DuplicateKeyException exception){throw new ApiException(CommonErrorCode.CONFLICT,"该评价已有改进计划");}
        event(id,"CREATE",null,"OPEN",null);audit("PERFORMANCE_IMPROVEMENT_CREATE",id,"OPEN");
        return view(mapper.get(identity.tenantId(),evaluationId));
    }
    @Transactional public ImprovementModels.View submit(long evaluationId,ImprovementModels.Submit command) {
        var before=require(evaluationId);
        if(!before.status().equals("OPEN")&&!before.status().equals("REWORK"))throw invalid("当前状态不能提交改进结果");
        long fileId=parseId(command.evidenceFileId());
        if(!files.active(fileId))throw invalid("证据文件不存在或不可用");
        var identity=TenantContext.require();
        if(mapper.submit(identity.tenantId(),evaluationId,command.completionNote().trim(),fileId,identity.actorId(),command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        event(before.id(),"SUBMIT",before.status(),"SUBMITTED",command.completionNote().trim());
        audit("PERFORMANCE_IMPROVEMENT_SUBMIT",before.id(),"SUBMITTED");
        return view(mapper.get(identity.tenantId(),evaluationId));
    }
    @Transactional public ImprovementModels.View review(long evaluationId,ImprovementModels.Review command) {
        var before=require(evaluationId);
        if(!before.status().equals("SUBMITTED"))throw invalid("只有已提交的改进结果可以验收");
        var identity=TenantContext.require();
        if(before.createdBy()==identity.actorId())throw invalid("改进计划创建人不能验收自己的结果");
        String next=command.decision()==ImprovementModels.Decision.ACCEPT?"ACCEPTED":"REWORK";
        if(mapper.review(identity.tenantId(),evaluationId,command.decision().name(),command.comment().trim(),identity.actorId(),command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        event(before.id(),"REVIEW_"+command.decision(),"SUBMITTED",next,command.comment().trim());
        audit("PERFORMANCE_IMPROVEMENT_REVIEW",before.id(),next);
        return view(mapper.get(identity.tenantId(),evaluationId));
    }
    private ImprovementModels.Row require(long evaluationId) {
        evaluations.get(evaluationId);
        var identity=TenantContext.require();var row=mapper.get(identity.tenantId(),evaluationId);
        if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        return row;
    }
    private ImprovementModels.View view(ImprovementModels.Row row) {
        var identity=TenantContext.require();
        return new ImprovementModels.View(Long.toString(row.id()),Long.toString(row.evaluationId()),row.rootCause(),row.actionPlan(),row.dueDate(),
            ImprovementModels.Status.valueOf(row.status()),row.completionNote(),row.evidenceFileId()==null?null:Long.toString(row.evidenceFileId()),
            row.reviewComment(),row.reviewedBy()==null?null:Long.toString(row.reviewedBy()),row.reviewedAt(),Long.toString(row.createdBy()),
            row.version(),row.createdAt(),row.updatedAt(),mapper.events(identity.tenantId(),row.id()).stream().map(e->
                new ImprovementModels.Event(Long.toString(e.id()),e.action(),e.fromStatus(),e.toStatus(),e.comment(),Long.toString(e.actorId()),e.createdAt())).toList());
    }
    private void event(long planId,String action,String from,String to,String comment) {
        var identity=TenantContext.require();mapper.event(ids.nextId(),identity.tenantId(),planId,action,from,to,comment,identity.actorId());
    }
    private void audit(String action,long id,String status) {
        audit.append(new AuditService.AuditCommand(action,"PERFORMANCE_IMPROVEMENT",id,null,Map.of(),Map.of("status",status),null,null));
    }
    private long parseId(String value){try{long id=Long.parseLong(value);if(id<=0)throw new NumberFormatException();return id;}
        catch(NumberFormatException exception){throw invalid("证据文件 ID 无效");}}
    private ApiException invalid(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
}

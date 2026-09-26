package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class LiftService {
    private static final Set<String> REQUIRED=Set.of("OPEN_QUALITY","OPEN_SAFETY","OPEN_IMPROVEMENT");
    private final LiftMapper mapper;private final BlacklistMapper restrictions;private final BlacklistService cases;
    private final AppealEvidenceVerifier evidence;private final List<SupplierLiftCheck> checks;
    private final OperationIdGenerator ids;private final AuditService audit;private final Duration watchPeriod;
    public LiftService(LiftMapper mapper,BlacklistMapper restrictions,BlacklistService cases,AppealEvidenceVerifier evidence,
        List<SupplierLiftCheck> checks,OperationIdGenerator ids,AuditService audit,
        @Value("${ism.restriction.lift.watch-period:P7D}") Duration watchPeriod){
        if(watchPeriod.isNegative())throw new IllegalArgumentException("Observation period cannot be negative");
        this.mapper=mapper;this.restrictions=restrictions;this.cases=cases;this.evidence=evidence;
        this.checks=List.copyOf(checks);this.ids=ids;this.audit=audit;this.watchPeriod=watchPeriod;
    }
    public LiftModels.Page list(long caseId,int page,int size){
        cases.get(caseId);var i=TenantContext.require();
        return new LiftModels.Page(mapper.count(i.tenantId(),caseId),page,size,
            mapper.list(i.tenantId(),caseId,Math.multiplyExact(page,size),size).stream().map(this::view).toList());
    }
    @Transactional public LiftModels.Readiness readiness(long caseId){return readiness(lockCase(caseId));}
    @Transactional public LiftModels.View create(long caseId,LiftModels.Create command){
        var row=lockCase(caseId);if(!row.status().equals("APPROVED")||restrictions.lifted(TenantContext.require().tenantId(),caseId)>0)
            throw invalid("限制未批准或已解除，不能新增解除申请");
        long fileId=parseId(command.evidenceFileId());if(!evidence.available(fileId))throw invalid("解除证据不存在或不可用");
        var i=TenantContext.require();long id=ids.nextId();
        try{mapper.insert(id,i.tenantId(),caseId,command.reason().trim(),fileId,i.actorId());}
        catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"已有待处理解除申请");}
        audit("RESTRICTION_LIFT_SUBMIT",id,Map.of("caseId",caseId,"evidenceFileId",fileId,"reason",command.reason().trim()));
        return view(mapper.get(i.tenantId(),caseId,id));
    }
    @Transactional public LiftModels.View review(long caseId,long id,LiftModels.Review command){
        var restriction=lockCase(caseId);var i=TenantContext.require();var before=mapper.get(i.tenantId(),caseId,id);
        if(before==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(!before.status().equals("SUBMITTED"))throw invalid("解除申请已有处理结论");
        if(before.createdBy()==i.actorId())throw invalid("申请人不能审批自己的解除申请");
        String next=command.decision()==LiftModels.Decision.APPROVE?"APPROVED":"REJECTED";
        if(command.decision()==LiftModels.Decision.APPROVE){
            if(!evidence.available(before.evidenceFileId()))throw invalid("解除证据已不可用");
            if(!readiness(restriction).ready())throw invalid("解除前核验未通过，请处理整改或等待观察期结束");
        }
        if(mapper.review(i.tenantId(),caseId,id,next,command.comment().trim(),i.actorId(),command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        if(command.decision()==LiftModels.Decision.APPROVE)
            mapper.result(ids.nextId(),i.tenantId(),caseId,id,i.actorId(),command.comment().trim());
        audit("RESTRICTION_LIFT_"+command.decision(),id,Map.of("caseId",caseId,"status",next,"comment",command.comment().trim()));
        return view(mapper.get(i.tenantId(),caseId,id));
    }
    private LiftModels.Readiness readiness(BlacklistModels.Row row){
        var i=TenantContext.require();var blockers=new ArrayList<SupplierExitCheck.Blocker>();
        if(!row.status().equals("APPROVED")||restrictions.lifted(i.tenantId(),row.id())>0)
            blockers.add(new SupplierExitCheck.Blocker("LIFT_STATE","限制未批准或已解除",1,"/suppliers/blacklist"));
        Instant ends=null;
        if(row.restrictionType()==BlacklistModels.RestrictionType.WATCH){
            Long approved=restrictions.approvedAtMillis(i.tenantId(),row.id());
            if(approved==null)blockers.add(new SupplierExitCheck.Blocker("OBSERVATION_UNKNOWN","观察期起点无法核验",1,"/suppliers/blacklist"));
            else{ends=Instant.ofEpochMilli(approved).plus(watchPeriod);if(Instant.now().isBefore(ends))
                blockers.add(new SupplierExitCheck.Blocker("OBSERVATION_OPEN","观察期尚未结束",1,"/suppliers/blacklist"));}
        }
        var found=new HashSet<String>();
        for(var check:checks)for(var blocker:check.blockers(row.supplierId()))if(REQUIRED.contains(blocker.code())){
            if(!found.add(blocker.code())||blocker.count()<0)throw invalid("解除核验能力配置冲突");
            blockers.add(blocker);
        }
        for(var code:REQUIRED)if(!found.contains(code))
            blockers.add(new SupplierExitCheck.Blocker("MISSING_"+code,"整改核验能力未配置："+code,1,"/suppliers/blacklist"));
        return new LiftModels.Readiness(blockers.stream().allMatch(b->b.count()==0),ends,List.copyOf(blockers));
    }
    private BlacklistModels.Row lockCase(long caseId){
        var visible=cases.get(caseId);var i=TenantContext.require();restrictions.lockSupplier(i.tenantId(),Long.parseLong(visible.supplierId()));
        var row=restrictions.currentCase(i.tenantId(),caseId);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;
    }
    private LiftModels.View view(LiftModels.Row row){return new LiftModels.View(Long.toString(row.id()),Long.toString(row.caseId()),row.reason(),
        Long.toString(row.evidenceFileId()),row.status(),Long.toString(row.createdBy()),row.createdAt(),
        row.reviewedBy()==null?null:row.reviewedBy().toString(),row.reviewedAt(),row.reviewComment(),row.version());}
    private ApiException invalid(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
    private long parseId(String value){try{long id=Long.parseLong(value);if(id>0)return id;}catch(NumberFormatException ignored){}throw invalid("证据文件 ID 无效");}
    private void audit(String action,long id,Map<String,Object> after){audit.append(new AuditService.AuditCommand(action,"RESTRICTION_LIFT",id,null,Map.of(),after,null,null));}
}

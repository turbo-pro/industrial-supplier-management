package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ExitApplicationService {
    private final ExitMapper mapper;private final SupplierMapper suppliers;private final SupplierService scope;
    private final AppealEvidenceVerifier evidence;private final List<SupplierExitCheck> checks;
    private final OperationIdGenerator ids;private final AuditService audit;
    public ExitApplicationService(ExitMapper mapper,SupplierMapper suppliers,SupplierService scope,
        AppealEvidenceVerifier evidence,List<SupplierExitCheck> checks,OperationIdGenerator ids,AuditService audit){
        this.mapper=mapper;this.suppliers=suppliers;this.scope=scope;this.evidence=evidence;
        this.checks=List.copyOf(checks);this.ids=ids;this.audit=audit;
    }
    public ExitModels.Page list(long supplierId,int page,int size){
        var supplier=scope.get(supplierId);long tenant=TenantContext.require().tenantId();
        return new ExitModels.Page(mapper.count(tenant,supplierId),page,size,
            supplier.status()!=SupplierModels.Status.EXITED&&mapper.pendingCount(tenant,supplierId)==0,
            mapper.list(tenant,supplierId,Math.multiplyExact(page,size),size).stream().map(this::view).toList());
    }
    public ExitModels.View get(long supplierId,long id){
        scope.get(supplierId);return view(require(supplierId,id));
    }
    @Transactional public ExitModels.View create(long supplierId,ExitModels.Create command){
        var current=lock(supplierId);if("EXITED".equals(current.status()))throw invalid("供应商已退出，不能再次申请");
        long fileId=fileId(command.evidenceFileId());if(!evidence.available(fileId))throw invalid("退出依据文件不存在或不可用");
        var facts=ExitReadinessEvaluator.evaluate(checks,supplierId);var i=TenantContext.require();long id=ids.nextId();
        try{mapper.insert(id,i.tenantId(),supplierId,command.type().name(),command.reason().trim(),fileId,i.actorId());}
        catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"已有待处置退出申请");}
        snapshot(id,facts);event(id,"SUBMIT",command.reason().trim());
        audit("SUPPLIER_EXIT_SUBMIT",id,Map.of("supplierId",supplierId,"type",command.type(),"evidenceFileId",fileId,"localReady",facts.ready()));
        return view(require(supplierId,id));
    }
    @Transactional public ExitModels.View recheck(long supplierId,long id,ExitModels.Version command){
        lock(supplierId);var before=pending(supplierId,id,command.version());
        var facts=ExitReadinessEvaluator.evaluate(checks,supplierId);snapshot(id,facts);
        // Rechecking changes persisted facts and therefore also advances the application version.
        if(mapper.advanceVersion(TenantContext.require().tenantId(),supplierId,id,before.version())!=1)throw conflict();
        event(id,"RECHECK",facts.ready()?"本地业务处置核验通过":"仍有未完成事项");
        audit("SUPPLIER_EXIT_RECHECK",id,Map.of("supplierId",supplierId,"localReady",facts.ready()));
        return view(require(supplierId,id));
    }
    @Transactional public ExitModels.View cancel(long supplierId,long id,ExitModels.Version command){
        lock(supplierId);var before=pending(supplierId,id,command.version());var i=TenantContext.require();
        if(before.createdBy()!=i.actorId())throw invalid("只能撤回本人退出申请");
        finish(supplierId,id,"CANCELLED","申请人撤回",command.version());
        event(id,"CANCEL","申请人撤回");audit("SUPPLIER_EXIT_CANCEL",id,Map.of("supplierId",supplierId));
        return view(require(supplierId,id));
    }
    @Transactional public ExitModels.View review(long supplierId,long id,ExitModels.Review command){
        var current=lock(supplierId);var before=pending(supplierId,id,command.version());var i=TenantContext.require();
        if(before.createdBy()==i.actorId())throw invalid("申请人不能审批自己的退出申请");
        if(command.decision()==ExitModels.Decision.REJECT){
            finish(supplierId,id,"REJECTED",command.comment().trim(),command.version());event(id,"REJECT",command.comment().trim());
            audit("SUPPLIER_EXIT_REJECT",id,Map.of("supplierId",supplierId));return view(require(supplierId,id));
        }
        if("EXITED".equals(current.status()))throw invalid("供应商已经退出");
        if(!evidence.available(before.evidenceFileId()))throw invalid("退出依据文件已不可用");
        var facts=ExitReadinessEvaluator.evaluate(checks,supplierId);
        if(!facts.ready())throw invalid("退出处置未完成或核验能力缺失，请重新核验");
        snapshot(id,facts);
        if(suppliers.completeExit(i.tenantId(),i.actorId(),supplierId,current.version())!=1)throw conflict();
        finish(supplierId,id,"BUSINESS_CLOSED",command.comment().trim(),command.version());
        mapper.insertResult(ids.nextId(),i.tenantId(),id,supplierId,i.actorId(),command.comment().trim());
        event(id,"BUSINESS_CLOSE",command.comment().trim());
        audit("SUPPLIER_EXIT_BUSINESS_CLOSE",id,Map.of("supplierId",supplierId,"fromStatus",current.status(),"toStatus","EXITED","completionScope","LOCAL_BUSINESS","accessRecoveryStatus","NOT_VERIFIED"));
        return view(require(supplierId,id));
    }
    private SupplierModels.SupplierRow lock(long supplierId){
        scope.get(supplierId);var i=TenantContext.require();var current=suppliers.findForNewBusiness(i.tenantId(),supplierId);
        if(current==null||!AuthorizationContext.require().dataScope("supplier:master")
            .allows(new DataTarget(current.organizationId(),null,current.createdBy(),current.createdBy()),i.actorId()))throw notFound();
        return current;
    }
    private ExitModels.Row require(long supplierId,long id){
        var row=mapper.get(TenantContext.require().tenantId(),supplierId,id);if(row==null)throw notFound();return row;
    }
    private ExitModels.Row pending(long supplierId,long id,int version){
        var row=require(supplierId,id);if(!"SUBMITTED".equals(row.status()))throw invalid("退出申请已有处理结论");
        if(row.version()!=version)throw conflict();return row;
    }
    private void finish(long supplierId,long id,String status,String comment,int version){
        var i=TenantContext.require();if(mapper.finish(i.tenantId(),supplierId,id,status,comment,i.actorId(),version)!=1)throw conflict();
    }
    private void snapshot(long id,SupplierModels.ExitReadiness facts){
        long tenant=TenantContext.require().tenantId();mapper.resetItems(tenant,id);
        for(var fact:facts.blockers())mapper.upsertItem(ids.nextId(),tenant,id,fact.code(),fact.label(),fact.route(),fact.count());
    }
    private ExitModels.View view(ExitModels.Row row){
        long tenant=TenantContext.require().tenantId();
        var items=mapper.items(tenant,row.id()).stream().map(item->new ExitModels.Item(Long.toString(item.id()),
            item.checkCode(),item.checkLabel(),item.route(),item.initialCount(),item.currentCount(),item.checkedAt())).toList();
        var events=mapper.events(tenant,row.id()).stream().map(event->new ExitModels.Event(Long.toString(event.id()),
            event.action(),event.comment(),Long.toString(event.actorId()),event.createdAt())).toList();
        var r=mapper.findResult(tenant,row.id());
        var result=r==null?null:new ExitModels.Result(Long.toString(r.id()),Long.toString(r.approvedBy()),r.comment(),r.completionScope(),r.accessRecoveryStatus(),r.effectiveAt());
        return new ExitModels.View(Long.toString(row.id()),Long.toString(row.supplierId()),ExitModels.Type.valueOf(row.exitType()),
            row.reason(),Long.toString(row.evidenceFileId()),row.status(),Long.toString(row.createdBy()),row.createdAt(),
            row.reviewedBy()==null?null:row.reviewedBy().toString(),row.reviewedAt(),row.reviewComment(),row.version(),
            !items.isEmpty()&&items.stream().allMatch(item->item.currentCount()==0),items,events,result);
    }
    private void event(long id,String action,String comment){var i=TenantContext.require();mapper.event(ids.nextId(),i.tenantId(),id,action,comment,i.actorId());}
    private void audit(String action,long id,Map<String,Object> after){audit.append(new AuditService.AuditCommand(action,"SUPPLIER_EXIT",id,null,Map.of(),after,null,null));}
    private long fileId(String value){try{long parsed=Long.parseLong(value);if(parsed>0)return parsed;}catch(NumberFormatException ignored){}throw invalid("退出依据文件 ID 无效");}
    private ApiException invalid(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
    private ApiException conflict(){return new ApiException(CommonErrorCode.CONFLICT);}
    private ApiException notFound(){return new ApiException(CommonErrorCode.NOT_FOUND);}
}

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
    private static final Set<String> ENTITY_CODES=Set.of("OPEN_CONTRACT","OPEN_PROJECT","OPEN_PERSON","OPEN_ASSET","OPEN_SAFETY","OPEN_ATTENDANCE","OPEN_QUALITY","OPEN_IMPROVEMENT");
    private final ExitMapper mapper;private final SupplierMapper suppliers;private final SupplierService scope;
    private final AppealEvidenceVerifier evidence;private final List<SupplierExitCheck> checks;
    private final OperationIdGenerator ids;private final AuditService audit;private final ExitAssigneeVerifier assignees;private final ExitAssignmentNotifier notifications;
    public ExitApplicationService(ExitMapper mapper,SupplierMapper suppliers,SupplierService scope,
        AppealEvidenceVerifier evidence,List<SupplierExitCheck> checks,OperationIdGenerator ids,AuditService audit,ExitAssigneeVerifier assignees,ExitAssignmentNotifier notifications){
        this.mapper=mapper;this.suppliers=suppliers;this.scope=scope;this.evidence=evidence;
        this.checks=List.copyOf(checks);this.ids=ids;this.audit=audit;this.assignees=assignees;this.notifications=notifications;
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
    @Transactional public ExitModels.EntityPage entities(long supplierId,long applicationId,int page,int size){
        if(page<0||page>10000||size<1||size>100)throw invalid("事项分页参数无效");
        scope.get(supplierId);var application=require(supplierId,applicationId);long tenant=TenantContext.require().tenantId();
        return new ExitModels.EntityPage(mapper.entityCount(tenant,applicationId),page,size,application.version(),
            mapper.entities(tenant,applicationId,Math.multiplyExact(page,size),size).stream().map(this::entityView).toList());
    }
    @Transactional public ExitModels.View create(long supplierId,ExitModels.Create command){
        var current=lock(supplierId);if("EXITED".equals(current.status()))throw invalid("供应商已退出，不能再次申请");
        long fileId=fileId(command.evidenceFileId());if(!evidence.available(fileId))throw invalid("退出依据文件不存在或不可用");
        var facts=ExitReadinessEvaluator.evaluate(checks,supplierId);var i=TenantContext.require();long id=ids.nextId();
        try{mapper.insert(id,i.tenantId(),supplierId,command.type().name(),command.reason().trim(),fileId,i.actorId());}
        catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"已有待处置退出申请");}
        snapshot(supplierId,id,facts);event(id,"SUBMIT",command.reason().trim());
        audit("SUPPLIER_EXIT_SUBMIT",id,Map.of("supplierId",supplierId,"type",command.type(),"evidenceFileId",fileId,"localReady",facts.ready()));
        return view(require(supplierId,id));
    }
    @Transactional public ExitModels.View recheck(long supplierId,long id,ExitModels.Version command){
        lock(supplierId);var before=pending(supplierId,id,command.version());
        var facts=ExitReadinessEvaluator.evaluate(checks,supplierId);snapshot(supplierId,id,facts);
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
        snapshot(supplierId,id,facts);
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
    @Transactional public ExitModels.View assign(long supplierId,long applicationId,long entityId,ExitModels.Assign command){
        lock(supplierId);var application=pending(supplierId,applicationId,command.applicationVersion());
        var i=TenantContext.require();var entity=mapper.entity(i.tenantId(),applicationId,entityId);
        if(entity==null)throw notFound();
        if(entity.version()!=command.version())throw conflict();
        if(!"OPEN".equals(entity.state()))throw invalid("已核验完成的事项不能修改责任或说明");
        var facts=ExitReadinessEvaluator.evaluate(checks,supplierId);
        if(entityFacts(supplierId,facts).stream().noneMatch(e->e.code().equals(entity.checkCode())&&e.sourceId()==entity.sourceId()))
            throw invalid("此事项已处置，请重新核验刷新台账");
        long assignee=positiveId(command.assigneeId(),"责任人账号 ID 无效");
        if(!assignees.active(assignee))throw invalid("责任人必须是当前租户有效账号");
        if(command.note()==null||command.note().isBlank()||command.note().trim().length()>1800)throw invalid("处理说明不能为空且不超过 1800 字");
        if(mapper.assignEntity(i.tenantId(),applicationId,entityId,assignee,command.note().trim(),i.actorId(),command.version())!=1)throw conflict();
        if(mapper.advanceVersion(i.tenantId(),supplierId,applicationId,application.version())!=1)throw conflict();
        notifications.assigned(supplierId,applicationId,entityId,entity.checkCode(),entity.sourceId(),assignee);
        event(applicationId,"ENTITY_ASSIGN",entity.checkCode()+" #"+entity.sourceId()+" → 责任人 "+assignee+"："+command.note().trim());
        audit("SUPPLIER_EXIT_ENTITY_ASSIGN",applicationId,Map.of("supplierId",supplierId,"entityId",entityId,"code",entity.checkCode(),"sourceId",entity.sourceId(),"assigneeId",assignee));
        return view(require(supplierId,applicationId));
    }
    private List<SupplierExitCheck.Entity> entityFacts(long supplierId,SupplierModels.ExitReadiness facts){
        var codes=ENTITY_CODES;
        var result=new LinkedHashMap<String,SupplierExitCheck.Entity>();
        for(var check:checks){
            var entities=check.entities(supplierId);if(entities==null)throw invalid("退出实体核验能力返回无效结果");
            for(var e:entities){
                if(e==null||e.code()==null||!codes.contains(e.code())||e.sourceId()<=0||e.route()==null||!e.route().startsWith("/")||e.route().startsWith("//")||e.route().length()>200||result.putIfAbsent(e.code()+":"+e.sourceId(),e)!=null)
                    throw invalid("退出实体核验结果冲突或无效");
            }
        }
        for(var code:codes){
            long expected=facts.blockers().stream().filter(f->f.code().equals(code)).mapToLong(SupplierExitCheck.Blocker::count).sum();
            if(result.values().stream().filter(e->e.code().equals(code)).count()!=expected)throw invalid("退出实体明细与汇总不一致，请核验模块能力");
        }
        return List.copyOf(result.values());
    }
    private void snapshot(long supplierId,long id,SupplierModels.ExitReadiness facts){
        var entities=entityFacts(supplierId,facts);
        long tenant=TenantContext.require().tenantId();mapper.resetItems(tenant,id);
        for(var fact:facts.blockers())mapper.upsertItem(ids.nextId(),tenant,id,fact.code(),fact.label(),fact.route(),fact.count());
        var verified=facts.blockers().stream().map(SupplierExitCheck.Blocker::code).filter(ENTITY_CODES::contains).collect(java.util.stream.Collectors.toSet());
        if(!verified.isEmpty())mapper.clearEntities(tenant,id,verified);
        for(var entity:entities)mapper.upsertEntity(ids.nextId(),tenant,id,entity.code(),entity.sourceId(),entity.route());
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
            !items.isEmpty()&&items.stream().allMatch(item->item.currentCount()==0),items,
            mapper.entityCount(tenant,row.id()),mapper.entities(tenant,row.id(),0,20).stream().map(this::entityView).toList(),events,result);
    }
    private ExitModels.Entity entityView(ExitModels.EntityRow e){return new ExitModels.Entity(Long.toString(e.id()),e.checkCode(),Long.toString(e.sourceId()),e.route(),e.state(),
        e.assigneeId()==null?null:e.assigneeId().toString(),e.note(),e.assignedBy()==null?null:e.assignedBy().toString(),e.assignedAt(),e.checkedAt(),e.clearedAt(),e.version());}
    private void event(long id,String action,String comment){var i=TenantContext.require();mapper.event(ids.nextId(),i.tenantId(),id,action,comment,i.actorId());}
    private void audit(String action,long id,Map<String,Object> after){audit.append(new AuditService.AuditCommand(action,"SUPPLIER_EXIT",id,null,Map.of(),after,null,null));}
    private long fileId(String value){return positiveId(value,"退出依据文件 ID 无效");}
    private long positiveId(String value,String message){try{long parsed=Long.parseLong(value);if(parsed>0)return parsed;}catch(NumberFormatException ignored){}throw invalid(message);}
    private ApiException invalid(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
    private ApiException conflict(){return new ApiException(CommonErrorCode.CONFLICT);}
    private ApiException notFound(){return new ApiException(CommonErrorCode.NOT_FOUND);}
}

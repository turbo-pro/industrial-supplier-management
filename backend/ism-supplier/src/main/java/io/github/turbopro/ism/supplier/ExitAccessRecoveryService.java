package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class ExitAccessRecoveryService {
    private final ExitMapper mapper;
    private final SupplierService suppliers;
    private final AppealEvidenceVerifier evidence;
    private final ExitAssigneeVerifier assignees;
    private final ExitAssignmentNotifier notifications;
    private final OperationIdGenerator ids;
    private final AuditService audit;

    public ExitAccessRecoveryService(ExitMapper mapper,SupplierService suppliers,AppealEvidenceVerifier evidence,ExitAssigneeVerifier assignees,
                                     ExitAssignmentNotifier notifications,
                                     OperationIdGenerator ids,AuditService audit){
        this.mapper=mapper;this.suppliers=suppliers;this.evidence=evidence;this.assignees=assignees;this.notifications=notifications;this.ids=ids;this.audit=audit;
    }

    @Transactional(readOnly=true)
    public ExitAccessRecoveryModels.Inventory inventory(long supplierId,long applicationId){
        suppliers.get(supplierId);
        long tenantId=TenantContext.require().tenantId();
        var application=mapper.archiveApplication(tenantId,supplierId,applicationId);
        var result=mapper.findResult(tenantId,applicationId);
        if(application==null||!"BUSINESS_CLOSED".equals(application.status())||result==null)
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        var tasks=mapper.accessRecoveryTasks(tenantId,supplierId,applicationId).stream()
            .map(row->task(tenantId,row))
            .toList();
        return new ExitAccessRecoveryModels.Inventory(Long.toString(supplierId),Long.toString(applicationId),
            result.accessRecoveryStatus(),tasks);
    }

    @Transactional
    public ExitAccessRecoveryModels.Inventory record(long supplierId,long applicationId,long taskId,
                                                      ExitAccessRecoveryModels.RecordFinding command){
        if(!AuthorizationContext.require().hasAction("supplier:exit:review"))throw new ApiException(CommonErrorCode.FORBIDDEN);
        suppliers.get(supplierId);
        var identity=TenantContext.require();long tenantId=identity.tenantId();
        var application=mapper.get(tenantId,supplierId,applicationId);
        if(application==null||!"BUSINESS_CLOSED".equals(application.status())||mapper.findResult(tenantId,applicationId)==null)
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        var row=mapper.lockAccessRecoveryTask(tenantId,supplierId,applicationId,taskId);
        if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(row.version()!=command.version())throw new ApiException(CommonErrorCode.CONFLICT);
        long fileId;
        try{fileId=Long.parseLong(command.evidenceFileId());if(fileId<=0)throw new NumberFormatException();}
        catch(NumberFormatException ex){throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"证据文件 ID 无效");}
        if(!evidence.available(fileId))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"证据文件不存在或不可用");
        String note=command.note().trim();
        if(note.isEmpty())throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"请填写核查说明");
        if(mapper.recordAccessRecoveryFinding(tenantId,supplierId,applicationId,taskId,command.finding().name(),
                fileId,note,identity.actorId(),command.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        if(mapper.insertAccessRecoveryEvent(ids.nextId(),tenantId,taskId,command.finding().name(),fileId,note,identity.actorId())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        audit.append(new AuditService.AuditCommand("SUPPLIER_EXIT_ACCESS_DISCOVERY","SUPPLIER_EXIT_ACCESS",taskId,null,
            Map.of("status",row.status()),Map.of("status","DISCOVERY_RECORDED","finding",command.finding().name(),
                "supplierId",supplierId,"applicationId",applicationId),null,null));
        return inventory(supplierId,applicationId);
    }

    @Transactional
    public ExitAccessRecoveryModels.Inventory assign(long supplierId,long applicationId,long taskId,
                                                      ExitAccessRecoveryModels.Assign command){
        if(!AuthorizationContext.require().hasAction("supplier:exit:assign"))throw new ApiException(CommonErrorCode.FORBIDDEN);
        suppliers.get(supplierId);
        var identity=TenantContext.require();long tenantId=identity.tenantId();
        var application=mapper.get(tenantId,supplierId,applicationId);
        if(application==null||!"BUSINESS_CLOSED".equals(application.status())||mapper.findResult(tenantId,applicationId)==null)
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        var row=mapper.lockAccessRecoveryTask(tenantId,supplierId,applicationId,taskId);
        if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(row.version()!=command.version())throw new ApiException(CommonErrorCode.CONFLICT);
        long assigneeId;
        try{assigneeId=Long.parseLong(command.assigneeId());if(assigneeId<=0)throw new NumberFormatException();}
        catch(NumberFormatException ex){throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"责任人账号 ID 无效");}
        if(!assignees.active(assigneeId))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"责任人必须是本租户有效账号");
        var today=RestrictionBusinessDate.today();
        if(command.dueDate()!=null&&(command.dueDate().isBefore(today)||command.dueDate().isAfter(today.plusYears(1))))
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"核查期限须在今天至一年内");
        String note=command.note().trim();
        if(note.isEmpty())throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"请填写分派说明");
        if(mapper.assignAccessRecoveryTask(tenantId,supplierId,applicationId,taskId,assigneeId,command.dueDate(),note,identity.actorId(),command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        if(mapper.insertAccessAssignmentEvent(ids.nextId(),tenantId,taskId,row.assigneeId(),assigneeId,command.dueDate(),note,identity.actorId())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        notifications.accessAssigned(supplierId,applicationId,taskId,row.channel(),assigneeId,command.dueDate());
        audit.append(new AuditService.AuditCommand("SUPPLIER_EXIT_ACCESS_ASSIGN","SUPPLIER_EXIT_ACCESS",taskId,null,
            Map.of("previousAssigneeId",row.assigneeId()==null?"":row.assigneeId().toString()),
            Map.of("assigneeId",assigneeId,"dueDate",command.dueDate()==null?"":command.dueDate().toString(),"supplierId",supplierId,"applicationId",applicationId),null,null));
        return inventory(supplierId,applicationId);
    }

    @Transactional
    public ExitAccessRecoveryModels.Inventory remind(long supplierId,long applicationId,long taskId,
                                                      ExitAccessRecoveryModels.Remind command){
        if(!AuthorizationContext.require().hasAction("supplier:exit:remind"))throw new ApiException(CommonErrorCode.FORBIDDEN);
        suppliers.get(supplierId);
        var identity=TenantContext.require();long tenantId=identity.tenantId();
        var application=mapper.get(tenantId,supplierId,applicationId);
        if(application==null||!"BUSINESS_CLOSED".equals(application.status())||mapper.findResult(tenantId,applicationId)==null)
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        var row=mapper.lockAccessRecoveryTask(tenantId,supplierId,applicationId,taskId);
        if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(row.version()!=command.version())throw new ApiException(CommonErrorCode.CONFLICT);
        if(row.assigneeId()==null||!assignees.active(row.assigneeId()))
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"请先分派给本租户有效责任人");
        var now=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        if(row.lastRemindedAt()!=null&&row.lastRemindedAt().isAfter(now.minusHours(24)))
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"同一核查任务 24 小时内只能催办一次");
        if(mapper.remindAccessRecoveryTask(tenantId,supplierId,applicationId,taskId,row.assigneeId(),now,command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        if(mapper.insertAccessReminderEvent(ids.nextId(),tenantId,taskId,row.assigneeId(),identity.actorId(),row.dueDate())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        notifications.accessReminded(supplierId,applicationId,taskId,row.channel(),row.assigneeId(),row.dueDate());
        mapper.resolveAccessReminderFailure(tenantId,supplierId,applicationId,taskId,now);
        audit.append(new AuditService.AuditCommand("SUPPLIER_EXIT_ACCESS_REMIND","SUPPLIER_EXIT_ACCESS",taskId,null,
            Map.of("status",row.status()),Map.of("status",row.status(),"assigneeId",row.assigneeId(),
                "supplierId",supplierId,"applicationId",applicationId),null,null));
        return inventory(supplierId,applicationId);
    }

    /** Background-only reminder. The task row lock serializes all application instances and manual reminders. */
    @Transactional
    public boolean autoRemind(long supplierId,long applicationId,long taskId,int intervalHours){
        var identity=TenantContext.require();
        if(identity.actorId()!=0||intervalHours<24||intervalHours>720)throw new IllegalArgumentException("system context and valid interval required");
        var application=mapper.get(identity.tenantId(),supplierId,applicationId);
        if(application==null||!"BUSINESS_CLOSED".equals(application.status())||mapper.findResult(identity.tenantId(),applicationId)==null)return false;
        var row=mapper.lockAccessRecoveryTask(identity.tenantId(),supplierId,applicationId,taskId);
        if(row==null||row.assigneeId()==null||row.dueDate()==null||!row.dueDate().isBefore(RestrictionBusinessDate.today())||!assignees.active(row.assigneeId()))return false;
        var now=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        if(row.lastRemindedAt()!=null&&row.lastRemindedAt().isAfter(now.minusHours(intervalHours)))return false;
        if(mapper.remindAccessRecoveryTask(identity.tenantId(),supplierId,applicationId,taskId,row.assigneeId(),now,row.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        if(mapper.insertAccessReminderEvent(ids.nextId(),identity.tenantId(),taskId,row.assigneeId(),0,row.dueDate())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        notifications.accessReminded(supplierId,applicationId,taskId,row.channel(),row.assigneeId(),row.dueDate());
        audit.append(new AuditService.AuditCommand("SUPPLIER_EXIT_ACCESS_AUTO_REMIND","SUPPLIER_EXIT_ACCESS",taskId,null,
            Map.of("status",row.status()),Map.of("status",row.status(),"assigneeId",row.assigneeId(),"supplierId",supplierId,"applicationId",applicationId),null,null));
        return true;
    }

    @Transactional
    public void recordAutoReminderFailure(long supplierId,long applicationId,long taskId,String reasonCode){
        var identity=TenantContext.require();
        if(identity.actorId()!=0||reasonCode==null||!reasonCode.matches("[A-Z0-9_]{1,64}"))throw new IllegalArgumentException("system actor and safe reason code required");
        if(mapper.recordAccessReminderFailure(identity.tenantId(),supplierId,applicationId,taskId,reasonCode,
            java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))!=1)throw new ApiException(CommonErrorCode.NOT_FOUND);
    }

    @Transactional
    public void resolveAutoReminderFailure(long supplierId,long applicationId,long taskId){
        var identity=TenantContext.require();
        if(identity.actorId()!=0)throw new IllegalArgumentException("system actor required");
        mapper.resolveAccessReminderFailure(identity.tenantId(),supplierId,applicationId,taskId,
            java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
    }

    private ExitAccessRecoveryModels.Task task(long tenantId,ExitAccessRecoveryModels.TaskRow row){
        var events=mapper.accessRecoveryEvents(tenantId,row.id()).stream()
            .map(e->new ExitAccessRecoveryModels.Event(Long.toString(e.id()),e.finding(),Long.toString(e.evidenceFileId()),
                e.note(),Long.toString(e.actorId()),e.createdAt())).toList();
        var assignments=mapper.accessAssignmentEvents(tenantId,row.id()).stream()
            .map(e->new ExitAccessRecoveryModels.AssignmentEvent(Long.toString(e.id()),
                e.previousAssigneeId()==null?null:Long.toString(e.previousAssigneeId()),Long.toString(e.assigneeId()),
                e.dueDate(),e.note(),Long.toString(e.actorId()),e.createdAt())).toList();
        var reminders=mapper.accessReminderEvents(tenantId,row.id()).stream()
            .map(e->new ExitAccessRecoveryModels.ReminderEvent(Long.toString(e.id()),Long.toString(e.recipientId()),
                Long.toString(e.actorId()),e.dueDate(),e.createdAt())).toList();
        return new ExitAccessRecoveryModels.Task(Long.toString(row.id()),row.channel(),row.status(),row.finding(),
            row.evidenceFileId()==null?null:Long.toString(row.evidenceFileId()),row.discoveryNote(),
            row.discoveredBy()==null?null:Long.toString(row.discoveredBy()),row.discoveredAt(),
            row.assigneeId()==null?null:Long.toString(row.assigneeId()),row.dueDate(),row.assignmentNote(),
            row.assignedBy()==null?null:Long.toString(row.assignedBy()),row.assignedAt(),row.lastRemindedAt(),
            row.reminderFailureCount(),row.reminderFailureCode(),row.reminderFailureStatus(),row.reminderFirstFailedAt(),row.reminderLastFailedAt(),row.reminderResolvedAt(),
            row.version(),row.createdAt(),events,assignments,reminders);
    }
}

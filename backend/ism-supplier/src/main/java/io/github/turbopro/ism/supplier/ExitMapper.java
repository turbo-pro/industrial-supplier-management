package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ExitMapper extends TenantScopedMapper {
    String ENTITY_FIELDS="id,check_code,source_id,route,state,assignee_id,note,assigned_by,assigned_at,checked_at,cleared_at,version,due_date,last_reminded_at";
    @Update("UPDATE sup_exit_entity SET due_date=#{dueDate},version=version+1 WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} AND id=#{id} AND state='OPEN' AND version=#{version}")
    int deadline(long tenantId,long applicationId,long id,java.time.LocalDate dueDate,int version);
    @Update("UPDATE sup_exit_entity SET last_reminded_at=#{now},version=version+1 WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} AND id=#{id} AND state='OPEN' AND version=#{version}")
    int reminded(long tenantId,long applicationId,long id,java.time.LocalDateTime now,int version);
    @Select("SELECT COUNT(*) FROM sup_exit_entity WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}")
    long entityCount(long tenantId,long applicationId);
    @Select("SELECT "+ENTITY_FIELDS+" FROM sup_exit_entity WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} ORDER BY check_code,source_id LIMIT #{size} OFFSET #{offset}")
    List<ExitModels.EntityRow> entities(long tenantId,long applicationId,int offset,int size);
    @Select("SELECT "+ENTITY_FIELDS+" FROM sup_exit_entity WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} AND id=#{id} FOR UPDATE")
    ExitModels.EntityRow entity(long tenantId,long applicationId,long id);
    @Update("<script>UPDATE sup_exit_entity SET state='CLEARED',cleared_at=CURRENT_TIMESTAMP(3),checked_at=CURRENT_TIMESTAMP(3),version=version+1 WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} AND state='OPEN' AND check_code IN <foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach></script>")
    int clearEntities(long tenantId,long applicationId,java.util.Set<String> codes);
    @Insert("INSERT INTO sup_exit_entity(id,tenant_id,application_id,check_code,source_id,route) VALUES(#{id},#{tenantId},#{applicationId},#{code},#{sourceId},#{route}) ON DUPLICATE KEY UPDATE state='OPEN',cleared_at=NULL,checked_at=CURRENT_TIMESTAMP(3),version=version+1")
    int upsertEntity(long id,long tenantId,long applicationId,String code,long sourceId,String route);
    @Update("UPDATE sup_exit_entity SET assignee_id=#{assigneeId},note=#{note},assigned_by=#{actorId},assigned_at=CURRENT_TIMESTAMP(3),version=version+1 WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} AND id=#{id} AND state='OPEN' AND version=#{version}")
    int assignEntity(long tenantId,long applicationId,long id,long assigneeId,String note,long actorId,int version);
    @Select("SELECT COUNT(*) FROM sup_exit_application WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND status='SUBMITTED'")
    int pendingCount(long tenantId,long supplierId);
    @Update("UPDATE sup_exit_application SET version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} AND status='SUBMITTED' AND version=#{version}")
    int advanceVersion(long tenantId,long supplierId,long id,int version);
    String FIELDS="id,supplier_id,exit_type,reason,evidence_file_id,status,created_by,created_at,reviewed_by,reviewed_at,review_comment,version";
    @Select("SELECT "+FIELDS+" FROM sup_exit_application WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} FOR UPDATE")
    ExitModels.Row get(long tenantId,long supplierId,long id);
    @Select("SELECT "+FIELDS+" FROM sup_exit_application WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id}")
    ExitModels.Row archiveApplication(long tenantId,long supplierId,long id);
    @Select("SELECT "+FIELDS+" FROM sup_exit_application WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<ExitModels.Row> list(long tenantId,long supplierId,int offset,int size);
    @Select("SELECT COUNT(*) FROM sup_exit_application WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}")
    long count(long tenantId,long supplierId);
    @Insert("INSERT INTO sup_exit_application(id,tenant_id,supplier_id,exit_type,reason,evidence_file_id,created_by) VALUES(#{id},#{tenantId},#{supplierId},#{type},#{reason},#{fileId},#{actorId})")
    int insert(long id,long tenantId,long supplierId,String type,String reason,long fileId,long actorId);
    @Update("UPDATE sup_exit_application SET status=#{status},reviewed_by=#{actorId},reviewed_at=CURRENT_TIMESTAMP(3),review_comment=#{comment},version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} AND status='SUBMITTED' AND version=#{version}")
    int finish(long tenantId,long supplierId,long id,String status,String comment,long actorId,int version);
    @Select("SELECT id,check_code,check_label,route,initial_count,current_count,checked_at FROM sup_exit_item WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} ORDER BY check_code")
    List<ExitModels.ItemRow> items(long tenantId,long applicationId);
    @Update("UPDATE sup_exit_item SET current_count=0,checked_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}")
    int resetItems(long tenantId,long applicationId);
    @Insert("INSERT INTO sup_exit_item(id,tenant_id,application_id,check_code,check_label,route,initial_count,current_count) VALUES(#{id},#{tenantId},#{applicationId},#{code},#{label},#{route},#{count},#{count}) ON DUPLICATE KEY UPDATE current_count=#{count},check_label=#{label},route=#{route},checked_at=CURRENT_TIMESTAMP(3)")
    int upsertItem(long id,long tenantId,long applicationId,String code,String label,String route,long count);
    @Insert("INSERT INTO sup_exit_result(id,tenant_id,application_id,supplier_id,approved_by,comment) VALUES(#{id},#{tenantId},#{applicationId},#{supplierId},#{actorId},#{comment})")
    int insertResult(long id,long tenantId,long applicationId,long supplierId,long actorId,String comment);
    @Insert("INSERT INTO sup_exit_access_recovery_task(id,tenant_id,application_id,supplier_id,channel,created_by) VALUES(#{id},#{tenantId},#{applicationId},#{supplierId},#{channel},#{actorId})")
    int insertAccessRecoveryTask(long id,long tenantId,long applicationId,long supplierId,String channel,long actorId);
    String ACCESS_FIELDS="id,channel,status,finding,evidence_file_id,discovery_note,discovered_by,discovered_at,assignee_id,due_date,assignment_note,assigned_by,assigned_at,last_reminded_at,reminder_failure_count,reminder_failure_code,reminder_failure_status,reminder_first_failed_at,reminder_last_failed_at,reminder_resolved_at,version,created_at";
    @Select("SELECT "+ACCESS_FIELDS+" FROM sup_exit_access_recovery_task WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND application_id=#{applicationId} ORDER BY channel")
    List<ExitAccessRecoveryModels.TaskRow> accessRecoveryTasks(long tenantId,long supplierId,long applicationId);
    @Select("SELECT "+ACCESS_FIELDS+" FROM sup_exit_access_recovery_task WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND application_id=#{applicationId} AND id=#{taskId} FOR UPDATE")
    ExitAccessRecoveryModels.TaskRow lockAccessRecoveryTask(long tenantId,long supplierId,long applicationId,long taskId);
    @Update("UPDATE sup_exit_access_recovery_task SET last_reminded_at=CASE WHEN assignee_id IS NULL OR assignee_id!=#{assigneeId} THEN NULL ELSE last_reminded_at END,assignee_id=#{assigneeId},due_date=#{dueDate},assignment_note=#{note},assigned_by=#{actorId},assigned_at=CURRENT_TIMESTAMP(3),version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND application_id=#{applicationId} AND id=#{taskId} AND version=#{version}")
    int assignAccessRecoveryTask(long tenantId,long supplierId,long applicationId,long taskId,long assigneeId,java.time.LocalDate dueDate,String note,long actorId,int version);
    @Insert("INSERT INTO sup_exit_access_assignment_event(id,tenant_id,task_id,previous_assignee_id,assignee_id,due_date,note,actor_id) VALUES(#{id},#{tenantId},#{taskId},#{previousAssigneeId},#{assigneeId},#{dueDate},#{note},#{actorId})")
    int insertAccessAssignmentEvent(long id,long tenantId,long taskId,Long previousAssigneeId,long assigneeId,java.time.LocalDate dueDate,String note,long actorId);
    @Select("SELECT id,task_id,previous_assignee_id,assignee_id,due_date,note,actor_id,created_at FROM sup_exit_access_assignment_event WHERE tenant_id=#{tenantId} AND task_id=#{taskId} ORDER BY created_at,id")
    List<ExitAccessRecoveryModels.AssignmentEventRow> accessAssignmentEvents(long tenantId,long taskId);
    @Update("UPDATE sup_exit_access_recovery_task SET last_reminded_at=#{now},version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND application_id=#{applicationId} AND id=#{taskId} AND assignee_id=#{assigneeId} AND version=#{version}")
    int remindAccessRecoveryTask(long tenantId,long supplierId,long applicationId,long taskId,long assigneeId,java.time.LocalDateTime now,int version);
    @Insert("INSERT INTO sup_exit_access_reminder_event(id,tenant_id,task_id,recipient_id,actor_id,due_date) VALUES(#{id},#{tenantId},#{taskId},#{recipientId},#{actorId},#{dueDate})")
    int insertAccessReminderEvent(long id,long tenantId,long taskId,long recipientId,long actorId,java.time.LocalDate dueDate);
    @Select("SELECT id,task_id,recipient_id,actor_id,due_date,created_at FROM sup_exit_access_reminder_event WHERE tenant_id=#{tenantId} AND task_id=#{taskId} ORDER BY created_at,id")
    List<ExitAccessRecoveryModels.ReminderEventRow> accessReminderEvents(long tenantId,long taskId);
    @Update("UPDATE sup_exit_access_recovery_task SET reminder_failure_count=IF(reminder_failure_count>=2147483647,2147483647,reminder_failure_count+1),reminder_failure_code=#{reasonCode},reminder_failure_status='FAILED',reminder_first_failed_at=COALESCE(reminder_first_failed_at,#{now}),reminder_last_failed_at=#{now},reminder_resolved_at=NULL WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND application_id=#{applicationId} AND id=#{taskId}")
    int recordAccessReminderFailure(long tenantId,long supplierId,long applicationId,long taskId,String reasonCode,java.time.LocalDateTime now);
    @Update("UPDATE sup_exit_access_recovery_task SET reminder_failure_status='DELIVERED',reminder_resolved_at=#{now} WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND application_id=#{applicationId} AND id=#{taskId} AND reminder_failure_status='FAILED'")
    int resolveAccessReminderFailure(long tenantId,long supplierId,long applicationId,long taskId,java.time.LocalDateTime now);
    @Update("UPDATE sup_exit_access_recovery_task SET status='DISCOVERY_RECORDED',finding=#{finding},evidence_file_id=#{fileId},discovery_note=#{note},discovered_by=#{actorId},discovered_at=CURRENT_TIMESTAMP(3),version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND application_id=#{applicationId} AND id=#{taskId} AND version=#{version}")
    int recordAccessRecoveryFinding(long tenantId,long supplierId,long applicationId,long taskId,String finding,long fileId,String note,long actorId,int version);
    @Insert("INSERT INTO sup_exit_access_recovery_event(id,tenant_id,task_id,finding,evidence_file_id,note,actor_id) VALUES(#{id},#{tenantId},#{taskId},#{finding},#{fileId},#{note},#{actorId})")
    int insertAccessRecoveryEvent(long id,long tenantId,long taskId,String finding,long fileId,String note,long actorId);
    @Select("SELECT id,task_id,finding,evidence_file_id,note,actor_id,created_at FROM sup_exit_access_recovery_event WHERE tenant_id=#{tenantId} AND task_id=#{taskId} ORDER BY created_at,id")
    List<ExitAccessRecoveryModels.EventRow> accessRecoveryEvents(long tenantId,long taskId);
    @Select("SELECT id,approved_by,comment,completion_scope,access_recovery_status,effective_at FROM sup_exit_result WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}")
    ExitModels.ResultRow findResult(long tenantId,long applicationId);
    @Insert("INSERT INTO sup_exit_event(id,tenant_id,application_id,action,comment,actor_id) VALUES(#{id},#{tenantId},#{applicationId},#{action},#{comment},#{actorId})")
    int event(long id,long tenantId,long applicationId,String action,String comment,long actorId);
    @Select("SELECT id,action,comment,actor_id,created_at FROM sup_exit_event WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} ORDER BY created_at,id")
    List<ExitModels.EventRow> events(long tenantId,long applicationId);
}

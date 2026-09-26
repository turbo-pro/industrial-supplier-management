package io.github.turbopro.ism.performance;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.time.*;
import java.util.List;

@Mapper
public interface ImprovementMapper extends TenantScopedMapper {
    @Select("SELECT id FROM sup_supplier WHERE tenant_id=#{tenantId} AND id=#{supplierId} AND deleted=0 AND status!='EXITED' FOR UPDATE")
    Long lockSupplier(long tenantId, long supplierId);
    @Select("SELECT id,evaluation_id,root_cause,action_plan,due_date,status,completion_note,evidence_file_id,review_comment,reviewed_by,reviewed_at,created_by,version,created_at,updated_at FROM per_improvement_plan WHERE tenant_id=#{tenantId} AND evaluation_id=#{evaluationId}")
    ImprovementModels.Row get(long tenantId,long evaluationId);
    @Insert("INSERT INTO per_improvement_plan(id,tenant_id,evaluation_id,root_cause,action_plan,due_date,created_by,updated_by) VALUES(#{id},#{tenantId},#{evaluationId},#{rootCause},#{actionPlan},#{dueDate},#{actorId},#{actorId})")
    int insert(long id,long tenantId,long evaluationId,String rootCause,String actionPlan,LocalDate dueDate,long actorId);
    @Update("UPDATE per_improvement_plan SET status='SUBMITTED',completion_note=#{completionNote},evidence_file_id=#{fileId},review_comment=NULL,reviewed_by=NULL,reviewed_at=NULL,updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND evaluation_id=#{evaluationId} AND status IN ('OPEN','REWORK') AND version=#{version}")
    int submit(long tenantId,long evaluationId,String completionNote,long fileId,long actorId,int version);
    @Update("UPDATE per_improvement_plan SET status=CASE WHEN #{decision}='ACCEPT' THEN 'ACCEPTED' ELSE 'REWORK' END,review_comment=#{comment},reviewed_by=#{actorId},reviewed_at=CURRENT_TIMESTAMP(3),updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND evaluation_id=#{evaluationId} AND status='SUBMITTED' AND version=#{version}")
    int review(long tenantId,long evaluationId,String decision,String comment,long actorId,int version);
    @Insert("INSERT INTO per_improvement_event(id,tenant_id,plan_id,action,from_status,to_status,comment,actor_id) VALUES(#{id},#{tenantId},#{planId},#{action},#{fromStatus},#{toStatus},#{comment},#{actorId})")
    int event(long id,long tenantId,long planId,String action,String fromStatus,String toStatus,String comment,long actorId);
    @Select("SELECT id,action,from_status,to_status,comment,actor_id,created_at FROM per_improvement_event WHERE tenant_id=#{tenantId} AND plan_id=#{planId} ORDER BY created_at,id")
    List<EventRow> events(long tenantId,long planId);
    record EventRow(long id,String action,String fromStatus,String toStatus,String comment,long actorId,LocalDateTime createdAt) {}
}

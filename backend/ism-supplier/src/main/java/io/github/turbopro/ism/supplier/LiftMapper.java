package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface LiftMapper extends TenantScopedMapper {
    String FIELDS="id,case_id,reason,evidence_file_id,status,created_by,created_at,reviewed_by,reviewed_at,review_comment,version,observation_seconds";
    @Select("SELECT observation_seconds FROM sup_lift_application WHERE tenant_id=#{tenantId} AND case_id=#{caseId} AND status='SUBMITTED' FOR UPDATE")
    Long pendingObservationSeconds(long tenantId,long caseId);
    @Select("SELECT "+FIELDS+" FROM sup_lift_application WHERE tenant_id=#{tenantId} AND case_id=#{caseId} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<LiftModels.Row> list(long tenantId,long caseId,int offset,int size);
    @Select("SELECT COUNT(*) FROM sup_lift_application WHERE tenant_id=#{tenantId} AND case_id=#{caseId}")
    long count(long tenantId,long caseId);
    @Select("SELECT "+FIELDS+" FROM sup_lift_application WHERE tenant_id=#{tenantId} AND case_id=#{caseId} AND id=#{id} FOR UPDATE")
    LiftModels.Row get(long tenantId,long caseId,long id);
    @Insert("INSERT INTO sup_lift_application(id,tenant_id,case_id,reason,evidence_file_id,created_by,observation_seconds) VALUES(#{id},#{tenantId},#{caseId},#{reason},#{fileId},#{actorId},#{observationSeconds})")
    int insert(long id,long tenantId,long caseId,String reason,long fileId,long actorId,long observationSeconds);
    @Update("UPDATE sup_lift_application SET status=#{status},reviewed_by=#{actorId},reviewed_at=CURRENT_TIMESTAMP(3),review_comment=#{comment},version=version+1 WHERE tenant_id=#{tenantId} AND case_id=#{caseId} AND id=#{id} AND status='SUBMITTED' AND version=#{version}")
    int review(long tenantId,long caseId,long id,String status,String comment,long actorId,int version);
    @Insert("INSERT INTO sup_lift_result(id,tenant_id,case_id,application_id,approved_by,reason) VALUES(#{id},#{tenantId},#{caseId},#{applicationId},#{actorId},#{reason})")
    int result(long id,long tenantId,long caseId,long applicationId,long actorId,String reason);
}

package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ExitMapper extends TenantScopedMapper {
    @Select("SELECT COUNT(*) FROM sup_exit_application WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND status='SUBMITTED'")
    int pendingCount(long tenantId,long supplierId);
    @Update("UPDATE sup_exit_application SET version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} AND status='SUBMITTED' AND version=#{version}")
    int advanceVersion(long tenantId,long supplierId,long id,int version);
    String FIELDS="id,supplier_id,exit_type,reason,evidence_file_id,status,created_by,created_at,reviewed_by,reviewed_at,review_comment,version";
    @Select("SELECT "+FIELDS+" FROM sup_exit_application WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} FOR UPDATE")
    ExitModels.Row get(long tenantId,long supplierId,long id);
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
    @Select("SELECT id,approved_by,comment,completion_scope,access_recovery_status,effective_at FROM sup_exit_result WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}")
    ExitModels.ResultRow findResult(long tenantId,long applicationId);
    @Insert("INSERT INTO sup_exit_event(id,tenant_id,application_id,action,comment,actor_id) VALUES(#{id},#{tenantId},#{applicationId},#{action},#{comment},#{actorId})")
    int event(long id,long tenantId,long applicationId,String action,String comment,long actorId);
    @Select("SELECT id,action,comment,actor_id,created_at FROM sup_exit_event WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} ORDER BY created_at,id")
    List<ExitModels.EventRow> events(long tenantId,long applicationId);
}

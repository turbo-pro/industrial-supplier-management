package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.*;

@Mapper
public interface BlacklistMapper extends TenantScopedMapper {
    String FIELDS="id,supplier_id,organization_id,supplier_code,supplier_name,reason,source_ref,status,review_comment,reviewed_by,reviewed_at,revoked_reason,revoked_by,revoked_at,created_by,version,created_at,updated_at";
    String SCOPE="<choose><when test=\"scopeType == 'TENANT_ALL'\"></when><when test=\"scopeType == 'ORGANIZATION_SET'\"> AND organization_id IN <foreach collection='organizationIds' item='o' open='(' separator=',' close=')'>#{o}</foreach></when><when test=\"scopeType == 'CREATED' or scopeType == 'OWNED'\"> AND created_by=#{actorId}</when><otherwise> AND 1=0</otherwise></choose>";
    @Select("<script>SELECT COUNT(*) FROM sup_blacklist_case WHERE tenant_id=#{tenantId}"+SCOPE+"<if test='supplierId != null'> AND supplier_id=#{supplierId}</if><if test='status != null'> AND status=#{status}</if></script>")
    long count(long tenantId,Long supplierId,String status,String scopeType,Set<Long> organizationIds,long actorId);
    @Select("<script>SELECT "+FIELDS+" FROM sup_blacklist_case WHERE tenant_id=#{tenantId}"+SCOPE+"<if test='supplierId != null'> AND supplier_id=#{supplierId}</if><if test='status != null'> AND status=#{status}</if> ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<BlacklistModels.Row> list(long tenantId,Long supplierId,String status,String scopeType,Set<Long> organizationIds,long actorId,int offset,int size);
    @Select("SELECT "+FIELDS+" FROM sup_blacklist_case WHERE tenant_id=#{tenantId} AND id=#{id}")
    BlacklistModels.Row get(long tenantId,long id);
    @Select("SELECT COUNT(*) FROM sup_blacklist_case WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND status IN ('DRAFT','SUBMITTED','APPROVED')")
    int openCase(long tenantId,long supplierId);
    @Select("SELECT id FROM sup_supplier WHERE tenant_id=#{tenantId} AND id=#{supplierId} FOR UPDATE")
    Long lockSupplier(long tenantId,long supplierId);
    @Select("SELECT COUNT(*) FROM sup_blacklist_case WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND status='APPROVED'")
    int active(long tenantId,long supplierId);
    @Insert("INSERT INTO sup_blacklist_case(id,tenant_id,supplier_id,organization_id,supplier_code,supplier_name,reason,source_ref,created_by,updated_by) VALUES(#{id},#{tenantId},#{supplierId},#{org},#{code},#{name},#{reason},#{sourceRef},#{actorId},#{actorId})")
    int insert(long id,long tenantId,long supplierId,long org,String code,String name,String reason,String sourceRef,long actorId);
    @Update("UPDATE sup_blacklist_case SET status='SUBMITTED',updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='DRAFT' AND version=#{version}")
    int submit(long tenantId,long id,long actorId,int version);
    @Update("UPDATE sup_blacklist_case SET status=CASE WHEN #{decision}='APPROVE' THEN 'APPROVED' ELSE 'REJECTED' END,review_comment=#{comment},reviewed_by=#{actorId},reviewed_at=CURRENT_TIMESTAMP(3),updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='SUBMITTED' AND version=#{version}")
    int review(long tenantId,long id,String decision,String comment,long actorId,int version);
    @Update("UPDATE sup_blacklist_case SET status='REVOKED',revoked_reason=#{reason},revoked_by=#{actorId},revoked_at=CURRENT_TIMESTAMP(3),updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='APPROVED' AND version=#{version}")
    int revoke(long tenantId,long id,String reason,long actorId,int version);
    @Insert("INSERT INTO sup_blacklist_event(id,tenant_id,case_id,action,from_status,to_status,comment,actor_id) VALUES(#{id},#{tenantId},#{caseId},#{action},#{fromStatus},#{toStatus},#{comment},#{actorId})")
    int event(long id,long tenantId,long caseId,String action,String fromStatus,String toStatus,String comment,long actorId);
    @Select("SELECT id,action,from_status,to_status,comment,actor_id,created_at FROM sup_blacklist_event WHERE tenant_id=#{tenantId} AND case_id=#{caseId} ORDER BY created_at,id")
    List<EventRow> events(long tenantId,long caseId);
    record EventRow(long id,String action,String fromStatus,String toStatus,String comment,long actorId,LocalDateTime createdAt) {}
}

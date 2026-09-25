package io.github.turbopro.ism.performance;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Set;

@Mapper
public interface PerformanceMapper extends TenantScopedMapper {
    String SCOPE = "<choose><when test=\"scopeType == 'TENANT_ALL'\"></when>"
            + "<when test=\"scopeType == 'ORGANIZATION_SET'\"> AND e.organization_id IN <foreach collection='organizationIds' item='o' open='(' separator=',' close=')'>#{o}</foreach></when>"
            + "<when test=\"scopeType == 'CREATED' or scopeType == 'OWNED'\"> AND e.created_by=#{actorId}</when>"
            + "<otherwise> AND 1=0</otherwise></choose>";
    String FIELDS = "e.id,e.organization_id,e.supplier_id,e.supplier_code,e.supplier_name,e.period_start,e.period_end,e.total_score,e.grade,e.status,e.quality_ncr_total,e.quality_ncr_open,e.safety_issue_total,e.safety_issue_open,e.submitted_at,e.reviewed_by,e.reviewed_at,e.review_comment,e.created_by,e.version,e.created_at,e.updated_at";
    String JOIN = " FROM per_supplier_evaluation e";

    @Select("SELECT quality_weight,delivery_weight,safety_weight,service_weight,version FROM per_score_rule WHERE tenant_id=#{tenantId}")
    RuleRow rule(long tenantId);
    @Insert("INSERT INTO per_score_rule(tenant_id,quality_weight,delivery_weight,safety_weight,service_weight,updated_by) VALUES(#{tenantId},#{quality},#{delivery},#{safety},#{service},#{actorId})")
    int insertRule(long tenantId, int quality, int delivery, int safety, int service, long actorId);
    @Update("UPDATE per_score_rule SET quality_weight=#{quality},delivery_weight=#{delivery},safety_weight=#{safety},service_weight=#{service},updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND version=#{version}")
    int updateRule(long tenantId, int quality, int delivery, int safety, int service, long actorId, int version);

    @Select("<script>SELECT COUNT(*)" + JOIN + " WHERE e.tenant_id=#{tenantId}" + SCOPE
            + "<if test='supplierId != null'> AND e.supplier_id=#{supplierId}</if>"
            + "<if test='status != null'> AND e.status=#{status}</if></script>")
    long count(long tenantId, Long supplierId, String status, String scopeType, Set<Long> organizationIds, long actorId);
    @Select("<script>SELECT " + FIELDS + JOIN + " WHERE e.tenant_id=#{tenantId}" + SCOPE
            + "<if test='supplierId != null'> AND e.supplier_id=#{supplierId}</if>"
            + "<if test='status != null'> AND e.status=#{status}</if>"
            + " ORDER BY e.period_end DESC,e.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<PerformanceModels.Row> list(long tenantId, Long supplierId, String status, String scopeType,
                                      Set<Long> organizationIds, long actorId, int offset, int size);
    @Select("SELECT " + FIELDS + JOIN + " WHERE e.tenant_id=#{tenantId} AND e.id=#{id}")
    PerformanceModels.Row get(long tenantId, long id);
    @Select("SELECT dimension_code,weight,score,comment,evidence_file_id FROM per_evaluation_item WHERE tenant_id=#{tenantId} AND evaluation_id=#{id} ORDER BY FIELD(dimension_code,'QUALITY','DELIVERY','SAFETY','SERVICE')")
    List<PerformanceModels.ItemRow> items(long tenantId, long id);

    @Insert("INSERT INTO per_supplier_evaluation(id,tenant_id,organization_id,supplier_id,supplier_code,supplier_name,period_start,period_end,total_score,grade,quality_ncr_total,quality_ncr_open,safety_issue_total,safety_issue_open,created_by,updated_by) VALUES(#{id},#{tenantId},#{org},#{supplierId},#{supplierCode},#{supplierName},#{start},#{end},#{total},#{grade},#{qualityTotal},#{qualityOpen},#{safetyTotal},#{safetyOpen},#{actorId},#{actorId})")
    int insert(long id, long tenantId, long org, long supplierId, String supplierCode, String supplierName, LocalDate start, LocalDate end,
               BigDecimal total, String grade, int qualityTotal, int qualityOpen, int safetyTotal,
               int safetyOpen, long actorId);
    @Update("UPDATE per_supplier_evaluation SET total_score=#{total},grade=#{grade},quality_ncr_total=#{qualityTotal},quality_ncr_open=#{qualityOpen},safety_issue_total=#{safetyTotal},safety_issue_open=#{safetyOpen},status='DRAFT',submitted_at=NULL,review_comment=NULL,reviewed_by=NULL,reviewed_at=NULL,updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status IN ('DRAFT','REJECTED') AND version=#{version}")
    int update(long tenantId, long id, BigDecimal total, String grade, int qualityTotal, int qualityOpen,
               int safetyTotal, int safetyOpen, long actorId, int version);
    @Delete("DELETE FROM per_evaluation_item WHERE tenant_id=#{tenantId} AND evaluation_id=#{id}")
    int deleteItems(long tenantId, long id);
    @Insert("INSERT INTO per_evaluation_item(evaluation_id,tenant_id,dimension_code,weight,score,comment,evidence_file_id) VALUES(#{id},#{tenantId},#{dimension},#{weight},#{score},#{comment},#{fileId})")
    int insertItem(long id, long tenantId, String dimension, int weight, BigDecimal score, String comment, long fileId);
    @Update("UPDATE per_supplier_evaluation SET status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP(3),updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='DRAFT' AND version=#{version}")
    int submit(long tenantId, long id, long actorId, int version);
    @Update("UPDATE per_supplier_evaluation SET status=CASE WHEN #{decision}='APPROVE' THEN 'APPROVED' ELSE 'REJECTED' END,review_comment=#{comment},reviewed_by=#{actorId},reviewed_at=CURRENT_TIMESTAMP(3),updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='SUBMITTED' AND version=#{version}")
    int review(long tenantId, long id, String decision, String comment, long actorId, int version);
    @Insert("INSERT INTO per_evaluation_event(id,tenant_id,evaluation_id,action,from_status,to_status,comment,actor_id) VALUES(#{eventId},#{tenantId},#{evaluationId},#{action},#{fromStatus},#{toStatus},#{comment},#{actorId})")
    int insertEvent(long eventId, long tenantId, long evaluationId, String action, String fromStatus,
                    String toStatus, String comment, long actorId);
    @Select("SELECT id,action,from_status,to_status,comment,actor_id,created_at FROM per_evaluation_event WHERE tenant_id=#{tenantId} AND evaluation_id=#{id} ORDER BY created_at,id")
    List<EventRow> events(long tenantId, long id);
    record EventRow(long id, String action, String fromStatus, String toStatus, String comment, long actorId,
                    LocalDateTime createdAt) {}
    record RuleRow(int qualityWeight, int deliveryWeight, int safetyWeight, int serviceWeight, int version) {}
}

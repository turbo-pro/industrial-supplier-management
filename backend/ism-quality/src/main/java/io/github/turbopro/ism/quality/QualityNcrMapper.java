package io.github.turbopro.ism.quality;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Mapper
public interface QualityNcrMapper extends TenantScopedMapper {
    String SCOPE = "<choose><when test=\"scopeType == 'TENANT_ALL'\"></when>"
            + "<when test=\"scopeType == 'ORGANIZATION_SET'\"> AND x.organization_id IN <foreach collection='organizationIds' item='o' open='(' separator=',' close=')'>#{o}</foreach></when>"
            + "<when test=\"scopeType == 'PROJECT_SET'\"> AND x.project_id IN <foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach></when>"
            + "<when test=\"scopeType == 'OWNED'\"> AND x.responsible_user_id=#{actorId}</when>"
            + "<when test=\"scopeType == 'CREATED'\"> AND x.created_by=#{actorId}</when>"
            + "<otherwise> AND 1=0</otherwise></choose>";
    String FILTER = "<if test='keyword != null'> AND (x.ncr_no LIKE #{keyword} ESCAPE '=' OR x.title LIKE #{keyword} ESCAPE '=' OR s.supplier_name LIKE #{keyword} ESCAPE '=')</if>"
            + "<if test='status != null'> AND x.status=#{status}</if>";
    String JOINS = " FROM qua_nonconformance x JOIN prj_project p ON p.id=x.project_id AND p.tenant_id=x.tenant_id JOIN sup_supplier s ON s.id=x.supplier_id AND s.tenant_id=x.tenant_id";
    String FIELDS = "x.id,x.organization_id,x.project_id,x.supplier_id,p.project_code,p.project_name,s.supplier_code,s.supplier_name,x.ncr_no,x.title,x.category,x.severity,x.description,x.inspection_date,x.inspected_quantity,x.defective_quantity,x.unit,x.evidence_file_id,x.deadline,x.responsible_user_id,x.status,x.root_cause,x.correction,x.preventive_action,x.action_file_id,x.submitted_at,x.verification_result,x.verification_comment,x.verified_by,x.verified_at,x.created_by,x.version,x.created_at,x.updated_at";

    @Select("SELECT COUNT(*) total,COALESCE(SUM(CASE WHEN status<>'CLOSED' THEN 1 ELSE 0 END),0) open_count FROM qua_nonconformance WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND inspection_date BETWEEN #{start} AND #{end}")
    FactRow performanceFacts(long tenantId, long supplierId, LocalDate start, LocalDate end);
    record FactRow(int total, int openCount) {}

    @Select("SELECT p.organization_id,p.supplier_id FROM prj_project p JOIN sup_supplier s ON s.id=p.supplier_id AND s.tenant_id=p.tenant_id WHERE p.tenant_id=#{tenantId} AND p.id=#{projectId} AND p.status='ACTIVE' AND s.status='ACTIVE' AND s.deleted=0")
    QualityNcrModels.ProjectRef activeProject(long tenantId, long projectId);

    @Select("SELECT COUNT(*) FROM res_file_object WHERE tenant_id=#{tenantId} AND id=#{fileId} AND status='ACTIVE'")
    int activeFile(long tenantId, long fileId);

    @Select("SELECT COUNT(*) FROM iam_user WHERE tenant_id=#{tenantId} AND id=#{userId} AND status='ACTIVE' AND deleted=0")
    int activeUser(long tenantId, long userId);

    @Select("<script>SELECT COUNT(*)" + JOINS + " WHERE x.tenant_id=#{tenantId}" + SCOPE + FILTER + "</script>")
    long count(long tenantId, String keyword, String status, String scopeType,
               Set<Long> organizationIds, Set<Long> projectIds, long actorId);

    @Select("<script>SELECT " + FIELDS + JOINS + " WHERE x.tenant_id=#{tenantId}" + SCOPE + FILTER + " ORDER BY x.updated_at DESC,x.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<QualityNcrModels.Row> list(long tenantId, String keyword, String status, String scopeType,
                                     Set<Long> organizationIds, Set<Long> projectIds, long actorId, int offset, int size);

    @Select("SELECT " + FIELDS + JOINS + " WHERE x.tenant_id=#{tenantId} AND x.id=#{id}")
    QualityNcrModels.Row get(long tenantId, long id);

    @Insert("INSERT INTO qua_nonconformance(id,tenant_id,organization_id,project_id,supplier_id,ncr_no,title,category,severity,description,inspection_date,inspected_quantity,defective_quantity,unit,evidence_file_id,deadline,responsible_user_id,created_by,updated_by) VALUES(#{id},#{tenantId},#{org},#{projectId},#{supplierId},#{ncrNo},#{title},#{category},#{severity},#{description},#{inspectionDate},#{inspectedQuantity},#{defectiveQuantity},#{unit},#{evidenceFileId},#{deadline},#{responsibleUserId},#{actorId},#{actorId})")
    int insert(long id, long tenantId, long org, long projectId, long supplierId, String ncrNo,
               String title, String category, String severity, String description, LocalDate inspectionDate,
               BigDecimal inspectedQuantity, BigDecimal defectiveQuantity, String unit,
               long evidenceFileId, LocalDate deadline, long responsibleUserId, long actorId);

    @Update("UPDATE qua_nonconformance SET status='PENDING_REVIEW',root_cause=#{rootCause},correction=#{correction},preventive_action=#{preventiveAction},action_file_id=#{fileId},submitted_at=CURRENT_TIMESTAMP(3),verification_result=NULL,verification_comment=NULL,verified_by=NULL,verified_at=NULL,updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='OPEN' AND version=#{version}")
    int rectify(long tenantId, long id, String rootCause, String correction, String preventiveAction,
                long fileId, long actorId, int version);

    @Update("UPDATE qua_nonconformance SET status=CASE WHEN #{decision}='PASS' THEN 'CLOSED' ELSE 'OPEN' END,verification_result=#{decision},verification_comment=#{comment},verified_by=#{actorId},verified_at=CURRENT_TIMESTAMP(3),updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='PENDING_REVIEW' AND version=#{version}")
    int verify(long tenantId, long id, String decision, String comment, long actorId, int version);

    @Insert("INSERT INTO qua_ncr_event(id,tenant_id,ncr_id,action,from_status,to_status,note,file_id,actor_id) VALUES(#{eventId},#{tenantId},#{ncrId},#{action},#{fromStatus},#{toStatus},#{note},#{fileId},#{actorId})")
    int insertEvent(long eventId, long tenantId, long ncrId, String action, String fromStatus,
                    String toStatus, String note, Long fileId, long actorId);

    @Select("SELECT id,action,from_status,to_status,note,file_id,actor_id,created_at FROM qua_ncr_event WHERE tenant_id=#{tenantId} AND ncr_id=#{ncrId} ORDER BY created_at,id")
    List<EventRow> events(long tenantId, long ncrId);

    record EventRow(long id, String action, String fromStatus, String toStatus, String note,
                    Long fileId, long actorId, LocalDateTime createdAt) {}
}

package io.github.turbopro.ism.safety;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Mapper
public interface SafetyCredentialMapper extends TenantScopedMapper {
    @Select("SELECT id,organization_id,supplier_id,project_id,person_code,person_name,special_work_type,status,created_by FROM res_supplier_person WHERE tenant_id=#{tenantId} AND id=#{personId} FOR UPDATE")
    SafetyCredentialModels.PersonRef personForEntry(long tenantId,long personId);
    String SCOPE = "<choose>"
            + "<when test=\"scopeType == 'TENANT_ALL'\"></when>"
            + "<when test=\"scopeType == 'ORGANIZATION_SET'\"> AND p.organization_id IN <foreach collection='organizationIds' item='o' open='(' separator=',' close=')'>#{o}</foreach></when>"
            + "<when test=\"scopeType == 'PROJECT_SET'\"> AND p.project_id IN <foreach collection='projectIds' item='pId' open='(' separator=',' close=')'>#{pId}</foreach></when>"
            + "<when test=\"scopeType == 'CREATED'\"> AND c.created_by=#{actorId}</when>"
            + "<otherwise> AND 1=0</otherwise></choose>";
    String FIELDS = "c.id,p.organization_id,c.supplier_id,c.person_id,p.project_id,p.person_code,p.person_name,c.credential_no,c.credential_kind,c.work_type,c.title,c.exam_score,c.passed,c.effective_date,c.expiry_date,c.file_id,c.status,c.review_comment,c.reviewed_by,c.reviewed_at,c.created_by,c.version,c.created_at,c.updated_at";

    @Select("SELECT id,organization_id,supplier_id,project_id,person_code,person_name,special_work_type,status,created_by FROM res_supplier_person WHERE tenant_id=#{tenantId} AND id=#{personId}")
    SafetyCredentialModels.PersonRef person(long tenantId, long personId);

    @Select("SELECT COUNT(*) FROM res_file_object WHERE tenant_id=#{tenantId} AND id=#{fileId} AND status='ACTIVE'")
    int activeFile(long tenantId, long fileId);

    @Select("<script>SELECT COUNT(*) FROM saf_person_credential c JOIN res_supplier_person p ON p.id=c.person_id AND p.tenant_id=c.tenant_id WHERE c.tenant_id=#{tenantId}"
            + SCOPE + "<if test='personId != null'> AND c.person_id=#{personId}</if>"
            + "<if test='kind != null'> AND c.credential_kind=#{kind}</if>"
            + "<if test='status != null'> AND c.status=#{status}</if></script>")
    long count(long tenantId, Long personId, String kind, String status, String scopeType,
               Set<Long> organizationIds, Set<Long> projectIds, long actorId);

    @Select("<script>SELECT " + FIELDS + " FROM saf_person_credential c JOIN res_supplier_person p ON p.id=c.person_id AND p.tenant_id=c.tenant_id WHERE c.tenant_id=#{tenantId}"
            + SCOPE + "<if test='personId != null'> AND c.person_id=#{personId}</if>"
            + "<if test='kind != null'> AND c.credential_kind=#{kind}</if>"
            + "<if test='status != null'> AND c.status=#{status}</if>"
            + " ORDER BY c.updated_at DESC,c.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<SafetyCredentialModels.Row> list(long tenantId, Long personId, String kind, String status,
                                           String scopeType, Set<Long> organizationIds,
                                           Set<Long> projectIds, long actorId, int offset, int size);

    @Select("SELECT " + FIELDS + " FROM saf_person_credential c JOIN res_supplier_person p ON p.id=c.person_id AND p.tenant_id=c.tenant_id WHERE c.tenant_id=#{tenantId} AND c.id=#{id}")
    SafetyCredentialModels.Row get(long tenantId, long id);

    @Insert("INSERT INTO saf_person_credential(id,tenant_id,organization_id,supplier_id,person_id,project_id,credential_no,credential_kind,work_type,title,exam_score,passed,effective_date,expiry_date,file_id,created_by,updated_by) VALUES(#{id},#{tenantId},#{org},#{supplierId},#{personId},#{projectId},#{credentialNo},#{kind},#{workType},#{title},#{examScore},#{passed},#{effectiveDate},#{expiryDate},#{fileId},#{actorId},#{actorId})")
    int insert(long id, long tenantId, long org, long supplierId, long personId, Long projectId,
               String credentialNo, String kind, String workType, String title,
               BigDecimal examScore, Boolean passed, LocalDate effectiveDate,
               LocalDate expiryDate, long fileId, long actorId);

    @Update("UPDATE saf_person_credential SET status=#{status},review_comment=#{comment},reviewed_by=#{actorId},reviewed_at=CURRENT_TIMESTAMP(3),updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status=#{expectedStatus} AND version=#{version}")
    int review(long tenantId, long id, String expectedStatus, String status, String comment,
               long actorId, int version);

    @Select("SELECT COUNT(*) FROM saf_person_credential WHERE tenant_id=#{tenantId} AND person_id=#{personId} AND credential_kind='TRAINING' AND status='VERIFIED' AND passed=1 AND effective_date<=CURRENT_DATE AND expiry_date>=CURRENT_DATE")
    int validTraining(long tenantId, long personId);

    @Select("SELECT COUNT(*) FROM saf_person_credential WHERE tenant_id=#{tenantId} AND person_id=#{personId} AND credential_kind='SPECIAL_WORK' AND work_type=#{workType} AND status='VERIFIED' AND effective_date<=CURRENT_DATE AND expiry_date>=CURRENT_DATE")
    int validSpecialWork(long tenantId, long personId, String workType);
}

package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.*;

@Mapper
public interface AdmissionMapper extends TenantScopedMapper {
    String SCOPE="<choose><when test=\"scopeType == 'TENANT_ALL'\"></when><when test=\"scopeType == 'ORGANIZATION_SET'\"> AND a.organization_id IN <foreach collection='organizationIds' item='o' open='(' separator=',' close=')'>#{o}</foreach></when><when test=\"scopeType == 'CREATED'\"> AND a.created_by=#{actorId}</when><otherwise> AND 1=0</otherwise></choose>";
    String FILTER="<if test='keyword != null and keyword != &quot;&quot;'> AND (a.application_no LIKE #{keyword} ESCAPE '=' OR s.supplier_code LIKE #{keyword} ESCAPE '=' OR s.supplier_name LIKE #{keyword} ESCAPE '=')</if><if test='status != null and status != &quot;&quot;'> AND a.status=#{status}</if>";
    @Select("<script>SELECT COUNT(*) FROM qua_admission_application a JOIN sup_supplier s ON s.id=a.supplier_id AND s.tenant_id=a.tenant_id WHERE a.tenant_id=#{tenantId}"+SCOPE+FILTER+"</script>")
    long count(long tenantId,String keyword,String status,String scopeType,Set<Long> organizationIds,long actorId);
    @Select("<script>SELECT a.id,a.organization_id,a.supplier_id,a.application_no,s.supplier_code,s.supplier_name,a.purchase_category,a.status,a.version,a.submitted_at,a.updated_at FROM qua_admission_application a JOIN sup_supplier s ON s.id=a.supplier_id AND s.tenant_id=a.tenant_id WHERE a.tenant_id=#{tenantId}"+SCOPE+FILTER+" ORDER BY a.updated_at DESC,a.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<AdmissionModels.Summary> list(long tenantId,String keyword,String status,String scopeType,Set<Long> organizationIds,long actorId,int offset,int size);
    @Select("SELECT a.id,a.organization_id,a.supplier_id,a.application_no,s.supplier_code,s.supplier_name,a.purchase_category,a.admission_reason,a.expected_annual_amount,a.currency,a.status,a.workflow_instance_id,a.current_reviewer_id,a.created_by,a.version,a.submitted_at,a.decided_at,a.created_at,a.updated_at FROM qua_admission_application a JOIN sup_supplier s ON s.id=a.supplier_id AND s.tenant_id=a.tenant_id WHERE a.tenant_id=#{tenantId} AND a.id=#{id}")
    AdmissionModels.Row find(long tenantId,long id);
    @Select("SELECT id,material_type,material_name,file_id,required_flag `required`,provided_flag provided,remark,sort_order FROM qua_admission_material WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} ORDER BY sort_order,id")
    List<AdmissionModels.MaterialRow> materials(long tenantId,long applicationId);
    @Select("SELECT id,action,from_status,to_status,comment_text,operator_id,operated_at FROM qua_admission_review WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} ORDER BY operated_at,id")
    List<AdmissionModels.ReviewRow> reviews(long tenantId,long applicationId);
    @Select("SELECT organization_id FROM sup_supplier WHERE tenant_id=#{tenantId} AND id=#{supplierId} AND deleted=0 AND status IN ('DRAFT','ACTIVE','SUSPENDED')") Long supplierOrganization(long tenantId,long supplierId);
    @Insert("INSERT INTO qua_admission_application(id,tenant_id,organization_id,supplier_id,application_no,purchase_category,admission_reason,expected_annual_amount,currency,created_by,updated_by) VALUES(#{id},#{tenantId},#{organizationId},#{supplierId},#{number},#{category},#{reason},#{amount},#{currency},#{actorId},#{actorId})")
    int insert(long id,long tenantId,long actorId,long organizationId,long supplierId,String number,String category,String reason,BigDecimal amount,String currency);
    @Update("UPDATE qua_admission_application SET purchase_category=#{category},admission_reason=#{reason},expected_annual_amount=#{amount},currency=#{currency},updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status IN ('DRAFT','REVISION_REQUIRED') AND version=#{version}")
    int update(long tenantId,long actorId,long id,String category,String reason,BigDecimal amount,String currency,int version);
    @Delete("DELETE FROM qua_admission_material WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}") int deleteMaterials(long tenantId,long applicationId);
    @Insert("INSERT INTO qua_admission_material(id,tenant_id,application_id,material_type,material_name,file_id,required_flag,provided_flag,remark,sort_order) VALUES(#{id},#{tenantId},#{applicationId},#{type},#{name},#{fileId},#{required},#{provided},#{remark},#{sortOrder})")
    int insertMaterial(long id,long tenantId,long applicationId,String type,String name,Long fileId,boolean required,boolean provided,String remark,int sortOrder);
    @Update("UPDATE qua_admission_application SET status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP(3),updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status IN ('DRAFT','REVISION_REQUIRED') AND version=#{version}")
    int submit(long tenantId,long actorId,long id,int version);
    @Update("UPDATE qua_admission_application SET status=#{status},decided_at=CASE WHEN #{status} IN ('APPROVED','REJECTED') THEN CURRENT_TIMESTAMP(3) ELSE NULL END,updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='SUBMITTED' AND version=#{version}")
    int review(long tenantId,long actorId,long id,String status,int version);
    @Insert("INSERT INTO qua_admission_review(id,tenant_id,application_id,action,from_status,to_status,comment_text,operator_id) VALUES(#{id},#{tenantId},#{applicationId},#{action},#{from},#{to},#{comment},#{actorId})")
    int insertReview(long id,long tenantId,long applicationId,String action,String from,String to,String comment,long actorId);
    @Update("UPDATE sup_supplier SET status='ACTIVE',updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{supplierId} AND deleted=0 AND status='DRAFT'") int activateSupplier(long tenantId,long actorId,long supplierId);
}

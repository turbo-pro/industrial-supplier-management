package io.github.turbopro.ism.qualification;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface AdmissionResultMapper extends TenantScopedMapper {
 String COLUMNS="CAST(id AS CHAR) id,application_no,purchase_category,result,public_comment,published_at";
 @Select("SELECT r.id,r.action,r.to_status,r.application_version,(SELECT COUNT(*) FROM qua_admission_review x WHERE x.tenant_id=r.tenant_id AND x.application_id=r.application_id AND x.application_version IS NULL AND x.operated_at=r.operated_at) same_instant_count FROM qua_admission_review r WHERE r.tenant_id=#{tenantId} AND r.application_id=#{id} ORDER BY r.application_version DESC,r.operated_at DESC,r.id DESC LIMIT 1") AdmissionResultModels.Review latestReview(long tenantId,long id);
 @Select("SELECT "+COLUMNS+" FROM qua_admission_public_result WHERE tenant_id=#{tenantId} AND application_id=#{id} AND source_version=#{version}") AdmissionResultModels.View byVersion(long tenantId,long id,int version);
 @Select("SELECT "+COLUMNS+" FROM qua_admission_public_result WHERE tenant_id=#{tenantId} AND application_id=#{id} ORDER BY published_at DESC,source_version DESC,id DESC LIMIT 50") List<AdmissionResultModels.View> employeeList(long tenantId,long id);
 @Select("SELECT "+COLUMNS+" FROM qua_admission_public_result WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} ORDER BY published_at DESC,source_version DESC,id DESC LIMIT #{size} OFFSET #{offset}") List<AdmissionResultModels.View> portalList(long tenantId,long supplierId,int offset,int size);
 @Select("SELECT COUNT(*) FROM qua_admission_public_result WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}") long count(long tenantId,long supplierId);
 @Insert("INSERT INTO qua_admission_public_result(id,tenant_id,supplier_id,application_id,review_id,source_version,application_no,purchase_category,result,public_comment,published_by) VALUES(#{id},#{tenantId},#{supplierId},#{applicationId},#{review},#{version},#{number},#{category},#{result},#{comment},#{actor})") int publish(long id,long tenantId,long supplierId,long applicationId,long review,int version,String number,String category,String result,String comment,long actor);
}

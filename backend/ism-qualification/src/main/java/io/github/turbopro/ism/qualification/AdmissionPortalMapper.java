package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper public interface AdmissionPortalMapper extends TenantScopedMapper {
 @Select("SELECT a.id,a.organization_id,a.supplier_id,a.application_no,s.supplier_code,s.supplier_name,a.purchase_category,a.purchase_category_id,a.admission_reason,a.expected_annual_amount,a.currency,a.status,a.workflow_instance_id,a.current_reviewer_id,a.created_by,a.version,a.submitted_at,a.decided_at,a.created_at,a.updated_at FROM qua_admission_application a JOIN sup_supplier s ON s.id=a.supplier_id AND s.tenant_id=a.tenant_id WHERE a.tenant_id=#{tenantId} AND a.id=#{id} FOR UPDATE") AdmissionModels.Row application(long tenantId,long id);
 String COLUMNS="id,application_id,supplier_id,application_version,application_no,instruction,state,version,submitted_by,CAST(submission_json AS CHAR) submission_json,close_note,created_at,submitted_at";
 @Select("SELECT "+COLUMNS+" FROM qua_admission_portal_request WHERE tenant_id=#{tenantId} AND id=#{id}") AdmissionPortalModels.Row find(long tenantId,long id);
 @Select("SELECT "+COLUMNS+" FROM qua_admission_portal_request WHERE tenant_id=#{tenantId} AND id=#{id} FOR UPDATE") AdmissionPortalModels.Row lock(long tenantId,long id);
 @Select("SELECT id FROM qua_admission_application WHERE tenant_id=#{tenantId} AND id=#{id} FOR UPDATE") Long lockApplication(long tenantId,long id);
 @Select("SELECT id FROM qua_admission_portal_request WHERE tenant_id=#{tenantId} AND application_id=#{id} AND state IN ('OPEN','SUBMITTED') FOR UPDATE") Long active(long tenantId,long id);
 @Select("SELECT "+COLUMNS+" FROM qua_admission_portal_request WHERE tenant_id=#{tenantId} AND application_id=#{id} ORDER BY id DESC LIMIT 50") List<AdmissionPortalModels.Row> employeeList(long tenantId,long id);
 @Select("SELECT "+COLUMNS+" FROM qua_admission_portal_request WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} ORDER BY id DESC LIMIT #{size} OFFSET #{offset}") List<AdmissionPortalModels.Row> portalList(long tenantId,long supplierId,int offset,int size);
 @Select("SELECT COUNT(*) FROM qua_admission_portal_request WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}") long count(long tenantId,long supplierId);
 @Select("SELECT material_type,material_name,file_id,file_name,sort_order FROM qua_admission_portal_item WHERE tenant_id=#{tenantId} AND request_id=#{id} ORDER BY sort_order,material_type") List<AdmissionPortalModels.ItemRow> items(long tenantId,long id);
 @Insert("INSERT INTO qua_admission_portal_request(id,tenant_id,supplier_id,application_id,application_version,application_no,instruction,published_by) VALUES(#{id},#{tenantId},#{supplierId},#{applicationId},#{version},#{number},#{instruction},#{actor})") int publish(long id,long tenantId,long supplierId,long applicationId,int version,String number,String instruction,long actor);
 @Insert("INSERT INTO qua_admission_portal_item(tenant_id,request_id,material_type,material_name,sort_order) VALUES(#{tenantId},#{id},#{type},#{name},#{sort})") int insertItem(long tenantId,long id,String type,String name,int sort);
 @Insert("INSERT INTO qua_portal_upload_use(upload_id,tenant_id,supplier_id,admission_request_id,usage_kind) VALUES(#{upload},#{tenantId},#{supplierId},#{id},'ADMISSION')") int consume(long upload,long tenantId,long supplierId,long id);
 @Update("UPDATE qua_admission_portal_item SET upload_id=#{upload},file_id=#{file},file_name=#{name} WHERE tenant_id=#{tenantId} AND request_id=#{id} AND material_type=#{type} AND upload_id IS NULL") int attach(long tenantId,long id,String type,long upload,long file,String name);
 @Update("UPDATE qua_admission_portal_request SET state='SUBMITTED',version=version+1,submitted_by=#{actor},submission_json=#{json},submitted_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenantId} AND id=#{id} AND state='OPEN' AND version=#{version}") int submit(long tenantId,long id,long actor,String json,int version);
 @Update("UPDATE qua_admission_portal_request SET state=#{state},close_note=#{note},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND version=#{version} AND state IN ('OPEN','SUBMITTED')") int close(long tenantId,long id,String state,String note,int version);
}

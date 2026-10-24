package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PortalCertificateSubmissionMapper extends TenantScopedMapper {
    @Select("SELECT qualification_id,account_id,request_json,request_key,file_name,created_at FROM qua_portal_submission WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND request_key=#{key} FOR UPDATE")
    PortalCertificateSubmissionModels.Row byKey(long tenantId,long supplierId,String key);
    @Insert("INSERT INTO qua_portal_submission(qualification_id,tenant_id,supplier_id,account_id,request_key,upload_id,request_json,file_name) VALUES(#{id},#{tenantId},#{supplierId},#{accountId},#{key},#{uploadId},#{request},#{fileName})")
    int insert(long id,long tenantId,long supplierId,long accountId,String key,long uploadId,String request,String fileName);
    String FROM=" FROM qua_portal_submission p JOIN qua_supplier_qualification q ON q.id=p.qualification_id AND q.tenant_id=p.tenant_id AND q.supplier_id=p.supplier_id JOIN qua_qualification_type t ON t.id=q.qualification_type_id AND t.tenant_id=p.tenant_id WHERE p.tenant_id=#{tenantId} AND p.supplier_id=#{supplierId}";
    String SELECT="SELECT CAST(q.id AS CHAR) id,p.request_key,t.type_name,q.certificate_no,q.status,p.file_name,p.created_at submitted_at";
    @Select(SELECT+FROM+" AND q.id=#{id}") PortalCertificateSubmissionModels.View find(long tenantId,long supplierId,long id);
    @Select("SELECT COUNT(*)"+FROM) long count(long tenantId,long supplierId);
    @Select(SELECT+FROM+" ORDER BY p.created_at DESC,p.qualification_id DESC LIMIT #{size} OFFSET #{offset}") List<PortalCertificateSubmissionModels.View> list(long tenantId,long supplierId,int offset,int size);
}

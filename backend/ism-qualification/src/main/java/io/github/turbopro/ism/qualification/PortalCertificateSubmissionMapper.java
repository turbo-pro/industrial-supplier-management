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
    @Insert("INSERT INTO qua_portal_renewal(qualification_id,tenant_id,supplier_id,parent_id,parent_version) VALUES(#{id},#{tenantId},#{supplierId},#{parentId},#{version})")
    int insertRenewal(long id,long tenantId,long supplierId,long parentId,int version);
    String FROM=" FROM qua_portal_submission p JOIN qua_supplier_qualification q ON q.id=p.qualification_id AND q.tenant_id=p.tenant_id AND q.supplier_id=p.supplier_id JOIN qua_qualification_type t ON t.id=q.qualification_type_id AND t.tenant_id=p.tenant_id LEFT JOIN qua_portal_renewal r ON r.qualification_id=q.id AND r.tenant_id=p.tenant_id AND r.supplier_id=p.supplier_id LEFT JOIN qua_supplier_qualification old ON old.id=r.parent_id AND old.tenant_id=p.tenant_id AND old.supplier_id=p.supplier_id WHERE p.tenant_id=#{tenantId} AND p.supplier_id=#{supplierId}";
    String SELECT="SELECT CAST(q.id AS CHAR) id,p.request_key,t.type_name,q.certificate_no,q.status,p.file_name,p.created_at submitted_at,CAST(old.id AS CHAR) parent_qualification_id,old.certificate_no parent_certificate_no";
    @Select(SELECT+FROM+" AND q.id=#{id}") PortalCertificateSubmissionModels.View find(long tenantId,long supplierId,long id);
    @Select("SELECT COUNT(*)"+FROM) long count(long tenantId,long supplierId);
    @Select(SELECT+FROM+" ORDER BY p.created_at DESC,p.qualification_id DESC LIMIT #{size} OFFSET #{offset}") List<PortalCertificateSubmissionModels.View> list(long tenantId,long supplierId,int offset,int size);
}

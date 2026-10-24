package io.github.turbopro.ism.resource.file;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PortalUploadMapper extends TenantScopedMapper {
    @Select("SELECT upload_id FROM res_portal_upload WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND account_id=#{accountId} AND request_key=#{key} FOR UPDATE")
    Long byKey(long tenantId,long supplierId,long accountId,String key);
    @Select("SELECT u.id,u.uploader_id,u.file_name,u.content_type,u.total_size,u.file_sha256,u.chunk_size,u.total_chunks,u.status,u.file_id,u.expires_at,u.version FROM res_portal_upload p JOIN res_upload_session u ON u.id=p.upload_id AND u.tenant_id=p.tenant_id WHERE p.tenant_id=#{tenantId} AND p.supplier_id=#{supplierId} AND p.account_id=#{accountId} AND p.upload_id=#{id} FOR UPDATE")
    FileModels.UploadRow lock(long tenantId,long supplierId,long accountId,long id);
    @Select("SELECT COUNT(*) FROM res_portal_upload WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND created_at>DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 24 HOUR) AND (#{accountId}=0 OR account_id=#{accountId})")
    long recent(long tenantId,long supplierId,long accountId);
    @Insert("INSERT INTO res_portal_upload(upload_id,tenant_id,supplier_id,account_id,request_key) VALUES(#{id},#{tenantId},#{supplierId},#{accountId},#{key})")
    int insert(long id,long tenantId,long supplierId,long accountId,String key);
}

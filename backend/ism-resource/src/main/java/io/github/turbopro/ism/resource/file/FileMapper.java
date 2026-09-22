package io.github.turbopro.ism.resource.file;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FileMapper extends TenantScopedMapper {
    @Select("SELECT setting_value FROM cfg_tenant_setting WHERE tenant_id=#{tenantId} AND setting_key=#{key}")
    String tenantSetting(long tenantId,String key);
    @Select("SELECT id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status,created_at FROM res_file_object WHERE tenant_id=#{tenantId} AND file_sha256=#{sha256} AND file_size=#{size} AND status='ACTIVE'")
    FileModels.FileRow fileByHash(long tenantId,String sha256,long size);
    @Select("SELECT id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status,created_at FROM res_file_object WHERE tenant_id=#{tenantId} AND id=#{id} AND status='ACTIVE'")
    FileModels.FileRow file(long tenantId,long id);
    @Select("SELECT id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status,created_at FROM res_file_object WHERE tenant_id=#{tenantId} AND status='ACTIVE' ORDER BY created_at DESC,id DESC")
    List<FileModels.FileRow> files(long tenantId);
    @Insert("INSERT INTO res_file_object(id,tenant_id,owner_id,original_name,content_type,file_size,file_sha256,storage_provider,object_key,status) VALUES(#{id},#{tenantId},#{ownerId},#{name},#{contentType},#{size},#{sha256},#{provider},#{objectKey},'ACTIVE')")
    int insertFile(long tenantId,long id,long ownerId,String name,String contentType,long size,String sha256,String provider,String objectKey);
    @Delete("DELETE FROM res_file_object WHERE tenant_id=#{tenantId} AND id=#{id} AND status='ACTIVE'")
    int deleteFile(long tenantId,long id);
    @Insert("INSERT INTO res_upload_session(id,tenant_id,uploader_id,file_name,content_type,total_size,file_sha256,chunk_size,total_chunks,status,file_id,expires_at) VALUES(#{id},#{tenantId},#{uploaderId},#{name},#{contentType},#{size},#{sha256},#{chunkSize},#{totalChunks},#{status},#{fileId},#{expiresAt})")
    int insertUpload(long tenantId,long id,long uploaderId,String name,String contentType,long size,String sha256,int chunkSize,int totalChunks,String status,Long fileId,LocalDateTime expiresAt);
    @Select("SELECT id,uploader_id,file_name,content_type,total_size,file_sha256,chunk_size,total_chunks,status,file_id,expires_at,version FROM res_upload_session WHERE tenant_id=#{tenantId} AND id=#{id}")
    FileModels.UploadRow upload(long tenantId,long id);
    @Select("SELECT chunk_index FROM res_upload_chunk WHERE tenant_id=#{tenantId} AND upload_id=#{uploadId} ORDER BY chunk_index")
    List<Integer> uploadedChunks(long tenantId,long uploadId);
    @Select("SELECT chunk_sha256 FROM res_upload_chunk WHERE tenant_id=#{tenantId} AND upload_id=#{uploadId} AND chunk_index=#{index}")
    String chunkHash(long tenantId,long uploadId,int index);
    @Insert("INSERT INTO res_upload_chunk(id,tenant_id,upload_id,chunk_index,chunk_size,chunk_sha256) VALUES(#{id},#{tenantId},#{uploadId},#{index},#{size},#{sha256})")
    int insertChunk(long tenantId,long id,long uploadId,int index,int size,String sha256);
    @Update("UPDATE res_upload_session SET status='ASSEMBLING',version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='UPLOADING' AND version=#{version}")
    int claimCompletion(long tenantId,long id,int version);
    @Update("UPDATE res_upload_session SET status='COMPLETED',file_id=#{fileId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status IN ('UPLOADING','ASSEMBLING')")
    int completeUpload(long tenantId,long id,long fileId);
}

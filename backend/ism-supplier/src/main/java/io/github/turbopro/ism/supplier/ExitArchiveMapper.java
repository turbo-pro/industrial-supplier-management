package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExitArchiveMapper extends TenantScopedMapper {
    @Insert("INSERT INTO sup_exit_archive(id,tenant_id,application_id,supplier_id,result_id,evidence_file_id,digest_sha256,item_count,entity_count,event_count,sealed_by) VALUES(#{id},#{tenantId},#{applicationId},#{supplierId},#{resultId},#{evidenceFileId},#{digest},#{itemCount},#{entityCount},#{eventCount},#{sealedBy})")
    int insert(long id,long tenantId,long applicationId,long supplierId,long resultId,long evidenceFileId,
        String digest,int itemCount,long entityCount,int eventCount,long sealedBy);
    @Select("SELECT id,application_id,supplier_id,result_id,evidence_file_id,schema_version,digest_sha256,item_count,entity_count,event_count,sealed_by,sealed_at FROM sup_exit_archive WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND application_id=#{applicationId}")
    ExitArchiveModels.Row find(long tenantId,long supplierId,long applicationId);
}

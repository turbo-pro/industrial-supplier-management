package io.github.turbopro.ism.integration.finance;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ManualClearanceMapper extends TenantScopedMapper {
    @Select("SELECT status,evidence_file_id,statement,submitted_by,CAST(UNIX_TIMESTAMP(submitted_at)*1000 AS UNSIGNED) submitted_at_millis,reviewed_by,CAST(UNIX_TIMESTAMP(reviewed_at)*1000 AS UNSIGNED) reviewed_at_millis,review_comment,version FROM int_financial_clearance WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} FOR UPDATE")
    ManualClearanceModels.Row get(long tenantId,long supplierId);
    @Insert("INSERT INTO int_financial_clearance(tenant_id,supplier_id,status,evidence_file_id,statement,submitted_by) VALUES(#{tenantId},#{supplierId},'SUBMITTED',#{fileId},#{statement},#{actorId})")
    int insert(long tenantId,long supplierId,long fileId,String statement,long actorId);
    @Update("UPDATE int_financial_clearance SET status='SUBMITTED',evidence_file_id=#{fileId},statement=#{statement},submitted_by=#{actorId},submitted_at=CURRENT_TIMESTAMP(3),reviewed_by=NULL,reviewed_at=NULL,review_comment=NULL,version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND version=#{version}")
    int resubmit(long tenantId,long supplierId,long fileId,String statement,long actorId,int version);
    @Update("UPDATE int_financial_clearance SET status=#{status},reviewed_by=#{actorId},reviewed_at=CURRENT_TIMESTAMP(3),review_comment=#{comment},version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND version=#{version}")
    int review(long tenantId,long supplierId,String status,String comment,long actorId,int version);
}

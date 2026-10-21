package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PortalInvitationMapper extends TenantScopedMapper {
    record Redemption(long id,String status,String recipientName,String recipientEmail,boolean valid) {}
    @Select("SELECT id,status,recipient_name,recipient_email,expires_at>UTC_TIMESTAMP(3) valid FROM sup_portal_invitation WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} FOR UPDATE")
    Redemption lockRedemption(long tenantId,long supplierId,long id);
    @Update("UPDATE sup_portal_invitation SET status='ACCEPTED',accepted_account_id=#{accountId},accepted_at=UTC_TIMESTAMP(3),version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} AND status='PENDING' AND expires_at>UTC_TIMESTAMP(3)")
    int accept(long tenantId,long supplierId,long id,long accountId);
    String COLUMNS="CAST(id AS CHAR) id,generation,recipient_name,recipient_email,CASE WHEN status='PENDING' AND expires_at<=UTC_TIMESTAMP(3) THEN 'EXPIRED' ELSE status END status,expires_at,created_at,revoked_at,revoke_reason,version";
    @Select("SELECT COALESCE(MAX(generation),0) FROM sup_portal_invitation WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}")
    int latest(long tenantId,long supplierId);
    @Select("SELECT "+COLUMNS+" FROM sup_portal_invitation WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} ORDER BY generation DESC LIMIT 100")
    List<PortalInvitationModels.Invitation> history(long tenantId,long supplierId);
    @Select("SELECT "+COLUMNS+" FROM sup_portal_invitation WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id}")
    PortalInvitationModels.Invitation find(long tenantId,long supplierId,long id);
    @Update("UPDATE sup_portal_invitation SET status='REPLACED',version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND status='PENDING' AND expires_at>UTC_TIMESTAMP(3)")
    int replacePending(long tenantId,long supplierId);
    @Insert("INSERT INTO sup_portal_invitation(id,tenant_id,supplier_id,generation,recipient_name,recipient_email,token_hash,expires_at,created_by) VALUES(#{id},#{tenantId},#{supplierId},#{generation},#{name},#{email},#{hash},DATE_ADD(UTC_TIMESTAMP(3),INTERVAL #{hours} HOUR),#{actorId})")
    int insert(long id,long tenantId,long supplierId,int generation,String name,String email,String hash,int hours,long actorId);
    @Update("UPDATE sup_portal_invitation SET status='REVOKED',revoked_by=#{actorId},revoked_at=UTC_TIMESTAMP(3),revoke_reason=#{reason},version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} AND version=#{version} AND status='PENDING' AND expires_at>UTC_TIMESTAMP(3)")
    int revoke(long tenantId,long supplierId,long id,int version,long actorId,String reason);
    @Update("UPDATE sup_portal_invitation SET status='REVOKED',revoked_by=#{actorId},revoked_at=UTC_TIMESTAMP(3),revoke_reason=#{reason},version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND status='PENDING' AND expires_at>UTC_TIMESTAMP(3)")
    int revokeForExit(long tenantId,long supplierId,long actorId,String reason);
}

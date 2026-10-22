package io.github.turbopro.ism.iam.portal;
import io.github.turbopro.ism.common.infrastructure.portal.PortalPasswordRecoveryAdministration.State;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface PortalPasswordRecoveryMapper extends TenantScopedMapper {
    record Redemption(long id,int accountVersion,int tokenVersion,boolean valid) {}
    @Select("SELECT CAST(r.id AS CHAR) id,CASE WHEN r.status='PENDING' AND (r.account_version!=a.version OR r.token_version!=a.token_version OR a.status!='ACTIVE') THEN 'INVALIDATED' WHEN r.status='PENDING' AND r.expires_at<=UTC_TIMESTAMP(3) THEN 'EXPIRED' ELSE r.status END status,r.expires_at,r.created_at,CAST(r.created_by AS CHAR) created_by FROM iam_portal_password_recovery r JOIN iam_portal_account a ON a.id=r.account_id AND a.tenant_id=r.tenant_id WHERE r.tenant_id=#{tenantId} AND r.account_id=#{accountId} ORDER BY r.id DESC LIMIT 1")
    State current(long tenantId,long accountId);
    @Update("UPDATE iam_portal_password_recovery SET status=#{status},revoked_at=UTC_TIMESTAMP(3),revoked_by=#{actorId} WHERE tenant_id=#{tenantId} AND account_id=#{accountId} AND status='PENDING'")
    int retire(long tenantId,long accountId,String status,long actorId);
    @Insert("INSERT INTO iam_portal_password_recovery(id,tenant_id,account_id,token_hash,account_version,token_version,expires_at,created_by,reason) VALUES(#{id},#{tenantId},#{accountId},#{hash},#{version},#{tokenVersion},UTC_TIMESTAMP(3)+INTERVAL 15 MINUTE,#{actorId},#{reason})")
    int insert(long id,long tenantId,long accountId,String hash,int version,int tokenVersion,long actorId,String reason);
    @Select("SELECT id,account_version,token_version,(status='PENDING' AND expires_at>UTC_TIMESTAMP(3)) valid FROM iam_portal_password_recovery WHERE tenant_id=#{tenantId} AND account_id=#{accountId} AND token_hash=#{hash} FOR UPDATE")
    Redemption lock(long tenantId,long accountId,String hash);
    @Update("UPDATE iam_portal_password_recovery SET status='CONSUMED',consumed_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenantId} AND id=#{id} AND status='PENDING'")
    int consume(long tenantId,long id);
}

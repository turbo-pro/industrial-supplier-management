package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PortalAccountMapper extends TenantScopedMapper {
    @Select("SELECT id,supplier_id,username,display_name,password_hash,status,token_version,failed_count,COALESCE(locked_until>UTC_TIMESTAMP(3),FALSE) locked FROM iam_portal_account WHERE tenant_id=#{tenantId} AND id=#{id}")
    PortalAuthModels.Account find(long tenantId,long id);
    @Select("SELECT id,supplier_id,username,display_name,password_hash,status,token_version,failed_count,COALESCE(locked_until>UTC_TIMESTAMP(3),FALSE) locked FROM iam_portal_account WHERE tenant_id=#{tenantId} AND id=#{id} FOR UPDATE")
    PortalAuthModels.Account lock(long tenantId,long id);
    @Update("UPDATE iam_portal_account SET failed_count=CASE WHEN locked_until IS NOT NULL AND locked_until<=UTC_TIMESTAMP(3) THEN 1 ELSE failed_count+1 END,locked_until=CASE WHEN locked_until IS NOT NULL AND locked_until<=UTC_TIMESTAMP(3) THEN NULL WHEN failed_count>=5 THEN UTC_TIMESTAMP(3)+INTERVAL 15 MINUTE ELSE NULL END WHERE tenant_id=#{tenantId} AND id=#{id}")
    int failure(long tenantId,long id);
    @Update("UPDATE iam_portal_account SET failed_count=0,locked_until=NULL,last_login_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenantId} AND id=#{id}")
    int success(long tenantId,long id);
    @Insert("INSERT INTO iam_portal_session(id,tenant_id,account_id,token_hash,token_version,expires_at) VALUES(#{id},#{tenantId},#{accountId},#{hash},#{version},UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)")
    int session(long id,long tenantId,long accountId,String hash,int version);
    @Select("SELECT expires_at FROM iam_portal_session WHERE tenant_id=#{tenantId} AND id=#{id}")
    java.time.LocalDateTime expiry(long tenantId,long id);
    @Select("SELECT id,account_id,token_version,(revoked_at IS NULL AND expires_at>UTC_TIMESTAMP(3)) valid FROM iam_portal_session WHERE tenant_id=#{tenantId} AND token_hash=#{hash} FOR UPDATE")
    PortalAuthModels.SessionRow lockSession(long tenantId,String hash);
    @Update("UPDATE iam_portal_session SET revoked_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenantId} AND id=#{id} AND account_id=#{accountId} AND revoked_at IS NULL")
    int revoke(long tenantId,long id,long accountId);
    @Select("SELECT id,account_id,token_version,(revoked_at IS NULL AND expires_at>UTC_TIMESTAMP(3)) valid FROM iam_portal_session WHERE tenant_id=#{tenantId} AND id=#{id} AND account_id=#{accountId} FOR UPDATE")
    PortalAuthModels.SessionRow lockOwnSession(long tenantId,long id,long accountId);
    @Update("UPDATE iam_portal_account SET password_hash=#{hash},token_version=token_version+1,version=version+1,failed_count=0,locked_until=NULL WHERE tenant_id=#{tenantId} AND id=#{id}")
    int changePassword(long tenantId,long id,String hash);
    @Update("UPDATE iam_portal_session SET revoked_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenantId} AND account_id=#{accountId} AND revoked_at IS NULL")
    int revokeAll(long tenantId,long accountId);
    @Insert("INSERT INTO iam_portal_account(id,tenant_id,supplier_id,username,display_name,email,password_hash) VALUES(#{id},#{tenantId},#{supplierId},#{username},#{displayName},#{email},#{hash})")
    int insert(long id,long tenantId,long supplierId,String username,String displayName,String email,String hash);
}

package io.github.turbopro.ism.iam.auth;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface AuthMapper {
    @Select("""
        SELECT u.id,u.tenant_id,u.username,u.display_name,u.password_hash,u.status,
               u.force_password_change,u.locked_until,u.failed_count,u.token_version
        FROM iam_user u JOIN iam_tenant t ON t.id=u.tenant_id
        WHERE t.tenant_code=#{tenantCode} AND t.status='ACTIVE'
          AND u.username=#{username} AND u.deleted=0
        """)
    AuthModels.AuthUser findForLogin(String tenantCode, String username);

    @Select("""
        SELECT u.id,u.tenant_id,u.username,u.display_name,u.password_hash,u.status,u.force_password_change,
               u.locked_until,u.failed_count,u.token_version
        FROM iam_user u JOIN iam_tenant t ON t.id=u.tenant_id
        WHERE u.id=#{id} AND u.deleted=0 AND t.status='ACTIVE'
        """)
    AuthModels.AuthUser findById(long id);

    @Update("UPDATE iam_user SET failed_count=#{count}, locked_until=#{lockedUntil} WHERE id=#{id}")
    int updateLoginFailure(long id, int count, LocalDateTime lockedUntil);

    @Update("UPDATE iam_user SET failed_count=0,locked_until=NULL,last_login_at=#{now} WHERE id=#{id}")
    int markLoginSuccess(long id, LocalDateTime now);

    @Insert("""
        INSERT INTO iam_refresh_token(id,tenant_id,user_id,token_hash,family_id,issued_at,expires_at,device_id,ip)
        VALUES(#{id},#{tenantId},#{userId},#{hash},#{familyId},#{issuedAt},#{expiresAt},#{deviceId},#{ip})
        """)
    int insertRefreshToken(long id, long tenantId, long userId, String hash, String familyId,
                           LocalDateTime issuedAt, LocalDateTime expiresAt, String deviceId, String ip);

    @Select("""
        SELECT id,tenant_id,user_id,token_hash,family_id,expires_at,revoked_at,replaced_by_hash,device_id
        FROM iam_refresh_token WHERE token_hash=#{hash} FOR UPDATE
        """)
    AuthModels.RefreshTokenRecord lockRefreshToken(String hash);

    @Select("""
        SELECT COUNT(*) FROM iam_refresh_token
        WHERE family_id=#{familyId} AND user_id=#{userId} AND revoked_at IS NULL AND expires_at>#{now}
        """)
    int countActiveFamily(String familyId, long userId, LocalDateTime now);

    @Update("""
        UPDATE iam_refresh_token SET revoked_at=#{now},revoke_reason=#{reason},replaced_by_hash=#{replacement}
        WHERE id=#{id} AND revoked_at IS NULL
        """)
    int revokeToken(long id, LocalDateTime now, String reason, String replacement);

    @Update("""
        UPDATE iam_refresh_token SET revoked_at=#{now},revoke_reason=#{reason}
        WHERE family_id=#{familyId} AND revoked_at IS NULL
        """)
    int revokeFamily(String familyId, LocalDateTime now, String reason);

    @Update("""
        UPDATE iam_user SET password_hash=#{hash},force_password_change=0,password_changed_at=#{now},
            token_version=token_version+1,failed_count=0,locked_until=NULL WHERE id=#{id}
        """)
    int changePassword(long id, String hash, LocalDateTime now);

    @Update("""
        UPDATE iam_refresh_token SET revoked_at=#{now},revoke_reason=#{reason}
        WHERE user_id=#{userId} AND revoked_at IS NULL
        """)
    int revokeUserTokens(long userId, LocalDateTime now, String reason);
}

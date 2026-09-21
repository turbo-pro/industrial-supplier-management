package io.github.turbopro.ism.iam.console;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.Set;

@Mapper
public interface ConsoleAuthMapper {
    @Select("""
        SELECT id,username,display_name,password_hash,status,force_password_change,
               locked_until,failed_count,token_version
        FROM plt_user WHERE username=#{username}
        """)
    ConsoleAuthModels.PlatformUser findForLogin(String username);

    @Select("""
        SELECT id,username,display_name,password_hash,status,force_password_change,
               locked_until,failed_count,token_version
        FROM plt_user WHERE id=#{id}
        """)
    ConsoleAuthModels.PlatformUser findById(long id);

    @Select("""
        SELECT DISTINCT p.permission_code
        FROM plt_user_role ur
        JOIN plt_role r ON r.id=ur.role_id AND r.status='ACTIVE'
        JOIN plt_role_permission rp ON rp.role_id=r.id
        JOIN plt_permission p ON p.id=rp.permission_id AND p.status='ACTIVE'
        WHERE ur.user_id=#{userId}
        """)
    Set<String> findPermissions(long userId);

    @Update("UPDATE plt_user SET failed_count=#{count},locked_until=#{lockedUntil} WHERE id=#{id}")
    int updateLoginFailure(long id, int count, LocalDateTime lockedUntil);

    @Update("UPDATE plt_user SET failed_count=0,locked_until=NULL,last_login_at=#{now} WHERE id=#{id}")
    int markLoginSuccess(long id, LocalDateTime now);

    @Insert("""
        INSERT INTO plt_refresh_token(id,user_id,token_hash,family_id,issued_at,expires_at,device_id,ip)
        VALUES(#{id},#{userId},#{hash},#{familyId},#{issuedAt},#{expiresAt},#{deviceId},#{ip})
        """)
    int insertRefreshToken(long id, long userId, String hash, String familyId,
                           LocalDateTime issuedAt, LocalDateTime expiresAt, String deviceId, String ip);

    @Select("""
        SELECT id,user_id,token_hash,family_id,expires_at,revoked_at,replaced_by_hash,device_id
        FROM plt_refresh_token WHERE token_hash=#{hash} FOR UPDATE
        """)
    ConsoleAuthModels.RefreshToken lockRefreshToken(String hash);

    @Select("""
        SELECT COUNT(*) FROM plt_refresh_token
        WHERE family_id=#{familyId} AND user_id=#{userId} AND revoked_at IS NULL AND expires_at>#{now}
        """)
    int countActiveFamily(String familyId, long userId, LocalDateTime now);

    @Update("""
        UPDATE plt_refresh_token SET revoked_at=#{now},revoke_reason=#{reason},replaced_by_hash=#{replacement}
        WHERE id=#{id} AND revoked_at IS NULL
        """)
    int revokeToken(long id, LocalDateTime now, String reason, String replacement);

    @Update("""
        UPDATE plt_refresh_token SET revoked_at=#{now},revoke_reason=#{reason}
        WHERE family_id=#{familyId} AND revoked_at IS NULL
        """)
    int revokeFamily(String familyId, LocalDateTime now, String reason);

    @Update("""
        UPDATE plt_user SET password_hash=#{hash},force_password_change=0,password_changed_at=#{now},
            token_version=token_version+1,failed_count=0,locked_until=NULL WHERE id=#{id}
        """)
    int changePassword(long id, String hash, LocalDateTime now);

    @Update("""
        UPDATE plt_refresh_token SET revoked_at=#{now},revoke_reason=#{reason}
        WHERE user_id=#{userId} AND revoked_at IS NULL
        """)
    int revokeUserTokens(long userId, LocalDateTime now, String reason);
}

package io.github.turbopro.ism.iam.console;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConsoleUserMapper {
    @Select("SELECT id,username,display_name,status,force_password_change,manual_locked,manual_lock_reason,locked_until,version FROM plt_user ORDER BY created_at,id")
    List<ConsoleUserModels.Row> users();
    @Select("SELECT ur.user_id,r.role_code FROM plt_user_role ur JOIN plt_role r ON r.id=ur.role_id ORDER BY ur.user_id,r.role_code")
    List<ConsoleUserModels.UserRole> roles();
    @Select("SELECT id FROM plt_user ORDER BY id LIMIT 1 FOR UPDATE")
    Long lockPlatform();
    @Select("SELECT id,username,display_name,status,force_password_change,manual_locked,manual_lock_reason,locked_until,version FROM plt_user WHERE id=#{id} FOR UPDATE")
    ConsoleUserModels.Row lockUser(long id);
    @Select("SELECT id,username,display_name,status,force_password_change,manual_locked,manual_lock_reason,locked_until,version FROM plt_user WHERE id=#{id}")
    ConsoleUserModels.Row findUser(long id);
    @Select("SELECT r.role_code FROM plt_user_role ur JOIN plt_role r ON r.id=ur.role_id WHERE ur.user_id=#{id} AND r.status='ACTIVE' ORDER BY r.role_code")
    List<String> userRoleCodes(long id);
    @Select("SELECT DISTINCT p.permission_code FROM plt_user_role ur JOIN plt_role r ON r.id=ur.role_id AND r.status='ACTIVE' JOIN plt_role_permission rp ON rp.role_id=r.id JOIN plt_permission p ON p.id=rp.permission_id AND p.status='ACTIVE' WHERE ur.user_id=#{id} ORDER BY p.permission_code")
    List<String> userPermissionCodes(long id);
    @Select("SELECT p.permission_code FROM plt_role r JOIN plt_role_permission rp ON rp.role_id=r.id JOIN plt_permission p ON p.id=rp.permission_id AND p.status='ACTIVE' WHERE r.role_code=#{roleCode} AND r.status='ACTIVE' ORDER BY p.permission_code")
    List<String> rolePermissionCodes(String roleCode);
    @Select("SELECT COUNT(*) FROM plt_role WHERE role_code=#{roleCode} AND status='ACTIVE'")
    int activeRole(String roleCode);
    @Select("SELECT COUNT(*) FROM plt_user u JOIN plt_user_role ur ON ur.user_id=u.id JOIN plt_role r ON r.id=ur.role_id WHERE u.status='ACTIVE' AND u.manual_locked=0 AND r.role_code='PLATFORM_ADMIN' AND r.status='ACTIVE'")
    int activeAdmins();
    @Select("SELECT COUNT(*) FROM plt_user_role ur JOIN plt_role r ON r.id=ur.role_id WHERE ur.user_id=#{id} AND r.role_code='PLATFORM_ADMIN' AND r.status='ACTIVE'")
    int isAdmin(long id);
    @Insert("INSERT INTO plt_user(id,username,display_name,password_hash,status,force_password_change) VALUES(#{id},#{username},#{name},#{hash},'ACTIVE',1)")
    int insertUser(long id,String username,String name,String hash);
    @Insert("INSERT INTO plt_user_role(user_id,role_id) SELECT #{id},id FROM plt_role WHERE role_code=#{roleCode} AND status='ACTIVE'")
    int assignRole(long id,String roleCode);
    @Delete("DELETE FROM plt_user_role WHERE user_id=#{id}")
    int clearRoles(long id);
    @Select("SELECT password_hash FROM plt_user WHERE id=#{id}")
    String passwordHash(long id);
    @Update("UPDATE plt_user SET token_version=token_version+1,version=version+1 WHERE id=#{id} AND version=#{version}")
    int bumpSessionVersion(long id,int version);
    @Update("UPDATE plt_user SET password_hash=#{hash},force_password_change=1,password_changed_at=NULL,failed_count=0,locked_until=NULL,token_version=token_version+1,version=version+1 WHERE id=#{id} AND version=#{version}")
    int resetPassword(long id,String hash,int version);
    @Update("UPDATE plt_user SET status=#{status},token_version=token_version+1,version=version+1 WHERE id=#{id} AND version=#{version}")
    int changeStatus(long id,String status,int version);
    @Update("UPDATE plt_user SET manual_locked=1,manual_lock_reason=#{reason},manual_locked_at=#{now},manual_locked_by=#{actorId},token_version=token_version+1,version=version+1 WHERE id=#{id} AND version=#{version}")
    int lockLogin(long id,String reason,LocalDateTime now,long actorId,int version);
    @Update("UPDATE plt_user SET manual_locked=0,manual_lock_reason=NULL,manual_locked_at=NULL,manual_locked_by=NULL,failed_count=0,locked_until=NULL,token_version=token_version+1,version=version+1 WHERE id=#{id} AND version=#{version}")
    int unlockLogin(long id,int version);
    @Update("UPDATE plt_refresh_token SET revoked_at=#{now},revoke_reason=#{reason} WHERE user_id=#{id} AND revoked_at IS NULL")
    int revokeTokens(long id,LocalDateTime now,String reason);
}

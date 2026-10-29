package io.github.turbopro.ism.iam.access;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import io.github.turbopro.ism.iam.authorization.GrantEntitlementSql;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

@Mapper
public interface AccessMapper extends TenantScopedMapper {
    @Select("SELECT organization_id FROM iam_user_organization WHERE tenant_id=#{tenantId} AND user_id=#{userId} AND is_primary=1")
    Long userPrimaryOrganization(long tenantId,long userId);

    @Select("""
        <script>SELECT u.id,u.username,u.display_name,u.status,u.force_password_change,u.manual_locked,u.manual_lock_reason,u.locked_until,u.version
        FROM iam_user u WHERE u.tenant_id=#{tenantId} AND u.deleted=0
        <choose><when test="scopeType == 'TENANT_ALL'"></when>
        <when test="scopeType == 'ORGANIZATION_SET' and organizations != null and !organizations.isEmpty()">
        AND EXISTS(SELECT 1 FROM iam_user_organization o WHERE o.tenant_id=#{tenantId} AND o.user_id=u.id AND o.is_primary=1
        AND o.organization_id IN <foreach collection='organizations' item='org' open='(' separator=',' close=')'>#{org}</foreach>)
        </when><otherwise>AND 1=0</otherwise></choose> ORDER BY u.created_at,u.id</script>
        """)
    List<AccessModels.UserRow> visibleUsers(long tenantId,String scopeType,Set<Long> organizations);
    @Select("""
        <script>SELECT ur.user_id,ur.role_id FROM iam_user_role ur JOIN iam_user u ON u.tenant_id=ur.tenant_id AND u.id=ur.user_id
        WHERE ur.tenant_id=#{tenantId} AND u.deleted=0 AND ur.user_id IN
        <foreach collection='userIds' item='user' open='(' separator=',' close=')'>#{user}</foreach> ORDER BY ur.user_id,ur.role_id</script>
        """)
    List<AccessModels.UserRoleRow> visibleUserRoles(long tenantId,Set<Long> userIds);

    @Select("SELECT id,role_code,role_name,built_in,status,version FROM iam_role WHERE tenant_id=#{tenantId} ORDER BY built_in DESC,created_at,id")
    List<AccessModels.RoleRow> roles(long tenantId);
    @Select("SELECT id,role_code,role_name,built_in,status,version FROM iam_role WHERE tenant_id=#{tenantId} AND id=#{id}")
    AccessModels.RoleRow role(long tenantId,long id);
    @Select("<script>SELECT p.permission_code FROM sys_permission p WHERE p.status='ACTIVE' AND EXISTS (SELECT 1 FROM iam_role r WHERE r.tenant_id=#{tenantId}) AND " + GrantEntitlementSql.PERMISSION + " ORDER BY p.permission_code</script>")
    List<String> activePermissionCodes(long tenantId,Set<String> modules);
    @Select("<script>SELECT m.menu_code FROM sys_menu m WHERE m.status='ACTIVE' AND EXISTS (SELECT 1 FROM iam_role r WHERE r.tenant_id=#{tenantId}) AND " + GrantEntitlementSql.MENU + " ORDER BY m.menu_code</script>")
    List<String> activeMenuCodes(long tenantId,Set<String> modules);
    @Select("<script>SELECT DISTINCT ds.resource_code FROM sys_data_scope_resource_module ds WHERE EXISTS (SELECT 1 FROM iam_role r WHERE r.tenant_id=#{tenantId}) AND " + GrantEntitlementSql.RESOURCE + " ORDER BY ds.resource_code</script>")
    List<String> tenantScopeResourceCodes(long tenantId,Set<String> modules);
    @Select("<script>SELECT p.permission_code AS code,p.permission_name AS name,p.permission_type AS type FROM sys_permission p WHERE p.status='ACTIVE' AND EXISTS (SELECT 1 FROM iam_role r WHERE r.tenant_id=#{tenantId}) AND " + GrantEntitlementSql.PERMISSION + " ORDER BY p.permission_code</script>")
    List<AccessModels.PermissionOption> activePermissionOptions(long tenantId,Set<String> modules);
    @Select("<script>SELECT m.menu_code AS code,m.menu_name AS name,parent.menu_code AS parentCode,m.route_path AS routePath FROM sys_menu m LEFT JOIN sys_menu parent ON parent.id=m.parent_id WHERE m.status='ACTIVE' AND EXISTS (SELECT 1 FROM iam_role r WHERE r.tenant_id=#{tenantId}) AND " + GrantEntitlementSql.MENU + " ORDER BY m.menu_code</script>")
    List<AccessModels.MenuOption> activeMenuOptions(long tenantId,Set<String> modules);
    @Select("SELECT CAST(id AS CHAR) AS id,organization_code AS code,organization_name AS name,organization_type AS type FROM iam_organization WHERE tenant_id=#{tenantId} AND status='ACTIVE' ORDER BY organization_code,id")
    List<AccessModels.OrganizationOption> activeOrganizationOptions(long tenantId);
    @Insert("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(#{id},#{tenantId},#{code},#{name},0,'ACTIVE')")
    int insertRole(long tenantId,long id,String code,String name);
    @Update("UPDATE iam_role SET role_name=#{name},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='ACTIVE' AND version=#{version}")
    int updateRole(long tenantId,long id,String name,int version);
    @Update("UPDATE iam_role SET version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='ACTIVE' AND built_in=0 AND version=#{version}")
    int advanceRoleGrantVersion(long tenantId,long id,int version);
    @Delete("DELETE FROM iam_role_permission WHERE tenant_id=#{tenantId} AND role_id=#{roleId}") int clearPermissions(long tenantId,long roleId);
    @Delete("DELETE FROM iam_role_menu WHERE tenant_id=#{tenantId} AND role_id=#{roleId}") int clearMenus(long tenantId,long roleId);
    @Delete("DELETE FROM iam_role_data_scope_organization WHERE tenant_id=#{tenantId} AND role_id=#{roleId}") int clearScopeOrganizations(long tenantId,long roleId);
    @Delete("DELETE FROM iam_role_data_scope WHERE tenant_id=#{tenantId} AND role_id=#{roleId}") int clearScopes(long tenantId,long roleId);
    @Insert("<script>INSERT INTO iam_role_permission(tenant_id,role_id,permission_id) SELECT #{tenantId},#{roleId},p.id FROM sys_permission p WHERE p.status='ACTIVE' AND " + GrantEntitlementSql.PERMISSION + " AND p.permission_code IN <foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach></script>")
    int grantPermissions(long tenantId,long roleId,Set<String> codes,Set<String> modules);
    @Insert("<script>INSERT INTO iam_role_menu(tenant_id,role_id,menu_id) SELECT #{tenantId},#{roleId},m.id FROM sys_menu m WHERE m.status='ACTIVE' AND " + GrantEntitlementSql.MENU + " AND m.menu_code IN <foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach></script>")
    int grantMenus(long tenantId,long roleId,Set<String> codes,Set<String> modules);
    @Insert("""
        INSERT INTO iam_role_menu(tenant_id,role_id,menu_id)
        SELECT DISTINCT #{tenantId},#{roleId},m.parent_id
        FROM sys_menu m JOIN iam_role_menu rm ON rm.menu_id=m.id
        WHERE rm.tenant_id=#{tenantId} AND rm.role_id=#{roleId} AND m.parent_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM iam_role_menu existing WHERE existing.tenant_id=#{tenantId}
                          AND existing.role_id=#{roleId} AND existing.menu_id=m.parent_id)
        """)
    int grantParentMenus(long tenantId,long roleId);
    @Insert("<script>INSERT INTO iam_role_data_scope(tenant_id,role_id,resource_code,scope_type) SELECT DISTINCT #{tenantId},#{roleId},ds.resource_code,#{type} FROM sys_data_scope_resource_module ds WHERE ds.resource_code=#{resource} AND " + GrantEntitlementSql.RESOURCE + "</script>")
    int grantScope(long tenantId,long roleId,String resource,String type,Set<String> modules);
    @Insert("<script>INSERT INTO iam_role_data_scope_organization(tenant_id,role_id,resource_code,organization_id) SELECT #{tenantId},#{roleId},#{resource},id FROM iam_organization WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int grantScopeOrganizations(long tenantId,long roleId,String resource,Set<Long> ids);
    @Select("SELECT id,username,display_name,status,force_password_change,manual_locked,manual_lock_reason,locked_until,version FROM iam_user WHERE tenant_id=#{tenantId} AND deleted=0 ORDER BY created_at,id")
    List<AccessModels.UserRow> users(long tenantId);
    @Select("SELECT ur.user_id,ur.role_id FROM iam_user_role ur JOIN iam_user u ON u.tenant_id=ur.tenant_id AND u.id=ur.user_id WHERE ur.tenant_id=#{tenantId} AND u.deleted=0 ORDER BY ur.user_id,ur.role_id")
    List<AccessModels.UserRoleRow> userRoles(long tenantId);
    @Select("SELECT id FROM iam_user WHERE tenant_id=#{tenantId} AND deleted=0 ORDER BY id LIMIT 1 FOR UPDATE")
    Long lockTenant(long tenantId);
    @Select("SELECT id,username,display_name,status,force_password_change,manual_locked,manual_lock_reason,locked_until,version FROM iam_user WHERE tenant_id=#{tenantId} AND id=#{userId} AND deleted=0 FOR UPDATE")
    AccessModels.UserRow userForStatusUpdate(long tenantId,long userId);
    @Select("SELECT id,username,display_name,status,force_password_change,manual_locked,manual_lock_reason,locked_until,version FROM iam_user WHERE tenant_id=#{tenantId} AND id=#{userId} AND deleted=0")
    AccessModels.UserRow findUser(long tenantId,long userId);
    @Select("SELECT role_id FROM iam_user_role WHERE tenant_id=#{tenantId} AND user_id=#{userId} ORDER BY role_id")
    List<Long> assignedUserRoleIds(long tenantId,long userId);
    @Select("<script>SELECT COUNT(*) FROM iam_role WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND id IN <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int activeRoleCount(long tenantId,Set<Long> roleIds);
    @Select("<script>SELECT DISTINCT p.permission_code FROM iam_role r JOIN iam_role_permission rp ON rp.tenant_id=r.tenant_id AND rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id AND p.status='ACTIVE' WHERE r.tenant_id=#{tenantId} AND r.status='ACTIVE' AND r.id IN <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY p.permission_code</script>")
    List<String> permissionCodesForRoles(long tenantId,Set<Long> roleIds);
    @Select("<script>SELECT DISTINCT m.menu_code FROM iam_role r JOIN iam_role_menu rm ON rm.tenant_id=r.tenant_id AND rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id AND m.status='ACTIVE' WHERE r.tenant_id=#{tenantId} AND r.status='ACTIVE' AND r.id IN <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY m.menu_code</script>")
    List<String> menuCodesForRoles(long tenantId,Set<Long> roleIds);
    @Select("<script>SELECT ds.role_id,ds.resource_code,ds.scope_type FROM iam_role r JOIN iam_role_data_scope ds ON ds.tenant_id=r.tenant_id AND ds.role_id=r.id WHERE r.tenant_id=#{tenantId} AND r.status='ACTIVE' AND r.id IN <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY ds.role_id,ds.resource_code</script>")
    List<AccessModels.RoleScopeRow> scopeRowsForRoles(long tenantId,Set<Long> roleIds);
    @Select("<script>SELECT dso.role_id,dso.resource_code,dso.organization_id FROM iam_role r JOIN iam_role_data_scope_organization dso ON dso.tenant_id=r.tenant_id AND dso.role_id=r.id WHERE r.tenant_id=#{tenantId} AND r.status='ACTIVE' AND r.id IN <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY dso.role_id,dso.resource_code,dso.organization_id</script>")
    List<AccessModels.RoleScopeOrganizationRow> scopeOrganizationsForRoles(long tenantId,Set<Long> roleIds);
    @Select("""
        SELECT COUNT(DISTINCT u.id) FROM iam_user u
        JOIN iam_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
        JOIN iam_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id
        WHERE u.tenant_id=#{tenantId} AND u.status='ACTIVE' AND u.manual_locked=0 AND u.deleted=0
          AND r.role_code='TENANT_ADMIN' AND r.status='ACTIVE'
        """)
    int activeTenantAdministrators(long tenantId);
    @Select("""
        SELECT COUNT(*) FROM iam_user_role ur JOIN iam_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id
        WHERE ur.tenant_id=#{tenantId} AND ur.user_id=#{userId}
          AND r.role_code='TENANT_ADMIN' AND r.status='ACTIVE'
        """)
    int tenantAdministratorRole(long tenantId,long userId);
    @Select("SELECT id FROM iam_role WHERE tenant_id=#{tenantId} AND role_code='TENANT_ADMIN' AND status='ACTIVE'")
    Long tenantAdminRoleId(long tenantId);
    @Update("UPDATE iam_user SET status=#{status},version=version+1,token_version=token_version+1 WHERE tenant_id=#{tenantId} AND id=#{userId} AND deleted=0 AND version=#{version}")
    int updateUserStatus(long tenantId,long userId,String status,int version);
    @Select("SELECT password_hash FROM iam_user WHERE tenant_id=#{tenantId} AND id=#{userId} AND deleted=0")
    String passwordHash(long tenantId,long userId);
    @Update("UPDATE iam_user SET password_hash=#{hash},force_password_change=1,password_changed_at=NULL,failed_count=0,locked_until=NULL,token_version=token_version+1,version=version+1 WHERE tenant_id=#{tenantId} AND id=#{userId} AND deleted=0 AND version=#{version}")
    int resetUserPassword(long tenantId,long userId,String hash,int version);
    @Update("UPDATE iam_user SET manual_locked=1,manual_lock_reason=#{reason},manual_locked_at=#{now},manual_locked_by=#{actorId},token_version=token_version+1,version=version+1 WHERE tenant_id=#{tenantId} AND id=#{userId} AND deleted=0 AND manual_locked=0 AND version=#{version}")
    int lockLogin(long tenantId,long userId,String reason,LocalDateTime now,long actorId,int version);
    @Update("UPDATE iam_user SET manual_locked=0,manual_lock_reason=NULL,manual_locked_at=NULL,manual_locked_by=NULL,locked_until=NULL,failed_count=0,token_version=token_version+1,version=version+1 WHERE tenant_id=#{tenantId} AND id=#{userId} AND deleted=0 AND version=#{version}")
    int unlockLogin(long tenantId,long userId,int version);
    @Update("UPDATE iam_refresh_token SET revoked_at=#{now},revoke_reason=#{reason} WHERE tenant_id=#{tenantId} AND user_id=#{userId} AND revoked_at IS NULL")
    int revokeRefreshTokens(long tenantId,long userId,LocalDateTime now,String reason);
    @Select("SELECT id FROM iam_user WHERE tenant_id=#{tenantId} AND id=#{userId} AND status='ACTIVE' AND deleted=0 FOR SHARE")
    Long activeUserForAssignment(long tenantId,long userId);
    @Insert("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status,force_password_change) VALUES(#{id},#{tenantId},#{username},#{displayName},#{passwordHash},'ACTIVE',1)")
    int insertUser(long tenantId,long id,String username,String displayName,String passwordHash);
    @Insert("INSERT INTO iam_user_organization(tenant_id,user_id,organization_id,is_primary) SELECT #{tenantId},#{userId},id,1 FROM iam_organization WHERE tenant_id=#{tenantId} AND id=#{organizationId} AND status='ACTIVE'")
    int insertUserOrganization(long tenantId,long userId,long organizationId);
    @Insert("INSERT INTO iam_user_context(tenant_id,user_id,current_organization_id) VALUES(#{tenantId},#{userId},#{organizationId})")
    int insertUserContext(long tenantId,long userId,long organizationId);
    @Delete("DELETE FROM iam_user_role WHERE tenant_id=#{tenantId} AND user_id=#{userId}") int clearUserRoles(long tenantId,long userId);
    @Insert("<script>INSERT INTO iam_user_role(tenant_id,user_id,role_id) SELECT #{tenantId},#{userId},id FROM iam_role WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int assignUserRoles(long tenantId,long userId,Set<Long> ids);
    @Update("UPDATE iam_user SET token_version=token_version+1,version=version+1 WHERE tenant_id=#{tenantId} AND id=#{userId} AND deleted=0 AND version=#{version}") int revokeUserSessions(long tenantId,long userId,int version);
}

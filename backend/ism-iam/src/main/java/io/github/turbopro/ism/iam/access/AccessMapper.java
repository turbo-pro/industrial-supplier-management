package io.github.turbopro.ism.iam.access;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Set;

@Mapper
public interface AccessMapper extends TenantScopedMapper {
    @Select("SELECT id,role_code,role_name,built_in,status,version FROM iam_role WHERE tenant_id=#{tenantId} ORDER BY built_in DESC,created_at,id")
    List<AccessModels.RoleRow> roles(long tenantId);
    @Select("SELECT id,role_code,role_name,built_in,status,version FROM iam_role WHERE tenant_id=#{tenantId} AND id=#{id}")
    AccessModels.RoleRow role(long tenantId,long id);
    @Insert("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(#{id},#{tenantId},#{code},#{name},0,'ACTIVE')")
    int insertRole(long tenantId,long id,String code,String name);
    @Update("UPDATE iam_role SET role_name=#{name},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='ACTIVE' AND version=#{version}")
    int updateRole(long tenantId,long id,String name,int version);
    @Delete("DELETE FROM iam_role_permission WHERE tenant_id=#{tenantId} AND role_id=#{roleId}") int clearPermissions(long tenantId,long roleId);
    @Delete("DELETE FROM iam_role_menu WHERE tenant_id=#{tenantId} AND role_id=#{roleId}") int clearMenus(long tenantId,long roleId);
    @Delete("DELETE FROM iam_role_data_scope_organization WHERE tenant_id=#{tenantId} AND role_id=#{roleId}") int clearScopeOrganizations(long tenantId,long roleId);
    @Delete("DELETE FROM iam_role_data_scope WHERE tenant_id=#{tenantId} AND role_id=#{roleId}") int clearScopes(long tenantId,long roleId);
    @Insert("<script>INSERT INTO iam_role_permission(tenant_id,role_id,permission_id) SELECT #{tenantId},#{roleId},id FROM sys_permission WHERE status='ACTIVE' AND permission_code IN <foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach></script>")
    int grantPermissions(long tenantId,long roleId,Set<String> codes);
    @Insert("<script>INSERT INTO iam_role_menu(tenant_id,role_id,menu_id) SELECT #{tenantId},#{roleId},id FROM sys_menu WHERE status='ACTIVE' AND menu_code IN <foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach></script>")
    int grantMenus(long tenantId,long roleId,Set<String> codes);
    @Insert("""
        INSERT INTO iam_role_menu(tenant_id,role_id,menu_id)
        SELECT DISTINCT #{tenantId},#{roleId},m.parent_id
        FROM sys_menu m JOIN iam_role_menu rm ON rm.menu_id=m.id
        WHERE rm.tenant_id=#{tenantId} AND rm.role_id=#{roleId} AND m.parent_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM iam_role_menu existing WHERE existing.tenant_id=#{tenantId}
                          AND existing.role_id=#{roleId} AND existing.menu_id=m.parent_id)
        """)
    int grantParentMenus(long tenantId,long roleId);
    @Insert("INSERT INTO iam_role_data_scope(tenant_id,role_id,resource_code,scope_type) VALUES(#{tenantId},#{roleId},#{resource},#{type})")
    int grantScope(long tenantId,long roleId,String resource,String type);
    @Insert("<script>INSERT INTO iam_role_data_scope_organization(tenant_id,role_id,resource_code,organization_id) SELECT #{tenantId},#{roleId},#{resource},id FROM iam_organization WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int grantScopeOrganizations(long tenantId,long roleId,String resource,Set<Long> ids);
    @Select("SELECT id,username,display_name,status,force_password_change,version FROM iam_user WHERE tenant_id=#{tenantId} AND deleted=0 ORDER BY created_at,id")
    List<AccessModels.UserRow> users(long tenantId);
    @Insert("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status,force_password_change) VALUES(#{id},#{tenantId},#{username},#{displayName},#{passwordHash},'ACTIVE',1)")
    int insertUser(long tenantId,long id,String username,String displayName,String passwordHash);
    @Insert("INSERT INTO iam_user_organization(tenant_id,user_id,organization_id,is_primary) SELECT #{tenantId},#{userId},id,1 FROM iam_organization WHERE tenant_id=#{tenantId} AND id=#{organizationId} AND status='ACTIVE'")
    int insertUserOrganization(long tenantId,long userId,long organizationId);
    @Insert("INSERT INTO iam_user_context(tenant_id,user_id,current_organization_id) VALUES(#{tenantId},#{userId},#{organizationId})")
    int insertUserContext(long tenantId,long userId,long organizationId);
    @Delete("DELETE FROM iam_user_role WHERE tenant_id=#{tenantId} AND user_id=#{userId}") int clearUserRoles(long tenantId,long userId);
    @Insert("<script>INSERT INTO iam_user_role(tenant_id,user_id,role_id) SELECT #{tenantId},#{userId},id FROM iam_role WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int assignUserRoles(long tenantId,long userId,Set<Long> ids);
    @Update("UPDATE iam_user SET token_version=token_version+1 WHERE tenant_id=#{tenantId} AND id=#{userId} AND deleted=0") int revokeUserSessions(long tenantId,long userId);
}

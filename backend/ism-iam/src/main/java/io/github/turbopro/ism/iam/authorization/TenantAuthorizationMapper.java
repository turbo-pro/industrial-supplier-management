package io.github.turbopro.ism.iam.authorization;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TenantAuthorizationMapper extends TenantScopedMapper {
    @Select("""
        SELECT DISTINCT p.permission_code
        FROM iam_user_role ur
        JOIN iam_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id AND r.status='ACTIVE'
        JOIN iam_role_permission rp ON rp.tenant_id=r.tenant_id AND rp.role_id=r.id
        JOIN sys_permission p ON p.id=rp.permission_id AND p.status='ACTIVE' AND p.permission_type='ACTION'
        WHERE ur.tenant_id=#{tenantId} AND ur.user_id=#{userId}
        """)
    List<String> actionPermissions(long tenantId, long userId);

    @Select("""
        SELECT DISTINCT p.permission_code
        FROM iam_user_role ur
        JOIN iam_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id AND r.status='ACTIVE'
        JOIN iam_role_permission rp ON rp.tenant_id=r.tenant_id AND rp.role_id=r.id
        JOIN sys_permission p ON p.id=rp.permission_id AND p.status='ACTIVE' AND p.permission_type='FIELD'
        WHERE ur.tenant_id=#{tenantId} AND ur.user_id=#{userId}
        """)
    List<String> fieldPermissions(long tenantId, long userId);

    @Select("""
        SELECT ds.role_id,ds.resource_code,ds.scope_type
        FROM iam_user_role ur
        JOIN iam_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id AND r.status='ACTIVE'
        JOIN iam_role_data_scope ds ON ds.tenant_id=r.tenant_id AND ds.role_id=r.id
        WHERE ur.tenant_id=#{tenantId} AND ur.user_id=#{userId}
        """)
    List<DataScopeRow> dataScopes(long tenantId, long userId);

    @Select("""
        SELECT dso.role_id,dso.resource_code,c.descendant_id AS organization_id
        FROM iam_user_role ur
        JOIN iam_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id AND r.status='ACTIVE'
        JOIN iam_role_data_scope_organization dso ON dso.tenant_id=r.tenant_id AND dso.role_id=r.id
        JOIN iam_organization_closure c ON c.tenant_id=dso.tenant_id AND c.ancestor_id=dso.organization_id
        WHERE ur.tenant_id=#{tenantId} AND ur.user_id=#{userId}
        """)
    List<OrganizationScopeRow> organizationScopes(long tenantId, long userId);

    record DataScopeRow(long roleId, String resourceCode, String scopeType) {}
    record OrganizationScopeRow(long roleId, String resourceCode, long organizationId) {}
}

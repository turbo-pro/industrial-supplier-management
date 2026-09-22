package io.github.turbopro.ism.iam.navigation;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NavigationMapper extends TenantScopedMapper {
    @Select("""
        SELECT DISTINCT m.id,m.parent_id,m.menu_code,m.menu_name,m.route_path,m.component_key,m.icon,m.sort_order
        FROM sys_menu m
        JOIN iam_role_menu rm ON rm.menu_id=m.id
        JOIN iam_user_role ur ON ur.tenant_id=rm.tenant_id AND ur.role_id=rm.role_id
        JOIN iam_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id AND r.status='ACTIVE'
        WHERE ur.tenant_id=#{tenantId} AND ur.user_id=#{userId} AND m.status='ACTIVE'
        ORDER BY m.sort_order,m.id
        """)
    List<NavigationModels.MenuRow> menus(long tenantId, long userId);
}

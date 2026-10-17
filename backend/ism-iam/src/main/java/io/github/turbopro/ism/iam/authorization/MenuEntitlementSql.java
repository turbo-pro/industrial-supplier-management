package io.github.turbopro.ism.iam.authorization;

/** Shared predicate for a sys_menu alias named m and the current tenantId parameter. */
public final class MenuEntitlementSql {
    private MenuEntitlementSql() {}

    public static final String ALLOWED = """
        (m.module_code IN ('SYSTEM','COMMON','MESSAGE','WORKFLOW') OR EXISTS (
            SELECT 1 FROM plt_tenant pt
            JOIN plt_package_version pv ON pv.id=pt.package_version_id AND pv.status='PUBLISHED'
            JOIN plt_package_module pm ON pm.package_version_id=pv.id AND pm.enabled=1
            JOIN plt_module module ON module.id=pm.module_id AND module.status='ACTIVE'
            WHERE pt.id=#{tenantId} AND module.module_code=m.module_code
        ))
        """;
}

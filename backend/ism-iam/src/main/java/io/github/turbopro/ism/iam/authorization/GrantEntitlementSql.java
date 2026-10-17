package io.github.turbopro.ism.iam.authorization;

/** IAM-owned predicates; enabled module codes arrive through the public platform contract. */
public final class GrantEntitlementSql {
    private GrantEntitlementSql() {}

    public static final String PERMISSION = """
        p.module_code IN
        <foreach collection='modules' item='module' open='(' separator=',' close=')'>#{module}</foreach>
        """;
    public static final String MENU = """
        m.module_code IN
        <foreach collection='modules' item='module' open='(' separator=',' close=')'>#{module}</foreach>
        """;
    public static final String RESOURCE = """
        EXISTS (SELECT 1 FROM sys_data_scope_resource_module resource_module
        WHERE resource_module.resource_code=ds.resource_code AND resource_module.module_code IN
        <foreach collection='modules' item='module' open='(' separator=',' close=')'>#{module}</foreach>)
        """;
}

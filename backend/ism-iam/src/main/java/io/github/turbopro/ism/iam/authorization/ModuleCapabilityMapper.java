package io.github.turbopro.ism.iam.authorization;

import io.github.turbopro.ism.common.api.authorization.ModuleCapabilityCatalog;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Set;

/** IAM-owned global catalogs; deliberately no tenant tables or principal data. */
@Mapper
public interface ModuleCapabilityMapper {
    String CATALOG = """
        SELECT 'MENU' AS kind,menu_code AS code,menu_name AS name,module_code
        FROM sys_menu WHERE status='ACTIVE' AND component_key&lt;&gt;'Layout'
        UNION ALL
        SELECT permission_type AS kind,permission_code AS code,permission_name AS name,module_code
        FROM sys_permission WHERE status='ACTIVE' AND permission_type IN ('ACTION','FIELD')
        """;
    String FILTER = """
        FROM (
        """ + CATALOG + """
        ) c WHERE module_code IN
        <foreach collection='modules' item='m' open='(' separator=',' close=')'>#{m}</foreach>
        """;
    @Select("<script>SELECT kind,COUNT(*) AS total " + FILTER + " GROUP BY kind ORDER BY kind</script>")
    List<Count> counts(Set<String> modules);
    @Select("<script>SELECT kind,code,name,module_code,#{change} AS `change` " + FILTER
            + " ORDER BY module_code,kind,code LIMIT #{limit}</script>")
    List<ModuleCapabilityCatalog.Capability> items(Set<String> modules,String change,int limit);
    record Count(String kind,long total) {}
}

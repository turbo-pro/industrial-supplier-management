package io.github.turbopro.ism.iam.support;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface SupportRoleMapper extends TenantScopedMapper {
 record Row(long id,String roleName,int version,String status){}
 @Select("SELECT id,role_name,version,status FROM iam_role WHERE tenant_id=#{tenantId} ORDER BY role_name,id") List<Row> roles(long tenantId);
 @Select("SELECT id,role_name,version,status FROM iam_role WHERE tenant_id=#{tenantId} AND id=#{id} FOR SHARE") Row role(long tenantId,long id);
 @Select("SELECT p.permission_code FROM iam_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id AND p.status='ACTIVE' WHERE rp.tenant_id=#{tenantId} AND rp.role_id=#{id} AND p.permission_code='supplier:master:view' AND p.module_code='SUPPLIER' FOR SHARE") List<String> readPermissions(long tenantId,long id);
 @Select("SELECT scope_type FROM iam_role_data_scope WHERE tenant_id=#{tenantId} AND role_id=#{id} AND resource_code='supplier:master' FOR SHARE") String scope(long tenantId,long id);
 @Select("""
 SELECT DISTINCT CAST(o.id AS CHAR) FROM iam_role_data_scope_organization g
 JOIN iam_organization_closure c ON c.tenant_id=g.tenant_id AND c.ancestor_id=g.organization_id
 JOIN iam_organization o ON o.tenant_id=c.tenant_id AND o.id=c.descendant_id AND o.status='ACTIVE'
 WHERE g.tenant_id=#{tenantId} AND g.role_id=#{id} AND g.resource_code='supplier:master'
 AND NOT EXISTS(SELECT 1 FROM iam_organization_closure a JOIN iam_organization parent ON parent.tenant_id=a.tenant_id AND parent.id=a.ancestor_id WHERE a.tenant_id=#{tenantId} AND a.descendant_id=o.id AND parent.status<>'ACTIVE')
 ORDER BY CAST(o.id AS CHAR) FOR SHARE
 """) List<String> organizations(long tenantId,long id);
}

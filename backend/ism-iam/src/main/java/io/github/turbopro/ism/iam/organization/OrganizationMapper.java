package io.github.turbopro.ism.iam.organization;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrganizationMapper extends TenantScopedMapper {
    @Select("SELECT id,parent_id,organization_code,organization_name,organization_type,status,sort_order,version FROM iam_organization WHERE tenant_id=#{tenantId} ORDER BY sort_order,created_at,id")
    List<OrganizationModels.OrganizationRow> list(long tenantId);

    @Select("SELECT id,parent_id,organization_code,organization_name,organization_type,status,sort_order,version FROM iam_organization WHERE tenant_id=#{tenantId} AND id=#{id}")
    OrganizationModels.OrganizationRow find(long tenantId, long id);

    @Insert("INSERT INTO iam_organization(id,tenant_id,parent_id,organization_code,organization_name,organization_type,status,sort_order) VALUES(#{id},#{tenantId},#{parentId},#{code},#{name},#{type},'ACTIVE',#{sortOrder})")
    int insert(long tenantId, long id, long parentId, String code, String name, String type, int sortOrder);

    @Insert("INSERT INTO iam_organization_closure(tenant_id,ancestor_id,descendant_id,depth) VALUES(#{tenantId},#{id},#{id},0)")
    int insertSelf(long tenantId, long id);

    @Insert("INSERT INTO iam_organization_closure(tenant_id,ancestor_id,descendant_id,depth) SELECT #{tenantId},ancestor_id,#{id},depth+1 FROM iam_organization_closure WHERE tenant_id=#{tenantId} AND descendant_id=#{parentId}")
    int insertAncestors(long tenantId, long id, long parentId);

    @Update("UPDATE iam_organization SET organization_name=#{name},sort_order=#{sortOrder},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND status='ACTIVE' AND version=#{version}")
    int update(long tenantId, long id, String name, int sortOrder, int version);

    @Select("SELECT COUNT(*) FROM iam_organization WHERE tenant_id=#{tenantId} AND parent_id=#{id} AND status='ACTIVE'")
    int activeChildCount(long tenantId, long id);

    @Update("UPDATE iam_organization SET status='DISABLED',version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND parent_id IS NOT NULL AND status='ACTIVE' AND version=#{version}")
    int disable(long tenantId, long id, int version);

    @Select("SELECT COUNT(*) FROM iam_user_organization uo JOIN iam_organization_closure c ON c.tenant_id=uo.tenant_id AND c.ancestor_id=uo.organization_id WHERE uo.tenant_id=#{tenantId} AND uo.user_id=#{userId} AND c.descendant_id=#{organizationId}")
    int canSwitch(long tenantId, long userId, long organizationId);

    @Insert("INSERT INTO iam_user_context(tenant_id,user_id,current_organization_id) VALUES(#{tenantId},#{userId},#{organizationId}) ON DUPLICATE KEY UPDATE current_organization_id=VALUES(current_organization_id)")
    int switchContext(long tenantId, long userId, long organizationId);

    @Select("SELECT o.id,o.parent_id,o.organization_code,o.organization_name,o.organization_type,o.status,o.sort_order,o.version FROM iam_user_context uc JOIN iam_organization o ON o.tenant_id=uc.tenant_id AND o.id=uc.current_organization_id WHERE uc.tenant_id=#{tenantId} AND uc.user_id=#{userId}")
    OrganizationModels.OrganizationRow current(long tenantId, long userId);
}

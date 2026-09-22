package io.github.turbopro.ism.platform.tenant;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TenantMapper {
    @Select("SELECT id,tenant_code,tenant_name,status,initialization_status,timezone,locale,package_version_id,version,initialized_at FROM plt_tenant ORDER BY created_at,id")
    List<TenantModels.TenantRow> list();

    @Select("SELECT id,tenant_code,tenant_name,status,initialization_status,timezone,locale,package_version_id,version,initialized_at FROM plt_tenant WHERE id=#{id}")
    TenantModels.TenantRow find(long id);

    @Select("SELECT COUNT(*) FROM plt_package_version WHERE id=#{id} AND status='PUBLISHED'")
    int publishedPackageExists(long id);

    @Insert("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status,timezone,locale,package_version_id) VALUES(#{id},#{code},#{name},'PROVISIONING','PENDING',#{timezone},#{locale},#{packageVersionId})")
    int insert(long id, String code, String name, String timezone, String locale, long packageVersionId);

    @Insert("INSERT INTO plt_subscription(id,tenant_id,package_version_id,status,effective_from,exception_json) VALUES(#{id},#{tenantId},#{packageVersionId},'ACTIVE',#{now},JSON_OBJECT())")
    int insertSubscription(long id, long tenantId, long packageVersionId, LocalDateTime now);

    @Update("UPDATE plt_tenant SET tenant_name=#{name},timezone=#{timezone},locale=#{locale},version=version+1 WHERE id=#{id} AND status<>'CANCELLED' AND version=#{version}")
    int update(long id, String name, String timezone, String locale, int version);

    @Insert("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status,timezone,locale) VALUES(#{id},#{code},#{name},'ACTIVE',#{timezone},#{locale})")
    int insertIamTenant(long id, String code, String name, String timezone, String locale);

    @Insert("INSERT INTO iam_organization(id,tenant_id,parent_id,organization_code,organization_name,organization_type,status) VALUES(#{id},#{tenantId},NULL,'HEADQUARTERS',#{name},'HEADQUARTERS','ACTIVE')")
    int insertHeadquarters(long id, long tenantId, String name);

    @Insert("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status,force_password_change) VALUES(#{id},#{tenantId},#{username},#{displayName},#{passwordHash},'ACTIVE',1)")
    int insertAdmin(long id, long tenantId, String username, String displayName, String passwordHash);

    @Insert("INSERT INTO iam_organization_closure(tenant_id,ancestor_id,descendant_id,depth) VALUES(#{tenantId},#{organizationId},#{organizationId},0)")
    int insertOrganizationClosure(long tenantId, long organizationId);

    @Insert("INSERT INTO iam_user_organization(tenant_id,user_id,organization_id,is_primary) VALUES(#{tenantId},#{userId},#{organizationId},1)")
    int insertAdminOrganization(long tenantId, long userId, long organizationId);

    @Insert("INSERT INTO iam_user_context(tenant_id,user_id,current_organization_id) VALUES(#{tenantId},#{userId},#{organizationId})")
    int insertAdminContext(long tenantId, long userId, long organizationId);

    @Update("UPDATE plt_tenant SET status='ACTIVE',initialization_status='READY',initialized_at=#{now},version=version+1 WHERE id=#{id} AND status='PROVISIONING' AND initialization_status='PENDING'")
    int markReady(long id, LocalDateTime now);

    @Update("UPDATE plt_tenant SET status=#{target},version=version+1 WHERE id=#{id} AND status=#{expected}")
    int transition(long id, String expected, String target);

    @Update("UPDATE iam_tenant SET status=#{status} WHERE id=#{id}")
    int updateIamStatus(long id, String status);

    @Update("UPDATE iam_user SET status=#{status},token_version=token_version+1 WHERE tenant_id=#{tenantId} AND deleted=0")
    int updateUserStatus(long tenantId, String status);
}

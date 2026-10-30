package io.github.turbopro.ism.platform.tenant;

import io.github.turbopro.ism.platform.packageplan.EffectiveSubscriptionSql;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TenantMapper {
    @Select("SELECT operation_mode FROM plt_tenant WHERE id=#{tenantId}")
    String siteOperationMode(long tenantId);
    record OperationState(String status,String initializationStatus,String operationMode,Long defaultSiteId,int version){}
    @Select("SELECT status,initialization_status,operation_mode,default_site_id,version FROM plt_tenant WHERE id=#{id} FOR UPDATE")
    @org.apache.ibatis.annotations.Options(useCache=false,flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    OperationState lockOperationState(long id);
    @Update("UPDATE plt_tenant SET operation_mode='MULTI_SITE',version=version+1 WHERE id=#{id} AND operation_mode='SINGLE_SITE' AND status='ACTIVE' AND initialization_status='READY' AND version=#{version}")
    int upgradeMode(long id,int version);

    record PortalTenantState(String status,String initializationStatus) {}
    @Select("SELECT status,initialization_status FROM plt_tenant WHERE id=#{tenantId} FOR UPDATE")
    PortalTenantState lockForPortalRegistration(long tenantId);
    @Select("SELECT t.id,t.tenant_code,t.tenant_name,t.status,t.initialization_status,t.timezone,t.locale," + EffectiveSubscriptionSql.VERSION + " AS package_version_id,t.version,t.initialized_at,t.operation_mode,t.site_label,t.default_site_id FROM plt_tenant t ORDER BY t.created_at,t.id")
    List<TenantModels.TenantRow> list();

    @Select("SELECT t.id,t.tenant_code,t.tenant_name,t.status,t.initialization_status,t.timezone,t.locale," + EffectiveSubscriptionSql.VERSION + " AS package_version_id,t.version,t.initialized_at,t.operation_mode,t.site_label,t.default_site_id FROM plt_tenant t WHERE t.id=#{id}")
    TenantModels.TenantRow find(long id);

    @Select("SELECT v.id FROM plt_package_version v JOIN plt_package p ON p.id=v.package_id WHERE v.id=#{id} AND v.status='PUBLISHED' AND p.status='ACTIVE' FOR UPDATE")
    Long lockAvailablePackageVersion(long id);
    String PACKAGE_OPTIONS_FILTER=" FROM plt_package_version v JOIN plt_package p ON p.id=v.package_id WHERE v.status='PUBLISHED' AND p.status='ACTIVE' <if test='keyword != null'> AND (p.package_code LIKE #{keyword} ESCAPE '=' OR p.package_name LIKE #{keyword} ESCAPE '=' OR v.version_name LIKE #{keyword} ESCAPE '=')</if>";
    @Select("<script>SELECT COUNT(*)"+PACKAGE_OPTIONS_FILTER+"</script>")
    long countPackageOptions(String keyword);
    @Select("<script>SELECT v.id,p.package_code,p.package_name,v.version_no,v.version_name"+PACKAGE_OPTIONS_FILTER+" ORDER BY p.package_code,v.version_no DESC,v.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<TenantModels.PackageOption> packageOptions(String keyword,int offset,int size);

    @Insert("INSERT INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status,timezone,locale,package_version_id) VALUES(#{id},#{code},#{name},'PROVISIONING','PENDING',#{timezone},#{locale},#{packageVersionId})")
    int insert(long id, String code, String name, String timezone, String locale, long packageVersionId);

    @Insert("INSERT INTO plt_subscription(id,tenant_id,package_version_id,status,effective_from,exception_json) VALUES(#{id},#{tenantId},#{packageVersionId},'ACTIVE',#{now},JSON_OBJECT())")
    int insertSubscription(long id, long tenantId, long packageVersionId, LocalDateTime now);

    @Update("UPDATE plt_tenant SET tenant_name=#{name},timezone=#{timezone},locale=#{locale},version=version+1 WHERE id=#{id} AND status<>'CANCELLED' AND version=#{version}")
    int update(long id, String name, String timezone, String locale, int version);

    @Insert("INSERT INTO iam_tenant(id,tenant_code,tenant_name,status,timezone,locale) VALUES(#{id},#{code},#{name},'ACTIVE',#{timezone},#{locale})")
    int insertIamTenant(long id, String code, String name, String timezone, String locale);

    @Insert("INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status,force_password_change) VALUES(#{id},#{tenantId},#{username},#{displayName},#{passwordHash},'ACTIVE',1)")
    int insertAdmin(long id, long tenantId, String username, String displayName, String passwordHash);

    @Insert("INSERT INTO iam_user_organization(tenant_id,user_id,organization_id,is_primary) VALUES(#{tenantId},#{userId},#{organizationId},1)")
    int insertAdminOrganization(long tenantId, long userId, long organizationId);

    @Insert("INSERT INTO iam_user_context(tenant_id,user_id,current_organization_id) VALUES(#{tenantId},#{userId},#{organizationId})")
    int insertAdminContext(long tenantId, long userId, long organizationId);

    @Insert("INSERT INTO iam_role(id,tenant_id,role_code,role_name,built_in,status) VALUES(#{id},#{tenantId},'TENANT_ADMIN','租户管理员',1,'ACTIVE')")
    int insertTenantAdminRole(long tenantId, long id);

    @Insert("INSERT INTO iam_user_role(tenant_id,user_id,role_id) VALUES(#{tenantId},#{userId},#{roleId})")
    int insertAdminRole(long tenantId, long userId, long roleId);

    @Insert("INSERT INTO iam_role_permission(tenant_id,role_id,permission_id) SELECT #{tenantId},#{roleId},id FROM sys_permission WHERE status='ACTIVE'")
    int grantAdminPermissions(long tenantId, long roleId);

    @Insert("INSERT INTO iam_role_menu(tenant_id,role_id,menu_id) SELECT #{tenantId},#{roleId},id FROM sys_menu WHERE status='ACTIVE'")
    int grantAdminMenus(long tenantId, long roleId);

    @Insert("INSERT INTO iam_role_data_scope(tenant_id,role_id,resource_code,scope_type) VALUES(#{tenantId},#{roleId},'iam:organization','TENANT_ALL'),(#{tenantId},#{roleId},'iam:user','TENANT_ALL'),(#{tenantId},#{roleId},'iam:site','TENANT_ALL'),(#{tenantId},#{roleId},'resource:file','TENANT_ALL'),(#{tenantId},#{roleId},'print:template','TENANT_ALL'),(#{tenantId},#{roleId},'supplier:master','TENANT_ALL'),(#{tenantId},#{roleId},'supplier:admission','TENANT_ALL'),(#{tenantId},#{roleId},'supplier:qualification','TENANT_ALL'),(#{tenantId},#{roleId},'contract','TENANT_ALL'),(#{tenantId},#{roleId},'project','TENANT_ALL'),(#{tenantId},#{roleId},'resource:person','TENANT_ALL'),(#{tenantId},#{roleId},'resource:asset','TENANT_ALL'),(#{tenantId},#{roleId},'safety:issue','TENANT_ALL'),(#{tenantId},#{roleId},'safety:credential','TENANT_ALL'),(#{tenantId},#{roleId},'safety:attendance','TENANT_ALL'),(#{tenantId},#{roleId},'quality:ncr','TENANT_ALL'),(#{tenantId},#{roleId},'performance:evaluation','TENANT_ALL')")
    int grantAdminDataScopes(long tenantId, long roleId);

    @Update("UPDATE plt_tenant SET operation_mode=#{mode},site_label=#{label},default_site_id=#{siteId} WHERE id=#{id} AND initialization_status='PENDING'")
    int operationStructure(long id,String mode,String label,Long siteId);

    @Update("UPDATE plt_tenant SET status='ACTIVE',initialization_status='READY',initialized_at=#{now},version=version+1 WHERE id=#{id} AND status='PROVISIONING' AND initialization_status='PENDING'")
    int markReady(long id, LocalDateTime now);

    @Update("UPDATE plt_tenant SET status=#{target},version=version+1 WHERE id=#{id} AND status=#{expected}")
    int transition(long id, String expected, String target);

    @Update("UPDATE iam_tenant SET status=#{status} WHERE id=#{id}")
    int updateIamStatus(long id, String status);

    @Update("UPDATE iam_user SET status=#{status},token_version=token_version+1 WHERE tenant_id=#{tenantId} AND deleted=0")
    int updateUserStatus(long tenantId, String status);
}

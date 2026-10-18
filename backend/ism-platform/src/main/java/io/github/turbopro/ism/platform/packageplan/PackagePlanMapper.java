package io.github.turbopro.ism.platform.packageplan;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PackagePlanMapper {
    @Select("""
        SELECT m.module_code FROM plt_tenant t
        JOIN plt_package_version v ON v.id=(
        """ + EffectiveSubscriptionSql.VERSION + """
        ) AND v.status='PUBLISHED'
        JOIN plt_package_module pm ON pm.package_version_id=v.id AND pm.enabled=1
        JOIN plt_module m ON m.id=pm.module_id AND m.status='ACTIVE'
        WHERE t.id=#{tenantId}
        """)
    List<String> enabledTenantModules(long tenantId);
    @Select("SELECT id,package_code,package_name,status,version FROM plt_package ORDER BY created_at,id")
    List<PackagePlanModels.PackageRow> listPackages();
    @Select("SELECT id,package_code,package_name,status,version FROM plt_package WHERE id=#{id}")
    PackagePlanModels.PackageRow findPackage(long id);
    @Insert("INSERT INTO plt_package(id,package_code,package_name,status) VALUES(#{id},#{code},#{name},'DRAFT')")
    int insertPackage(long id, String code, String name);
    @Update("UPDATE plt_package SET package_name=#{name},version=version+1 WHERE id=#{id} AND status='DRAFT' AND version=#{version}")
    int updatePackage(long id, String name, int version);
    @Select("SELECT id,package_id,version_no,version_name,effective_from,status,version FROM plt_package_version WHERE package_id=#{packageId} ORDER BY version_no")
    List<PackagePlanModels.VersionRow> listVersions(long packageId);
    @Select("SELECT id,package_id,version_no,version_name,effective_from,status,version FROM plt_package_version WHERE id=#{id}")
    PackagePlanModels.VersionRow findVersion(long id);
    @Insert("INSERT INTO plt_package_version(id,package_id,version_no,version_name,effective_from,status) VALUES(#{id},#{packageId},#{versionNo},#{name},#{effectiveFrom},'DRAFT')")
    int insertVersion(long id, long packageId, int versionNo, String name, LocalDateTime effectiveFrom);
    @Insert("INSERT INTO plt_package_module(package_version_id,module_id,enabled,quota_json) SELECT #{versionId},id,#{enabled},#{quotaJson} FROM plt_module WHERE module_code=#{moduleCode} AND status='ACTIVE'")
    int insertModule(long versionId, String moduleCode, boolean enabled, String quotaJson);
    @Select("""
        SELECT pm.package_version_id,pm.module_id,m.module_code,m.module_name,pm.enabled,pm.quota_json
        FROM plt_package_module pm JOIN plt_module m ON m.id=pm.module_id
        WHERE pm.package_version_id=#{versionId} ORDER BY m.sort_order
        """)
    List<PackagePlanModels.ModuleRow> listModules(long versionId);
    @Update("UPDATE plt_package_version SET status='PUBLISHED',published_at=#{now},published_by=#{actorId},version=version+1 WHERE id=#{id} AND status='DRAFT' AND version=#{version}")
    int publish(long id, int version, long actorId, LocalDateTime now);
    @Select("SELECT " + EffectiveSubscriptionSql.VERSION + " FROM plt_tenant t WHERE t.id=#{tenantId}")
    Long findTenantPackageVersion(long tenantId);
    @Select("SELECT id FROM plt_tenant WHERE id=#{tenantId} FOR UPDATE")
    Long lockTenant(long tenantId);
    @Select("SELECT UTC_TIMESTAMP(3)")
    LocalDateTime utcNow();
    @Insert("INSERT INTO plt_subscription(id,tenant_id,package_version_id,status,effective_from,exception_json) SELECT #{id},t.id,t.package_version_id,'ACTIVE',#{now},JSON_OBJECT() FROM plt_tenant t JOIN plt_package_version v ON v.id=t.package_version_id AND v.status='PUBLISHED' WHERE t.id=#{tenantId} AND NOT EXISTS (SELECT 1 FROM plt_subscription s WHERE s.tenant_id=t.id)")
    int materializeLegacySubscription(long tenantId,long id,LocalDateTime now);
    @Update("UPDATE plt_subscription SET status='TERMINATED' WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND effective_from > #{now}")
    int cancelFutureSubscriptions(long tenantId, LocalDateTime now);
    @Insert("INSERT INTO plt_subscription(id,tenant_id,package_version_id,status,effective_from,effective_to,exception_json,assignment_sequence) SELECT #{id},t.id,#{versionId},'ACTIVE',#{from},#{to},#{exceptions},t.version+1 FROM plt_tenant t WHERE t.id=#{tenantId}")
    int insertSubscription(long id, long tenantId, long versionId, LocalDateTime from, LocalDateTime to, String exceptions);
    @Update("UPDATE plt_tenant SET package_version_id=#{versionId},version=version+1 WHERE id=#{tenantId}")
    int updateTenantPackage(long tenantId, long versionId);
    @Select("SELECT COALESCE(used_value,0) FROM plt_quota_usage WHERE tenant_id=#{tenantId} AND quota_code=#{quotaCode} AND period_key=#{periodKey}")
    Long quotaUsed(long tenantId, String quotaCode, String periodKey);
    @Select("SELECT quota_code FROM plt_quota_usage WHERE tenant_id=#{tenantId} AND period_key='CURRENT' AND used_value>0")
    List<String> usedQuotaCodes(long tenantId);
    @Select("SELECT id,version FROM plt_tenant WHERE id=#{tenantId}")
    PackagePlanModels.SubscriptionTenantRow subscriptionTenant(long tenantId);
    @Select("SELECT id,version FROM plt_tenant WHERE id=#{tenantId} FOR UPDATE")
    PackagePlanModels.SubscriptionTenantRow lockSubscriptionTenant(long tenantId);
    @Select("SELECT COUNT(*) FROM plt_subscription WHERE tenant_id=#{tenantId}")
    long subscriptionCount(long tenantId);
    @Select("SELECT id FROM plt_subscription WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND effective_from <= #{asOf} ORDER BY effective_from DESC,assignment_sequence DESC,created_at DESC,id DESC LIMIT 1")
    Long latestStartedSubscription(long tenantId,LocalDateTime asOf);
    @Select("""
        SELECT s.id,s.package_version_id,p.package_name,v.version_name,s.status,v.status AS version_status,
          s.effective_from,s.effective_to,s.cancelled_at,s.cancelled_by,s.cancel_reason
        FROM plt_subscription s JOIN plt_package_version v ON v.id=s.package_version_id
        JOIN plt_package p ON p.id=v.package_id WHERE s.tenant_id=#{tenantId}
        ORDER BY s.assignment_sequence DESC,s.created_at DESC,s.id DESC LIMIT #{size} OFFSET #{offset}
        """)
    List<PackagePlanModels.SubscriptionRow> subscriptions(long tenantId,int offset,int size);
    @Select("""
        SELECT s.id,s.package_version_id,p.package_name,v.version_name,s.status,v.status AS version_status,
          s.effective_from,s.effective_to,s.cancelled_at,s.cancelled_by,s.cancel_reason
        FROM plt_subscription s JOIN plt_package_version v ON v.id=s.package_version_id
        JOIN plt_package p ON p.id=v.package_id WHERE s.tenant_id=#{tenantId} AND s.id=#{id}
        """)
    PackagePlanModels.SubscriptionRow subscription(long tenantId,long id);
    @Update("UPDATE plt_subscription SET status='CANCELLED',cancelled_at=#{now},cancelled_by=#{actorId},cancel_reason=#{reason} WHERE tenant_id=#{tenantId} AND id=#{id} AND status='ACTIVE' AND effective_from > UTC_TIMESTAMP(3)")
    int cancelSubscription(long tenantId,long id,LocalDateTime now,long actorId,String reason);
    @Update("UPDATE plt_tenant SET version=version+1 WHERE id=#{tenantId} AND version=#{version}")
    int advanceSubscriptionTenantVersion(long tenantId,int version);
}

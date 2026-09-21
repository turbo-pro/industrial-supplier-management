package io.github.turbopro.ism.platform.packageplan;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PackagePlanMapper {
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
    @Select("SELECT package_version_id FROM plt_tenant WHERE id=#{tenantId}")
    Long findTenantPackageVersion(long tenantId);
    @Update("UPDATE plt_subscription SET status='TERMINATED',effective_to=#{now} WHERE tenant_id=#{tenantId} AND status='ACTIVE'")
    int terminateSubscription(long tenantId, LocalDateTime now);
    @Insert("INSERT INTO plt_subscription(id,tenant_id,package_version_id,status,effective_from,effective_to,exception_json) VALUES(#{id},#{tenantId},#{versionId},'ACTIVE',#{from},#{to},#{exceptions})")
    int insertSubscription(long id, long tenantId, long versionId, LocalDateTime from, LocalDateTime to, String exceptions);
    @Update("UPDATE plt_tenant SET package_version_id=#{versionId},version=version+1 WHERE id=#{tenantId}")
    int updateTenantPackage(long tenantId, long versionId);
    @Select("SELECT COALESCE(used_value,0) FROM plt_quota_usage WHERE tenant_id=#{tenantId} AND quota_code=#{quotaCode} AND period_key=#{periodKey}")
    Long quotaUsed(long tenantId, String quotaCode, String periodKey);
}

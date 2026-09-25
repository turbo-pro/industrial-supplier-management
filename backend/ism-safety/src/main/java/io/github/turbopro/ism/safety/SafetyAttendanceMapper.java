package io.github.turbopro.ism.safety;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Set;

@Mapper
public interface SafetyAttendanceMapper extends TenantScopedMapper {
    String SCOPE = "<choose><when test=\"scopeType == 'TENANT_ALL'\"></when>"
            + "<when test=\"scopeType == 'ORGANIZATION_SET'\"> AND a.organization_id IN <foreach collection='organizationIds' item='o' open='(' separator=',' close=')'>#{o}</foreach></when>"
            + "<when test=\"scopeType == 'PROJECT_SET'\"> AND a.project_id IN <foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach></when>"
            + "<when test=\"scopeType == 'CREATED'\"> AND a.created_by=#{actorId}</when>"
            + "<otherwise> AND 1=0</otherwise></choose>";
    String FIELDS = "a.id,a.organization_id,a.project_id,a.supplier_id,a.person_id,p.person_code,p.person_name,s.supplier_name,j.project_name,a.site_name,a.check_in_at,a.check_out_at,a.check_in_by,a.check_out_by,a.check_out_note,a.created_by,a.version";
    String JOINS = " FROM saf_site_attendance a JOIN res_supplier_person p ON p.id=a.person_id AND p.tenant_id=a.tenant_id JOIN sup_supplier s ON s.id=a.supplier_id AND s.tenant_id=a.tenant_id JOIN prj_project j ON j.id=a.project_id AND j.tenant_id=a.tenant_id";

    @Select("SELECT COUNT(*) FROM prj_project WHERE tenant_id=#{tenantId} AND id=#{projectId} AND supplier_id=#{supplierId} AND status='ACTIVE'")
    int activeProject(long tenantId, long projectId, long supplierId);

    @Select("<script>SELECT COUNT(*)" + JOINS + " WHERE a.tenant_id=#{tenantId}" + SCOPE + "<if test='personId != null'> AND a.person_id=#{personId}</if><if test='openOnly'> AND a.check_out_at IS NULL</if></script>")
    long count(long tenantId, Long personId, boolean openOnly, String scopeType, Set<Long> organizationIds, Set<Long> projectIds, long actorId);

    @Select("<script>SELECT " + FIELDS + JOINS + " WHERE a.tenant_id=#{tenantId}" + SCOPE + "<if test='personId != null'> AND a.person_id=#{personId}</if><if test='openOnly'> AND a.check_out_at IS NULL</if> ORDER BY a.check_in_at DESC,a.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<SafetyAttendanceModels.Row> list(long tenantId, Long personId, boolean openOnly, String scopeType,
                                           Set<Long> organizationIds, Set<Long> projectIds, long actorId, int offset, int size);

    @Select("SELECT " + FIELDS + JOINS + " WHERE a.tenant_id=#{tenantId} AND a.id=#{id}")
    SafetyAttendanceModels.Row get(long tenantId, long id);

    @Insert("INSERT INTO saf_site_attendance(id,tenant_id,organization_id,project_id,supplier_id,person_id,site_name,check_in_at,check_in_by,created_by) VALUES(#{id},#{tenantId},#{org},#{projectId},#{supplierId},#{personId},#{siteName},CURRENT_TIMESTAMP(3),#{actorId},#{actorId})")
    int checkIn(long id, long tenantId, long org, long projectId, long supplierId, long personId, String siteName, long actorId);

    @Update("UPDATE saf_site_attendance SET check_out_at=CURRENT_TIMESTAMP(3),check_out_by=#{actorId},check_out_note=#{note},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND check_out_at IS NULL AND version=#{version}")
    int checkOut(long tenantId, long id, long actorId, String note, int version);
}

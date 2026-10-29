package io.github.turbopro.ism.iam.site;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface SitePreferenceMapper extends TenantScopedMapper {
    String ELIGIBLE="""
      FROM iam_organization s WHERE s.tenant_id=#{tenantId} AND s.organization_type='SITE' AND s.status='ACTIVE'
      AND NOT EXISTS(SELECT 1 FROM iam_organization_closure c JOIN iam_organization a ON a.tenant_id=c.tenant_id AND a.id=c.ancestor_id WHERE c.tenant_id=#{tenantId} AND c.descendant_id=s.id AND a.status&lt;&gt;'ACTIVE')
      AND EXISTS(SELECT 1 FROM iam_user_organization u JOIN iam_organization m ON m.tenant_id=u.tenant_id AND m.id=u.organization_id AND m.status='ACTIVE'
        WHERE u.tenant_id=#{tenantId} AND u.user_id=#{userId}
        AND NOT EXISTS(SELECT 1 FROM iam_organization_closure mc JOIN iam_organization ma ON ma.tenant_id=mc.tenant_id AND ma.id=mc.ancestor_id WHERE mc.tenant_id=#{tenantId} AND mc.descendant_id=m.id AND ma.status&lt;&gt;'ACTIVE')
        AND (EXISTS(SELECT 1 FROM iam_organization_closure d WHERE d.tenant_id=#{tenantId} AND d.ancestor_id=m.id AND d.descendant_id=s.id)
          OR EXISTS(SELECT 1 FROM iam_organization_closure p WHERE p.tenant_id=#{tenantId} AND p.ancestor_id=s.id AND p.descendant_id=m.id)))
      <choose><when test="scopeType == 'TENANT_ALL'"></when>
       <when test="scopeType == 'ORGANIZATION_SET' and organizations != null and !organizations.isEmpty()">
        AND s.id IN <foreach collection='organizations' item='org' open='(' separator=',' close=')'>#{org}</foreach>
       </when><otherwise>AND 1=0</otherwise></choose>
      """;
    @Select("<script>SELECT CAST(s.id AS CHAR) AS id,s.organization_code AS code,s.organization_name AS name "+ELIGIBLE+" AND (s.organization_code LIKE #{query} ESCAPE '!' OR s.organization_name LIKE #{query} ESCAPE '!') ORDER BY s.organization_code,s.id LIMIT #{size} OFFSET #{offset}</script>")
    List<SitePreferenceModels.Option> options(long tenantId,long userId,String scopeType,Set<Long> organizations,String query,int size,long offset);
    @Select("<script>SELECT COUNT(*) "+ELIGIBLE+" AND (s.organization_code LIKE #{query} ESCAPE '!' OR s.organization_name LIKE #{query} ESCAPE '!')</script>")
    long count(long tenantId,long userId,String scopeType,Set<Long> organizations,String query);
    @Select("<script>SELECT CAST(s.id AS CHAR) AS id,s.organization_code AS code,s.organization_name AS name "+ELIGIBLE+" AND s.id=#{siteId}</script>")
    SitePreferenceModels.Option eligible(long tenantId,long userId,String scopeType,Set<Long> organizations,long siteId);
    @Select("SELECT preferred_site_id,version FROM iam_user_site_preference WHERE tenant_id=#{tenantId} AND user_id=#{userId}")
    SitePreferenceModels.Row preference(long tenantId,long userId);
    @Select("SELECT preferred_site_id,version FROM iam_user_site_preference WHERE tenant_id=#{tenantId} AND user_id=#{userId} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    SitePreferenceModels.Row preferenceForUpdate(long tenantId,long userId);
    @Select("SELECT id FROM iam_user WHERE tenant_id=#{tenantId} AND id=#{userId} AND status='ACTIVE' AND deleted=0 FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Long lockUser(long tenantId,long userId);
    @Insert("INSERT INTO iam_user_site_preference(tenant_id,user_id,preferred_site_id,version) VALUES(#{tenantId},#{userId},#{siteId},1)")
    void insert(long tenantId,long userId,Long siteId);
    @Update("UPDATE iam_user_site_preference SET preferred_site_id=#{siteId},version=version+1 WHERE tenant_id=#{tenantId} AND user_id=#{userId} AND version=#{version}")
    int update(long tenantId,long userId,Long siteId,int version);
}

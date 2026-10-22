package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PortalSessionRetentionMapper extends TenantScopedMapper {
    @Select("SELECT id FROM iam_portal_session WHERE tenant_id=#{tenantId} AND expires_at<#{cutoff} AND (revoked_at IS NULL OR revoked_at<#{cutoff}) ORDER BY expires_at,id LIMIT #{size} FOR UPDATE SKIP LOCKED")
    List<Long> lockBatch(long tenantId,LocalDateTime cutoff,int size);
    // Pin PK lookup: a tenant/expiry range delete may lock rows skipped by the preceding SELECT.
    @Delete({"<script>DELETE s FROM iam_portal_session s FORCE INDEX(PRIMARY) WHERE s.tenant_id=#{tenantId} AND s.expires_at&lt;#{cutoff} AND (s.revoked_at IS NULL OR s.revoked_at&lt;#{cutoff}) AND s.id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"})
    int deleteBatch(long tenantId,LocalDateTime cutoff,List<Long> ids);
}

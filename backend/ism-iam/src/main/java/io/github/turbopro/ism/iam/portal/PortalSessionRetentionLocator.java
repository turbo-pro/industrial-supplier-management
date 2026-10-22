package io.github.turbopro.ism.iam.portal;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** Internal maintenance discovery only; no credentials or session IDs cross tenant boundaries here. */
@Mapper
public interface PortalSessionRetentionLocator {
    @Select("SELECT UTC_TIMESTAMP(3)-INTERVAL #{days} DAY")
    LocalDateTime cutoff(int days);
    @Select("SELECT DISTINCT tenant_id FROM iam_portal_session WHERE tenant_id>#{afterTenant} AND expires_at<#{cutoff} AND (revoked_at IS NULL OR revoked_at<#{cutoff}) ORDER BY tenant_id LIMIT 50")
    List<Long> tenants(long afterTenant,LocalDateTime cutoff);
}

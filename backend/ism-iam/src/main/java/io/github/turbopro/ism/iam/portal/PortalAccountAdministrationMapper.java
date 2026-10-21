package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.infrastructure.portal.PortalAccountAdministration.Account;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PortalAccountAdministrationMapper extends TenantScopedMapper {
    String FIELDS = "CAST(a.id AS CHAR) id,a.username,a.display_name,a.status,a.version,"+
            "(SELECT COUNT(*) FROM iam_portal_session s WHERE s.tenant_id=a.tenant_id AND s.account_id=a.id AND s.token_version=a.token_version AND s.revoked_at IS NULL AND s.expires_at>UTC_TIMESTAMP(3)) live_sessions,a.last_login_at";
    @Select("SELECT "+FIELDS+" FROM iam_portal_account a WHERE a.tenant_id=#{tenantId} AND a.supplier_id=#{supplierId} ORDER BY a.id DESC LIMIT #{size} OFFSET #{offset}")
    List<Account> list(long tenantId,long supplierId,int size,long offset);
    @Select("SELECT COUNT(*) FROM iam_portal_account WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}")
    long count(long tenantId,long supplierId);
    @Select("SELECT "+FIELDS+" FROM iam_portal_account a WHERE a.tenant_id=#{tenantId} AND a.supplier_id=#{supplierId} AND a.id=#{id} FOR UPDATE")
    Account lock(long tenantId,long supplierId,long id);
    @Update("UPDATE iam_portal_account SET status=#{status},version=version+1,token_version=token_version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} AND version=#{version}")
    int change(long tenantId,long supplierId,long id,int version,String status);
    @Update("UPDATE iam_portal_session SET revoked_at=UTC_TIMESTAMP(3) WHERE tenant_id=#{tenantId} AND account_id=#{id} AND revoked_at IS NULL")
    int revokeAll(long tenantId,long id);
    @Select("SELECT "+FIELDS+" FROM iam_portal_account a WHERE a.tenant_id=#{tenantId} AND a.supplier_id=#{supplierId} AND a.id>#{afterId} ORDER BY a.id LIMIT 100 FOR UPDATE")
    List<Account> exitBatch(long tenantId,long supplierId,long afterId);
}

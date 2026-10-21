package io.github.turbopro.ism.iam.portal;
import org.apache.ibatis.annotations.*;

/** Exact anonymous bootstrap lookups in IAM-owned tables; scoped reads/writes follow immediately. */
@Mapper
public interface PortalIdentityLocator {
    record Location(long tenantId,long accountId) {}
    @Select("SELECT a.tenant_id,a.id account_id FROM iam_portal_account a JOIN iam_tenant t ON t.id=a.tenant_id WHERE t.tenant_code=#{tenantCode} AND a.username=#{username}")
    Location account(String tenantCode,String username);
    @Select("SELECT tenant_id,account_id FROM iam_portal_session WHERE token_hash=#{hash}")
    Location session(String hash);
}

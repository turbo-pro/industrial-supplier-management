package io.github.turbopro.ism.iam.portal;
import org.apache.ibatis.annotations.*;
@Mapper
public interface PortalPasswordRecoveryLocator {
    record Location(long tenantId,long accountId,long supplierId) {}
    /** Exact anonymous capability lookup only; all subsequent operations are scoped. */
    @Select("SELECT r.tenant_id,r.account_id,a.supplier_id FROM iam_portal_password_recovery r JOIN iam_portal_account a ON a.tenant_id=r.tenant_id AND a.id=r.account_id WHERE r.token_hash=#{hash}")
    Location locate(String hash);
}

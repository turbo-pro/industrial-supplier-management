package io.github.turbopro.ism.supplier;

import org.apache.ibatis.annotations.*;

/** Deliberate global bootstrap lookup: exact 256-bit capability hash only, no PII or client tenant ID. */
@Mapper
public interface PortalInvitationTokenLocator {
    record Location(long id,long tenantId,long supplierId) {}
    @Select("SELECT id,tenant_id,supplier_id FROM sup_portal_invitation WHERE token_hash=#{hash}")
    Location locate(String hash);
}

package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PortalAccountMapper extends TenantScopedMapper {
    @Insert("INSERT INTO iam_portal_account(id,tenant_id,supplier_id,username,display_name,email,password_hash) VALUES(#{id},#{tenantId},#{supplierId},#{username},#{displayName},#{email},#{hash})")
    int insert(long id,long tenantId,long supplierId,String username,String displayName,String email,String hash);
}

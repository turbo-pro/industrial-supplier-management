package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PortalSupplierProfileMapper extends TenantScopedMapper {
    @Select("""
        SELECT CAST(s.id AS CHAR) id,s.supplier_code code,s.supplier_name name,s.short_name,
            s.unified_social_credit_code,s.supplier_type type,s.industry,s.country_code,s.province,s.city,s.address,
            s.established_date,s.website,s.status,s.version
        FROM sup_supplier s WHERE s.tenant_id=#{tenantId} AND s.id=#{supplierId} AND s.deleted=0
            AND s.status IN ('DRAFT','ACTIVE')
            AND NOT EXISTS(SELECT 1 FROM sup_exit_application e WHERE e.tenant_id=s.tenant_id
                AND e.supplier_id=s.id AND e.status='SUBMITTED')
        """)
    PortalSupplierProfileModels.Profile profile(long tenantId,long supplierId);

    @Select("""
        SELECT c.category_code code,c.category_name name FROM sup_purchase_category c
        JOIN sup_supplier_purchase_category a ON a.tenant_id=c.tenant_id AND a.category_id=c.id
        WHERE a.tenant_id=#{tenantId} AND a.supplier_id=#{supplierId} AND c.status='ACTIVE'
        ORDER BY c.category_code,c.id
        """)
    List<PortalSupplierProfileModels.Category> categories(long tenantId,long supplierId);
}

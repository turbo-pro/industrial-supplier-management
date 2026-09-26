package io.github.turbopro.ism.safety;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import io.github.turbopro.ism.supplier.SupplierExitCheck;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SafetyExitCheck implements SupplierExitCheck {
    private final ExitMapper mapper;
    public SafetyExitCheck(ExitMapper mapper) { this.mapper=mapper; }
    @Override public List<Blocker> blockers(long supplierId) {
        long tenant=TenantContext.require().tenantId();
        return List.of(new Blocker("OPEN_SAFETY","未关闭安全隐患",mapper.open(tenant,supplierId).size(),"/safety/issues"),
                new Blocker("OPEN_ATTENDANCE","未签退现场记录",mapper.attendance(tenant,supplierId).size(),"/safety/attendance"));
    }
    @Mapper public interface ExitMapper extends TenantScopedMapper {
        @Select("SELECT id FROM saf_site_attendance WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND check_out_at IS NULL FOR UPDATE") List<Long> attendance(long tenantId,long supplierId);
        @Select("SELECT id FROM saf_issue WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND status!='CLOSED' FOR UPDATE") List<Long> open(long tenantId,long supplierId);
    }
}

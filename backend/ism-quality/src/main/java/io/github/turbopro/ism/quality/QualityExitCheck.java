package io.github.turbopro.ism.quality;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import io.github.turbopro.ism.supplier.SupplierExitCheck;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class QualityExitCheck implements SupplierExitCheck, io.github.turbopro.ism.supplier.SupplierLiftCheck {
    private final ExitMapper mapper;
    public QualityExitCheck(ExitMapper mapper) { this.mapper=mapper; }
    @Override public List<Blocker> blockers(long supplierId) {
        return List.of(new Blocker("OPEN_QUALITY","未关闭质量不符合项",mapper.open(TenantContext.require().tenantId(),supplierId).size(),"/quality/nonconformances"));
    }
    @Mapper public interface ExitMapper extends TenantScopedMapper {
        @Select("SELECT id FROM qua_nonconformance WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND status!='CLOSED' FOR UPDATE") List<Long> open(long tenantId,long supplierId);
    }
}

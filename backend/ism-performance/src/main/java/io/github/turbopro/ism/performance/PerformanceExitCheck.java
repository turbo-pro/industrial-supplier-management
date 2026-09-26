package io.github.turbopro.ism.performance;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import io.github.turbopro.ism.supplier.SupplierExitCheck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PerformanceExitCheck implements SupplierExitCheck, io.github.turbopro.ism.supplier.SupplierLiftCheck {
    private final ExitMapper mapper;
    public PerformanceExitCheck(ExitMapper mapper) { this.mapper = mapper; }

    @Override public List<Blocker> blockers(long supplierId) {
        return List.of(new Blocker("OPEN_IMPROVEMENT", "未验收绩效改进计划",
                mapper.open(TenantContext.require().tenantId(), supplierId).size(), "/performance/evaluations"));
    }

    @Mapper public interface ExitMapper extends TenantScopedMapper {
        @Select("SELECT p.id FROM per_improvement_plan p JOIN per_supplier_evaluation e ON e.id=p.evaluation_id AND e.tenant_id=p.tenant_id WHERE p.tenant_id=#{tenantId} AND e.supplier_id=#{supplierId} AND p.status!='ACCEPTED' FOR UPDATE")
        List<Long> open(long tenantId, long supplierId);
    }
}

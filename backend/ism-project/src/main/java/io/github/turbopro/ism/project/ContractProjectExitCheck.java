package io.github.turbopro.ism.project;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.supplier.SupplierExitCheck;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ContractProjectExitCheck implements SupplierExitCheck {
    private final ProjectMapper mapper;
    public ContractProjectExitCheck(ProjectMapper mapper) { this.mapper = mapper; }
    @Override public List<Blocker> blockers(long supplierId) {
        long tenant = TenantContext.require().tenantId();
        return List.of(new Blocker("OPEN_CONTRACT", "未结束合同", mapper.openContracts(tenant, supplierId).size(), "/projects/contracts"),
                new Blocker("OPEN_PROJECT", "未结束项目", mapper.openProjects(tenant, supplierId).size(), "/projects/ledger"));
    }
}

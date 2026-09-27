package io.github.turbopro.ism.project;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.supplier.SupplierExitCheck;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ContractProjectExitCheck implements SupplierExitCheck {
    private final ProjectMapper mapper;
    public ContractProjectExitCheck(ProjectMapper mapper) { this.mapper = mapper; }
    @Override public List<Entity> entities(long supplierId) {
        long tenant=TenantContext.require().tenantId();var result=new java.util.ArrayList<Entity>();
        result.addAll(SupplierExitCheck.entities("OPEN_CONTRACT",mapper.openContracts(tenant,supplierId),"/projects/contracts"));
        result.addAll(SupplierExitCheck.entities("OPEN_PROJECT",mapper.openProjects(tenant,supplierId),"/projects/ledger"));
        return List.copyOf(result);
    }
    @Override public List<Blocker> blockers(long supplierId) {
        long tenant = TenantContext.require().tenantId();
        return List.of(new Blocker("OPEN_CONTRACT", "未结束合同", mapper.openContracts(tenant, supplierId).size(), "/projects/contracts"),
                new Blocker("OPEN_PROJECT", "未结束项目", mapper.openProjects(tenant, supplierId).size(), "/projects/ledger"));
    }
}

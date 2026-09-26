package io.github.turbopro.ism.resource.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import io.github.turbopro.ism.supplier.SupplierExitCheck;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ResourceExitCheck implements SupplierExitCheck {
    private final ExitMapper mapper;
    public ResourceExitCheck(ExitMapper mapper) { this.mapper=mapper; }
    @Override public List<Blocker> blockers(long supplierId) {
        return List.of(new Blocker("OPEN_PERSON","未离场人员",mapper.open(TenantContext.require().tenantId(),supplierId).size(),"/resources/persons"));
    }
    @Mapper public interface ExitMapper extends TenantScopedMapper {
        @Select("SELECT id FROM res_supplier_person WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND status!='EXITED' FOR UPDATE") List<Long> open(long tenantId,long supplierId);
    }
}

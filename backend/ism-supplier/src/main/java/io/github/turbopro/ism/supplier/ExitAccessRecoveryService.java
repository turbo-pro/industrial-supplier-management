package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExitAccessRecoveryService {
    private final ExitMapper mapper;
    private final SupplierService suppliers;

    public ExitAccessRecoveryService(ExitMapper mapper,SupplierService suppliers){
        this.mapper=mapper;this.suppliers=suppliers;
    }

    @Transactional(readOnly=true)
    public ExitAccessRecoveryModels.Inventory inventory(long supplierId,long applicationId){
        suppliers.get(supplierId);
        long tenantId=TenantContext.require().tenantId();
        var application=mapper.get(tenantId,supplierId,applicationId);
        var result=mapper.findResult(tenantId,applicationId);
        if(application==null||!"BUSINESS_CLOSED".equals(application.status())||result==null)
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        var tasks=mapper.accessRecoveryTasks(tenantId,supplierId,applicationId).stream()
            .map(row->new ExitAccessRecoveryModels.Task(Long.toString(row.id()),row.channel(),row.status(),row.createdAt()))
            .toList();
        return new ExitAccessRecoveryModels.Inventory(Long.toString(supplierId),Long.toString(applicationId),
            result.accessRecoveryStatus(),tasks);
    }
}

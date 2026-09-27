package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class ExitReminderFailureService {
    private final ExitReminderFailureMapper mapper;
    private final SupplierService suppliers;
    public ExitReminderFailureService(ExitReminderFailureMapper mapper,SupplierService suppliers){this.mapper=mapper;this.suppliers=suppliers;}

    @Transactional public void record(long supplierId,long applicationId,long entityId,String reasonCode){
        var identity=system();
        if(reasonCode==null||!reasonCode.matches("[A-Z0-9_]{1,64}"))throw new IllegalArgumentException("safe reason code required");
        if(mapper.record(identity.tenantId(),supplierId,applicationId,entityId,reasonCode,LocalDateTime.now(ZoneOffset.UTC))<1)
            throw new ApiException(CommonErrorCode.NOT_FOUND);
    }
    @Transactional public void delivered(long supplierId,long applicationId,long entityId){
        var identity=system();
        mapper.resolve(identity.tenantId(),supplierId,applicationId,entityId,LocalDateTime.now(ZoneOffset.UTC));
    }
    @Transactional(readOnly=true) public ExitReminderFailureModels.Page list(long supplierId,int page,int size){
        if(page<0||page>10000||size<1||size>100)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"分页参数无效");
        suppliers.get(supplierId);long tenantId=TenantContext.require().tenantId();
        return new ExitReminderFailureModels.Page(mapper.count(tenantId,supplierId),page,size,
            mapper.list(tenantId,supplierId,Math.multiplyExact(page,size),size).stream().map(r->new ExitReminderFailureModels.View(
                Long.toString(r.entityId()),Long.toString(r.applicationId()),Long.toString(r.supplierId()),r.failureCount(),r.reasonCode(),r.status(),
                r.firstFailedAt(),r.lastFailedAt(),r.resolvedAt(),r.entityState(),r.applicationStatus(),r.dueDate())).toList());
    }
    private TenantContext.Identity system(){var identity=TenantContext.require();if(identity.actorId()!=0)throw new IllegalArgumentException("system actor required");return identity;}
}

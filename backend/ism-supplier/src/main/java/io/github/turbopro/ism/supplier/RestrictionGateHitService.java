package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RestrictionGateHitService {
    private final RestrictionGateHitMapper mapper;private final OperationIdGenerator ids;private final SupplierService suppliers;
    public RestrictionGateHitService(RestrictionGateHitMapper mapper,OperationIdGenerator ids,SupplierService suppliers){this.mapper=mapper;this.ids=ids;this.suppliers=suppliers;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(long supplierId,SupplierRestrictionEvaluator.Action action,SupplierRestrictionEvaluator.Evaluation evaluation){
        var identity=TenantContext.require();
        for(var hit:evaluation.hits()){
            if(hit.decision()==SupplierRestrictionEvaluator.Decision.ALLOW)continue;
            mapper.insert(ids.nextId(),identity.tenantId(),supplierId,identity.actorId(),action.name(),hit.decision().name(),hit.code(),hit.sourceId(),evaluation.businessDate());
        }
    }
    public record Hit(String id,String supplierId,String actorId,String action,String decision,String code,String sourceId,LocalDate businessDate,LocalDateTime createdAt){}
    public record Page(long total,int page,int size,List<Hit> items){}
    @Transactional(readOnly=true)
    public Page list(long supplierId,int page,int size){
        if(page<0||page>10000||size<1||size>100)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"分页参数无效");
        suppliers.get(supplierId);
        var identity=TenantContext.require();
        long total=mapper.count(identity.tenantId(),supplierId);
        var items=mapper.list(identity.tenantId(),supplierId,Math.multiplyExact(page,size),size).stream().map(r->new Hit(Long.toString(r.id()),Long.toString(r.supplierId()),Long.toString(r.actorId()),r.actionCode(),r.decision(),r.hitCode(),r.sourceId(),r.businessDate(),r.createdAt())).toList();
        return new Page(total,page,size,items);
    }
}

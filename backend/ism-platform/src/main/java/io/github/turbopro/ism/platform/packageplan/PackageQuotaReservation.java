package io.github.turbopro.ism.platform.packageplan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.api.quota.TenantCreationQuota;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class PackageQuotaReservation implements TenantCreationQuota {
    private final PackagePlanMapper mapper;
    private final ObjectMapper json;
    private final OperationIdGenerator ids;
    public PackageQuotaReservation(PackagePlanMapper mapper, ObjectMapper json, OperationIdGenerator ids) {
        this.mapper=mapper; this.json=json; this.ids=ids;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void reserve(String code,long delta,LongSupplier currentUsed) {
        if(delta<=0) throw new IllegalArgumentException("Quota reservation must be positive");
        long tenant=TenantContext.require().tenantId();
        var row=mapper.lockQuotaTenant(tenant);
        if(row==null||!"ACTIVE".equals(row.status())) throw new ApiException(CommonErrorCode.RULE_BLOCKED,"租户当前不能新增资源");
        var now=mapper.utcNow();
        var latest=mapper.lockQuotaSubscription(tenant,now);
        Long version=latest==null?(mapper.lockAnySubscription(tenant)==null?row.packageVersionId():null)
                :latest.effectiveTo()==null||latest.effectiveTo().isAfter(now)?latest.packageVersionId():null;
        long limit=0;
        if(version!=null) for(var module:mapper.lockQuotaModules(version)) {
            try {
                Map<String,Long> values=json.readValue(module.quotaJson(),new TypeReference<>(){});
                Long value=values.get(code);if(value!=null)limit=Math.max(limit,value);
            } catch(java.io.IOException e) { throw new IllegalStateException("Invalid stored quota configuration",e); }
        }
        long used=currentUsed.getAsLong();
        if(used<0)throw new IllegalStateException("Negative domain usage");
        if(delta>limit||used>limit-delta)throw new ApiException(PackagePlanErrorCode.QUOTA_EXCEEDED,"供应商档案额度不足，请联系平台调整套餐；查询和退出处置不受该容量限制");
        mapper.recordQuotaUsage(ids.nextId(),tenant,code,used+delta);
    }
}

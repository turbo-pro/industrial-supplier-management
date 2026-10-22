package io.github.turbopro.ism.iam.portal;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class PortalSessionRetentionService {
    private final PortalSessionRetentionMapper mapper;
    private final PortalSessionRetentionLocator locator;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final int days,batchSize;
    public PortalSessionRetentionService(PortalSessionRetentionMapper mapper,PortalSessionRetentionLocator locator,
            AuditService audit,PlatformTransactionManager manager,
            @Value("${ism.portal.session-cleanup.retention-days:90}") int days,
            @Value("${ism.portal.session-cleanup.batch-size:100}") int batchSize){
        if(days<30 || days>3650 || batchSize<1 || batchSize>500)throw new IllegalArgumentException("Portal session retention requires 30..3650 days and batch size 1..500");
        this.mapper=mapper;this.locator=locator;this.audit=audit;this.days=days;this.batchSize=batchSize;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public List<Long> candidates(long afterTenant){requireBackground();return locator.tenants(afterTenant,locator.cutoff(days));}
    /** System context must be established by an internal worker, never from an HTTP actor parameter. */
    public int cleanBatch(){
        var identity=TenantContext.require();if(identity.actorId()!=0)throw new IllegalStateException("System maintenance context required");
        return Objects.requireNonNull(tx.execute(status->{
            var cutoff=locator.cutoff(days);var ids=mapper.lockBatch(identity.tenantId(),cutoff,batchSize);
            if(ids.isEmpty())return 0;
            int deleted=mapper.deleteBatch(identity.tenantId(),cutoff,ids);
            if(deleted!=ids.size())throw new IllegalStateException("Portal session retention batch changed unexpectedly");
            audit.append(new AuditService.AuditCommand("PORTAL_SESSION_CLEANUP","PORTAL_SESSION_RETENTION",identity.tenantId(),null,
                    Map.of(),Map.of("deletedSessions",deleted,"retentionDays",days,"cutoffUtc",cutoff.toString()),null,null));
            return deleted;
        }));
    }
    private void requireBackground(){if(TenantContext.current().isPresent())throw new IllegalStateException("Unscoped internal worker required");}
}

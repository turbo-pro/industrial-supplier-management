package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.supplier.ExitApplicationService;
import io.github.turbopro.ism.supplier.ExitReminderFailureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Component
public class ExitAutoReminderWorker {
    private static final Logger log=LoggerFactory.getLogger(ExitAutoReminderWorker.class);
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private final ExitReminderDispatchMapper dispatch;
    private final ExitApplicationService exits;
    private final ExitReminderFailureService failures;
    private final TransactionTemplate transactions;
    private final boolean enabled;
    private volatile long scanCursor;

    public ExitAutoReminderWorker(ExitReminderDispatchMapper dispatch,ExitApplicationService exits,ExitReminderFailureService failures,
                                  TransactionTemplate transactions,@Value("${ism.exit.auto-reminder.worker-enabled:true}") boolean enabled){
        this.dispatch=dispatch;this.exits=exits;this.failures=failures;this.transactions=transactions;this.enabled=enabled;
    }

    @Scheduled(fixedDelayString="${ism.exit.auto-reminder.poll-interval:300000}")
    public void poll(){
        if(!enabled)return;
        long cursor=scanCursor;
        var today=LocalDate.now(BUSINESS_ZONE);
        var before=LocalDateTime.now(ZoneOffset.UTC).minusHours(24);
        // Bounded batch, with keyset pagination so one invalid item cannot starve later candidates.
        for(int page=0;page<10;page++){
            var batch=dispatch.candidates(cursor,today,before,100);
            if(batch.isEmpty()){scanCursor=0;return;}
            for(var candidate:batch){
                try(var context=TenantContext.openSystem(candidate.tenantId())){
                    transactions.executeWithoutResult(status->{
                        if(!"1".equals(dispatch.enabled(candidate.tenantId())))return;
                        String configuredHours=dispatch.intervalHours(candidate.tenantId());
                        int hours=Integer.parseInt(configuredHours==null?"24":configuredHours);
                        if(exits.autoRemind(candidate.supplierId(),candidate.applicationId(),candidate.id(),hours))
                            failures.delivered(candidate.supplierId(),candidate.applicationId(),candidate.id());
                    });
                }catch(Exception ex){
                    log.warn("Automatic exit reminder failed for tenant={} entity={}",candidate.tenantId(),candidate.id(),ex);
                    try(var context=TenantContext.openSystem(candidate.tenantId())){
                        String reason=ex instanceof ApiException api?api.errorCode().code():"WORKER_FAILED";
                        transactions.executeWithoutResult(status->failures.record(candidate.supplierId(),candidate.applicationId(),candidate.id(),reason));
                    }catch(Exception ledgerError){log.error("Cannot record exit reminder failure for tenant={} entity={}",candidate.tenantId(),candidate.id(),ledgerError);}
                }
            }
            cursor=batch.get(batch.size()-1).id();
            scanCursor=cursor;
            if(batch.size()<100){scanCursor=0;return;}
        }
    }
}

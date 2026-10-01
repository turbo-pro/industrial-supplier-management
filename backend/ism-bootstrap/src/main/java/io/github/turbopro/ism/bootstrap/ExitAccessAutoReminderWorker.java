package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.supplier.ExitAccessRecoveryService;
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
public class ExitAccessAutoReminderWorker {
    private static final Logger log=LoggerFactory.getLogger(ExitAccessAutoReminderWorker.class);
    private final ExitAccessReminderDispatchMapper dispatch;
    private final ExitAccessRecoveryService recovery;
    private final TransactionTemplate transactions;
    private final boolean enabled;
    private volatile long scanCursor;

    public ExitAccessAutoReminderWorker(ExitAccessReminderDispatchMapper dispatch,ExitAccessRecoveryService recovery,
                                        TransactionTemplate transactions,@Value("${ism.exit.access-auto-reminder.worker-enabled:true}") boolean enabled){
        this.dispatch=dispatch;this.recovery=recovery;this.transactions=transactions;this.enabled=enabled;
    }

    @Scheduled(fixedDelayString="${ism.exit.access-auto-reminder.poll-interval:300000}")
    public void poll(){
        if(!enabled)return;
        long cursor=scanCursor;
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var before=LocalDateTime.now(ZoneOffset.UTC).minusHours(24);
        for(int page=0;page<10;page++){
            var batch=dispatch.candidates(cursor,today,before,100);
            if(batch.isEmpty()){scanCursor=0;return;}
            for(var candidate:batch){
                try(var context=TenantContext.openSystem(candidate.tenantId())){
                    transactions.executeWithoutResult(status->{
                        if(!"1".equals(dispatch.enabled(candidate.tenantId())))return;
                        String configured=dispatch.intervalHours(candidate.tenantId());
                        int hours=Integer.parseInt(configured==null?"24":configured);
                        recovery.autoRemind(candidate.supplierId(),candidate.applicationId(),candidate.id(),hours);
                    });
                }catch(Exception ex){
                    log.warn("Automatic exit access reminder failed for tenant={} task={}",candidate.tenantId(),candidate.id(),ex);
                }
            }
            cursor=batch.get(batch.size()-1).id();scanCursor=cursor;
            if(batch.size()<100){scanCursor=0;return;}
        }
    }
}

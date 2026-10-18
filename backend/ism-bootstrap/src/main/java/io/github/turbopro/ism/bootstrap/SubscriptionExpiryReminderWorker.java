package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.platform.packageplan.SubscriptionReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionExpiryReminderWorker {
    private static final Logger log=LoggerFactory.getLogger(SubscriptionExpiryReminderWorker.class);
    private final SubscriptionReminderService service;
    private final boolean enabled;
    private long cursor;
    public SubscriptionExpiryReminderWorker(SubscriptionReminderService service,
            @Value("${ism.subscription-reminders.enabled:false}") boolean enabled) { this.service=service;this.enabled=enabled; }
    @Scheduled(fixedDelayString="${ism.subscription-reminders.poll-interval:300000}")
    public synchronized void poll() {
        if(!enabled) return;
        for(int page=0;page<10;page++) {
            var batch=service.candidates(cursor,100);
            if(batch.isEmpty()) { cursor=0;return; }
            for(var row:batch) {
                try { service.dispatch(row.tenantId(),row.subscriptionId()); }
                catch(Exception failure) { log.warn("Subscription reminder attempt failed tenant={} subscription={}",row.tenantId(),row.subscriptionId(),failure); }
            }
            cursor=batch.get(batch.size()-1).subscriptionId();
            if(batch.size()<100) { cursor=0;return; }
        }
    }
}

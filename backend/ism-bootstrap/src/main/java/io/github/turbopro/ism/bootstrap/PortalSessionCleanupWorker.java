package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.iam.portal.PortalSessionRetentionService;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PortalSessionCleanupWorker {
    private static final Logger log=LoggerFactory.getLogger(PortalSessionCleanupWorker.class);
    private final PortalSessionRetentionService service;
    private final boolean enabled;
    private long cursor;
    public PortalSessionCleanupWorker(PortalSessionRetentionService service,@Value("${ism.portal.session-cleanup.enabled:false}") boolean enabled){this.service=service;this.enabled=enabled;}
    @Scheduled(fixedDelayString="${ism.portal.session-cleanup.poll-interval:3600000}",initialDelayString="${ism.portal.session-cleanup.initial-delay:3600000}")
    public synchronized void poll(){
        if(!enabled)return;
        var tenants=service.candidates(cursor);
        if(tenants.isEmpty()){cursor=0;return;}
        for(long tenant:tenants){
            try(var context=TenantContext.openSystem(tenant)){
                int deleted=service.cleanBatch();
                if(deleted>0)log.info("Portal session retention tenant={} deleted={}",tenant,deleted);
            }catch(Exception failure){log.warn("Portal session retention failed tenant={} type={}",tenant,failure.getClass().getSimpleName());}
            cursor=tenant;
        }
        if(tenants.size()<50)cursor=0;
    }
}

package io.github.turbopro.ism.operation;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.common.infrastructure.web.TraceIdContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class OutboxService {
    private final TenantOperationMapper tenantMapper;
    private final DispatchOperationMapper dispatchMapper;
    private final OperationIdGenerator ids;
    private final SensitivePayloadSanitizer sanitizer;

    public OutboxService(TenantOperationMapper tenantMapper, DispatchOperationMapper dispatchMapper,
                         OperationIdGenerator ids, SensitivePayloadSanitizer sanitizer) {
        this.tenantMapper = tenantMapper;
        this.dispatchMapper = dispatchMapper;
        this.ids = ids;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public String enqueue(String eventType, int eventVersion, String aggregateType,
                          long aggregateId, Object payload) {
        long tenantId = TenantContext.require().tenantId();
        String eventId = UUID.randomUUID().toString();
        LocalDateTime now = utcNow();
        tenantMapper.insertOutbox(ids.nextId(), eventId, tenantId, eventType, eventVersion,
                aggregateType, aggregateId, sanitizer.sanitize(payload), now, TraceIdContext.currentTraceId());
        return eventId;
    }

    @Transactional
    public Optional<OperationModels.OutboxEvent> claimNext(String nodeId, Duration leaseDuration) {
        LocalDateTime now = utcNow();
        OperationModels.OutboxEvent event = dispatchMapper.lockNextOutbox(now);
        if (event == null) {
            return Optional.empty();
        }
        return dispatchMapper.claimOutbox(event.id(), nodeId, now, now.plus(leaseDuration)) == 1
                ? Optional.of(event) : Optional.empty();
    }

    @Transactional
    public boolean markPublished(long eventId, String nodeId) {
        return dispatchMapper.publishOutbox(eventId, nodeId, utcNow()) == 1;
    }

    @Transactional
    public boolean markFailed(long eventId, String nodeId, int retryCount, int maxRetries,
                              Duration retryDelay, String stableErrorCode) {
        String nextStatus = retryCount + 1 >= maxRetries ? "DEAD" : "PENDING";
        String error = stableErrorCode == null ? "DISPATCH_FAILED"
                : stableErrorCode.substring(0, Math.min(stableErrorCode.length(), 100));
        return dispatchMapper.failOutbox(eventId, nodeId, nextStatus,
                utcNow().plus(retryDelay), error) == 1;
    }

    @Transactional
    public boolean consumeOnce(String eventId, String consumerCode, Runnable sideEffect) {
        try {
            dispatchMapper.insertConsumption(ids.nextId(), eventId, consumerCode, utcNow());
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
        sideEffect.run();
        return true;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}

package io.github.turbopro.ism.operation;

import java.time.LocalDateTime;

public final class OperationModels {
    private OperationModels() {
    }

    public record IdempotencyRecord(long id, long tenantId, long principalId, String operationCode,
                                    String idempotencyKey, String requestHash, String status,
                                    Integer responseStatus, String responseBody) {
    }

    public record OutboxEvent(long id, String eventId, long tenantId, String eventType, int eventVersion,
                              String aggregateType, long aggregateId, String payload, String status,
                              int retryCount, String leaseOwner, LocalDateTime leaseUntil, String traceId) {
    }

    public record AsyncTask(long id, long tenantId, String taskNo, String taskType, long requesterId,
                            String requestPayload, String status, int progress, String leaseOwner,
                            LocalDateTime leaseUntil, int retryCount, int version) {
    }
}

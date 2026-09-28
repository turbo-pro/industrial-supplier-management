package io.github.turbopro.ism.supplier;

import java.time.LocalDateTime;
import java.util.List;

public final class ExitAccessRecoveryModels {
    private ExitAccessRecoveryModels() {}
    public record TaskRow(long id,String channel,String status,LocalDateTime createdAt) {}
    public record Task(String id,String channel,String status,LocalDateTime createdAt) {}
    public record Inventory(String supplierId,String applicationId,String accessRecoveryStatus,List<Task> tasks) {}
}

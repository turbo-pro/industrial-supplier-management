package io.github.turbopro.ism.supplier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ExitReminderFailureModels {
    private ExitReminderFailureModels(){}
    public record Row(long entityId,long applicationId,long supplierId,int failureCount,String reasonCode,
                      String status,LocalDateTime firstFailedAt,LocalDateTime lastFailedAt,
                      LocalDateTime resolvedAt,String entityState,String applicationStatus,LocalDate dueDate){}
    public record View(String entityId,String applicationId,String supplierId,int failureCount,String reasonCode,
                       String status,LocalDateTime firstFailedAt,LocalDateTime lastFailedAt,
                       LocalDateTime resolvedAt,String entityState,String applicationStatus,LocalDate dueDate){}
    public record Page(long total,int page,int size,List<View> items){}
}

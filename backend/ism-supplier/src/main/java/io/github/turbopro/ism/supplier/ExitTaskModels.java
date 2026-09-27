package io.github.turbopro.ism.supplier;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

public final class ExitTaskModels {
    private ExitTaskModels(){}
    public record Row(long id,long applicationId,long supplierId,String supplierCode,String supplierName,
        long organizationId,String checkCode,long sourceId,String route,String note,LocalDateTime assignedAt,
        LocalDateTime checkedAt,int version,int applicationVersion,LocalDate dueDate,LocalDateTime lastRemindedAt){}
    public record Task(String id,String applicationId,String supplierId,String supplierCode,String supplierName,
        String organizationId,String code,String sourceId,String route,String note,LocalDateTime assignedAt,
        LocalDateTime checkedAt,int version,int applicationVersion,LocalDate dueDate,LocalDateTime lastRemindedAt,boolean overdue){}
    public record Page(long total,int page,int size,List<Task> items){}
}

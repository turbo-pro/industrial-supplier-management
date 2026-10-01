package io.github.turbopro.ism.supplier;

/** Transactional in-app notification; never claims that disposition is complete. */
public interface ExitAssignmentNotifier {
    void assigned(long supplierId,long applicationId,long entityId,String code,long sourceId,long assigneeId);
    void reminded(long supplierId,long applicationId,long entityId,String code,long sourceId,long assigneeId,java.time.LocalDate dueDate);
    void escalated(long supplierId,long applicationId,long entityId,String code,long sourceId,long assigneeId,long recipientId,java.time.LocalDate dueDate);
    void accessAssigned(long supplierId,long applicationId,long taskId,String channel,long assigneeId,java.time.LocalDate dueDate);
}

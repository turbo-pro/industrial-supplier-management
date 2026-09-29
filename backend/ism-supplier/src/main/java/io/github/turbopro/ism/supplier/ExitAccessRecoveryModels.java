package io.github.turbopro.ism.supplier;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.*;

public final class ExitAccessRecoveryModels {
    private ExitAccessRecoveryModels() {}
    public enum Finding { PRESENT, ABSENT }
    public record RecordFinding(@NotNull Finding finding,@NotBlank String evidenceFileId,
                                @NotBlank @Size(max=1000) String note,@PositiveOrZero int version) {}
    public record Assign(@NotBlank String assigneeId,LocalDate dueDate,@NotBlank @Size(max=1000) String note,
                         @PositiveOrZero int version) {}
    public record TaskRow(long id,String channel,String status,String finding,Long evidenceFileId,
                          String discoveryNote,Long discoveredBy,LocalDateTime discoveredAt,
                          Long assigneeId,LocalDate dueDate,String assignmentNote,Long assignedBy,LocalDateTime assignedAt,
                          int version,LocalDateTime createdAt) {}
    public record EventRow(long id,long taskId,String finding,long evidenceFileId,String note,long actorId,LocalDateTime createdAt) {}
    public record Event(String id,String finding,String evidenceFileId,String note,String actorId,LocalDateTime createdAt) {}
    public record AssignmentEventRow(long id,long taskId,Long previousAssigneeId,long assigneeId,LocalDate dueDate,
                                     String note,long actorId,LocalDateTime createdAt) {}
    public record AssignmentEvent(String id,String previousAssigneeId,String assigneeId,LocalDate dueDate,
                                  String note,String actorId,LocalDateTime createdAt) {}
    public record Task(String id,String channel,String status,String finding,String evidenceFileId,
                       String discoveryNote,String discoveredBy,LocalDateTime discoveredAt,
                       String assigneeId,LocalDate dueDate,String assignmentNote,String assignedBy,LocalDateTime assignedAt,
                       int version,LocalDateTime createdAt,List<Event> events,List<AssignmentEvent> assignments) {}
    public record Inventory(String supplierId,String applicationId,String accessRecoveryStatus,List<Task> tasks) {}
}

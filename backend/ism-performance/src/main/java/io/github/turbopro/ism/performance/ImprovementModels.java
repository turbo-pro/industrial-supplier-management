package io.github.turbopro.ism.performance;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class ImprovementModels {
    private ImprovementModels() {}
    public enum Status { OPEN, SUBMITTED, ACCEPTED, REWORK }
    public enum Decision { ACCEPT, REWORK }
    public record Create(@NotBlank @Size(max=1000) String rootCause,
                         @NotBlank @Size(max=2000) String actionPlan, @NotNull LocalDate dueDate) {}
    public record Submit(@NotBlank @Size(max=2000) String completionNote,
                         @NotBlank String evidenceFileId, @NotNull @PositiveOrZero Integer version) {}
    public record Review(@NotNull Decision decision, @NotBlank @Size(max=1000) String comment,
                         @NotNull @PositiveOrZero Integer version) {}
    public record Row(long id, long evaluationId, String rootCause, String actionPlan, LocalDate dueDate,
                      String status, String completionNote, Long evidenceFileId, String reviewComment,
                      Long reviewedBy, LocalDateTime reviewedAt, long createdBy, int version,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record View(String id, String evaluationId, String rootCause, String actionPlan, LocalDate dueDate,
                       Status status, String completionNote, String evidenceFileId, String reviewComment,
                       String reviewedBy, LocalDateTime reviewedAt, String createdBy, int version,
                       LocalDateTime createdAt, LocalDateTime updatedAt, List<Event> events) {}
    public record Event(String id, String action, String fromStatus, String toStatus, String comment,
                        String actorId, LocalDateTime createdAt) {}
}

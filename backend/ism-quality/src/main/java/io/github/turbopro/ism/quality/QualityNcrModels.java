package io.github.turbopro.ism.quality;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class QualityNcrModels {
    private QualityNcrModels() {}
    public enum Category { MATERIAL, PROCESS, DELIVERY, DOCUMENT, OTHER }
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum Status { OPEN, PENDING_REVIEW, CLOSED }
    public enum Decision { PASS, REJECT }
    public record Create(@NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{1,63}") String ncrNo,
                         @NotBlank String projectId, @NotBlank @Size(max = 200) String title,
                         @NotNull Category category, @NotNull Severity severity,
                         @NotBlank @Size(max = 2000) String description,
                         @NotNull LocalDate inspectionDate,
                         @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal inspectedQuantity,
                         @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal defectiveQuantity,
                         @NotBlank @Size(max = 32) String unit, @NotBlank String evidenceFileId,
                         @NotNull LocalDate deadline, @NotBlank String responsibleUserId) {}
    public record Rectify(@NotBlank @Size(max = 2000) String rootCause,
                          @NotBlank @Size(max = 2000) String correction,
                          @NotBlank @Size(max = 2000) String preventiveAction,
                          @NotBlank String fileId, @NotNull @PositiveOrZero Integer version) {}
    public record Verify(@NotNull Decision decision, @NotBlank @Size(max = 1000) String comment,
                         @NotNull @PositiveOrZero Integer version) {}
    public record ProjectRef(long organizationId, long supplierId) {}
    public record Row(long id, long organizationId, long projectId, long supplierId,
                      String projectCode, String projectName, String supplierCode, String supplierName,
                      String ncrNo, String title, String category, String severity, String description,
                      LocalDate inspectionDate, BigDecimal inspectedQuantity, BigDecimal defectiveQuantity,
                      String unit, long evidenceFileId, LocalDate deadline, long responsibleUserId,
                      String status, String rootCause, String correction, String preventiveAction,
                      Long actionFileId, LocalDateTime submittedAt, String verificationResult,
                      String verificationComment, Long verifiedBy, LocalDateTime verifiedAt,
                      long createdBy, int version, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record View(String id, String organizationId, String projectId, String supplierId,
                       String projectCode, String projectName, String supplierCode, String supplierName,
                       String ncrNo, String title, Category category, Severity severity, String description,
                       LocalDate inspectionDate, BigDecimal inspectedQuantity, BigDecimal defectiveQuantity,
                       String unit, String evidenceFileId, LocalDate deadline, String responsibleUserId,
                       Status status, String rootCause, String correction, String preventiveAction,
                       String actionFileId, LocalDateTime submittedAt, String verificationResult,
                       String verificationComment, String verifiedBy, LocalDateTime verifiedAt,
                       boolean overdue, int version, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record Page(long total, int page, int size, List<View> items) {}
    public record Event(String id, String action, String fromStatus, String toStatus,
                        String note, String fileId, String actorId, LocalDateTime createdAt) {}
}

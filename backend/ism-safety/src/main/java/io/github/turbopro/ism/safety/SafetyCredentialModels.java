package io.github.turbopro.ism.safety;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class SafetyCredentialModels {
    private SafetyCredentialModels() {}

    public enum Kind { TRAINING, SPECIAL_WORK }
    public enum WorkType { ELECTRICAL, WELDING, WORK_AT_HEIGHT, OTHER }
    public enum Status { PENDING, VERIFIED, REJECTED, REVOKED }
    public enum Decision { APPROVE, REJECT, REVOKE }

    public record Create(
            @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{1,63}") String credentialNo,
            @NotBlank String personId,
            @NotNull Kind kind,
            WorkType workType,
            @NotBlank @Size(max = 200) String title,
            @DecimalMin("0") @DecimalMax("100") BigDecimal examScore,
            Boolean passed,
            @NotNull LocalDate effectiveDate,
            @NotNull LocalDate expiryDate,
            @NotBlank String fileId
    ) {}

    public record Review(@NotNull Decision decision, @Size(max = 500) String comment,
                         @NotNull @PositiveOrZero Integer version) {}

    public record PersonRef(long id, long organizationId, long supplierId, Long projectId,
                            String personCode, String personName, String specialWorkType,
                            String status, long createdBy) {}

    public record Row(long id, long organizationId, long supplierId, long personId,
                      Long projectId, String personCode, String personName, String credentialNo,
                      String credentialKind, String workType, String title, BigDecimal examScore,
                      Boolean passed, LocalDate effectiveDate, LocalDate expiryDate, long fileId,
                      String status, String reviewComment, Long reviewedBy, LocalDateTime reviewedAt,
                      long createdBy, int version, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record View(String id, String personId, String personCode, String personName,
                       String credentialNo, Kind kind, WorkType workType, String title,
                       BigDecimal examScore, Boolean passed, LocalDate effectiveDate,
                       LocalDate expiryDate, String fileId, Status status, String reviewComment,
                       String reviewedBy, LocalDateTime reviewedAt, boolean currentlyValid,
                       int version, LocalDateTime updatedAt) {}

    public record Page(long total, int page, int size, List<View> items) {}

    public record Eligibility(String personId, String personName, String personStatus,
                              WorkType requiredWorkType, boolean trainingValid,
                              boolean specialWorkValid, boolean eligible) {}
}

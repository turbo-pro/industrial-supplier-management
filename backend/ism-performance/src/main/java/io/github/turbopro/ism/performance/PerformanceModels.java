package io.github.turbopro.ism.performance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class PerformanceModels {
    private PerformanceModels() {}
    public enum Dimension { QUALITY, DELIVERY, SAFETY, SERVICE }
    public enum Status { DRAFT, SUBMITTED, APPROVED, REJECTED }
    public enum Grade { A, B, C, D }
    public enum Decision { APPROVE, REJECT }
    public record Rule(int qualityWeight, int deliveryWeight, int safetyWeight, int serviceWeight,
                       int version, boolean customized) {}
    public record SaveRule(@Min(0) @Max(100) int qualityWeight, @Min(0) @Max(100) int deliveryWeight,
                           @Min(0) @Max(100) int safetyWeight, @Min(0) @Max(100) int serviceWeight,
                           @NotNull @PositiveOrZero Integer version) {}
    public record ScoreItem(@NotNull Dimension dimension,
                            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal score,
                            @NotBlank @Size(max = 1000) String comment, @NotBlank String evidenceFileId) {}
    public record SaveEvaluation(@NotBlank String supplierId, @NotNull LocalDate periodStart,
                                 @NotNull LocalDate periodEnd,
                                 @NotNull @Size(min = 4, max = 4) List<@Valid ScoreItem> items,
                                 @NotNull @PositiveOrZero Integer version) {}
    public record Review(@NotNull Decision decision, @Size(max = 1000) String comment,
                         @NotNull @PositiveOrZero Integer version) {}
    public record Row(long id, long organizationId, long supplierId, String supplierCode, String supplierName,
                      LocalDate periodStart, LocalDate periodEnd, BigDecimal totalScore, String grade,
                      String status, int qualityNcrTotal, int qualityNcrOpen, int safetyIssueTotal,
                      int safetyIssueOpen, LocalDateTime submittedAt, Long reviewedBy, LocalDateTime reviewedAt,
                      String reviewComment, long createdBy, int version, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record ItemRow(String dimensionCode, int weight, BigDecimal score, String comment, long evidenceFileId) {}
    public record ItemView(Dimension dimension, int weight, BigDecimal score, String comment, String evidenceFileId) {}
    public record View(String id, String organizationId, String supplierId, String supplierCode, String supplierName,
                       LocalDate periodStart, LocalDate periodEnd, BigDecimal totalScore, Grade grade, Status status,
                       int qualityNcrTotal, int qualityNcrOpen, int safetyIssueTotal, int safetyIssueOpen,
                       LocalDateTime submittedAt, String reviewedBy, LocalDateTime reviewedAt, String reviewComment,
                       String createdBy, int version, LocalDateTime createdAt, LocalDateTime updatedAt, List<ItemView> items) {}
    public record Page(long total, int page, int size, List<View> items) {}
    public record Event(String id, String action, String fromStatus, String toStatus, String comment,
                        String actorId, LocalDateTime createdAt) {}
}

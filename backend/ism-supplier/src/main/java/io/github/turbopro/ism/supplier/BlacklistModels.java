package io.github.turbopro.ism.supplier;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class BlacklistModels {
    private BlacklistModels() {}
    public enum Status { DRAFT, SUBMITTED, APPROVED, REJECTED, REVOKED }
    public enum RestrictionType { BLACKLIST, TEMPORARY, WATCH }
    public enum Decision { APPROVE, REJECT }
    public record Create(@NotBlank String supplierId,@NotBlank @Size(max=1000) String reason,
                         @Size(max=200) String sourceRef,@NotNull RestrictionType restrictionType,
                         LocalDate effectiveFrom,LocalDate effectiveUntil) {}
    public record Version(@NotNull @PositiveOrZero Integer version) {}
    public record Review(@NotNull Decision decision,@NotBlank @Size(max=1000) String comment,
                         @NotNull @PositiveOrZero Integer version) {}
    public record Revoke(@NotBlank @Size(max=1000) String reason,@NotNull @PositiveOrZero Integer version) {}
    public record Row(long id,long supplierId,long organizationId,String supplierCode,String supplierName,
                      RestrictionType restrictionType,LocalDate effectiveFrom,LocalDate effectiveUntil,
                      String reason,String sourceRef,String status,String reviewComment,Long reviewedBy,
                      LocalDateTime reviewedAt,String revokedReason,Long revokedBy,LocalDateTime revokedAt,
                      long createdBy,int version,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record View(String id,String supplierId,String organizationId,String supplierCode,String supplierName,
                       RestrictionType restrictionType,LocalDate effectiveFrom,LocalDate effectiveUntil,boolean effective,boolean lifted,
                       String reason,String sourceRef,Status status,String reviewComment,String reviewedBy,
                       LocalDateTime reviewedAt,String revokedReason,String revokedBy,LocalDateTime revokedAt,
                       String createdBy,int version,LocalDateTime createdAt,LocalDateTime updatedAt,List<Event> events) {}
    public record Page(long total,int page,int size,List<View> items) {}
    public record Event(String id,String action,String fromStatus,String toStatus,String comment,String actorId,
                        LocalDateTime createdAt) {}
}

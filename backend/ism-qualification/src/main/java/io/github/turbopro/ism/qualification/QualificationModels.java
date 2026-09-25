package io.github.turbopro.ism.qualification;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class QualificationModels {
    private QualificationModels() {}
    public enum TypeStatus { ACTIVE, DISABLED }
    public enum Status { DRAFT, VALID, EXPIRING, EXPIRED, REJECTED, REVOKED, SUPERSEDED }
    public enum Decision { APPROVE, REJECT }
    public record SaveType(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") String code,@NotBlank @Size(max=100) String name,
        @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") String category,boolean validityRequired,
        @Min(0) @Max(3650) int defaultWarningDays,@Size(max=500) String description,@NotNull TypeStatus status,
        @NotNull @PositiveOrZero Integer version) {}
    public record TypeRow(long id,String typeCode,String typeName,String category,boolean validityRequired,int defaultWarningDays,String description,String status,int version,LocalDateTime updatedAt) {}
    public record TypeView(String id,String code,String name,String category,boolean validityRequired,int defaultWarningDays,String description,TypeStatus status,int version,LocalDateTime updatedAt) {}
    public record SaveQualification(@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String supplierId,@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String typeId,
        @NotBlank @Size(max=100) String certificateNo,@Size(max=200) String issuingAuthority,LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,
        boolean permanent,@Min(0) @Max(3650) Integer warningDays,@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String fileId,
        @NotNull @PositiveOrZero Integer version) {}
    public record VerifyCommand(@NotNull Decision decision,@NotBlank @Size(max=1000) String comment,@NotNull @PositiveOrZero Integer version) {}
    public record RevokeCommand(@NotBlank @Size(max=500) String reason,@NotNull @PositiveOrZero Integer version) {}
    public record Row(long id,long organizationId,long supplierId,long qualificationTypeId,Long parentQualificationId,String supplierCode,String supplierName,String typeCode,String typeName,
        String certificateNo,String issuingAuthority,LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,boolean permanent,int warningDays,long fileId,
        String status,String verificationComment,Long verifiedBy,LocalDateTime verifiedAt,String revokedReason,long createdBy,int version,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record Summary(String id,String organizationId,String supplierId,String supplierCode,String supplierName,String typeId,String typeCode,String typeName,
        String certificateNo,LocalDate expiryDate,boolean permanent,Status status,int daysUntilExpiry,int version,LocalDateTime updatedAt) {}
    public record View(String id,String organizationId,String supplierId,String supplierCode,String supplierName,String typeId,String typeCode,String typeName,String parentQualificationId,
        String certificateNo,String issuingAuthority,LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,boolean permanent,int warningDays,String fileId,Status status,
        String verificationComment,String verifiedBy,LocalDateTime verifiedAt,String revokedReason,int version,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record Page(long total,int page,int size,List<Summary> items) {}
}

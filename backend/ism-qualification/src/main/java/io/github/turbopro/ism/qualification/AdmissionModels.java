package io.github.turbopro.ism.qualification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdmissionModels {
    private AdmissionModels() {}
    public enum Status { DRAFT, SUBMITTED, REVISION_REQUIRED, APPROVED, REJECTED, CANCELLED }
    public enum Decision { APPROVE, REQUIRE_REVISION, REJECT }
    public record MaterialCommand(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") String type,
        @NotBlank @Size(max=100) String name,String fileId,boolean required,boolean provided,
        @Size(max=300) String remark,@PositiveOrZero int sortOrder) {}
    public record SaveApplication(@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String supplierId,
        @NotBlank @Size(max=100) String purchaseCategory,@NotBlank @Size(max=500) String reason,
        @DecimalMin("0.00") BigDecimal expectedAnnualAmount,@Pattern(regexp="^[A-Z]{3}$") String currency,
        @Valid @NotEmpty @Size(max=50) List<MaterialCommand> materials,@NotNull @PositiveOrZero Integer version) {
        public SaveApplication { materials=materials==null?List.of():List.copyOf(materials); }
    }
    public record ReviewCommand(@NotNull Decision decision,@NotBlank @Size(max=1000) String comment,
        @NotNull @PositiveOrZero Integer version) {}
    public record Row(long id,long organizationId,long supplierId,String applicationNo,String supplierCode,String supplierName,
        String purchaseCategory,String admissionReason,BigDecimal expectedAnnualAmount,String currency,String status,
        String workflowInstanceId,Long currentReviewerId,long createdBy,int version,LocalDateTime submittedAt,
        LocalDateTime decidedAt,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record MaterialRow(long id,String materialType,String materialName,Long fileId,boolean required,boolean provided,String remark,int sortOrder) {}
    public record ReviewRow(long id,String action,String fromStatus,String toStatus,String commentText,long operatorId,LocalDateTime operatedAt) {}
    public record MaterialView(String id,String type,String name,String fileId,boolean required,boolean provided,String remark,int sortOrder) {}
    public record ReviewView(String id,String action,Status fromStatus,Status toStatus,String comment,String operatorId,LocalDateTime operatedAt) {}
    public record Summary(String id,String organizationId,String supplierId,String applicationNo,String supplierCode,String supplierName,
        String purchaseCategory,Status status,int version,LocalDateTime submittedAt,LocalDateTime updatedAt) {}
    public record View(String id,String organizationId,String supplierId,String applicationNo,String supplierCode,String supplierName,
        String purchaseCategory,String reason,BigDecimal expectedAnnualAmount,String currency,Status status,String workflowInstanceId,
        int version,LocalDateTime submittedAt,LocalDateTime decidedAt,LocalDateTime createdAt,LocalDateTime updatedAt,
        List<MaterialView> materials,List<ReviewView> reviews) {}
    public record Page(long total,int page,int size,List<Summary> items) {}
}

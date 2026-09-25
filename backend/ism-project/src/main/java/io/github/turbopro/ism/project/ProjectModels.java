package io.github.turbopro.ism.project;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class ProjectModels {
    private ProjectModels() {}
    public enum ContractType { PURCHASE, SERVICE, ENGINEERING, FRAMEWORK, OTHER }
    public enum ContractStatus { DRAFT, ACTIVE, COMPLETED, TERMINATED, EXPIRED }
    public enum ProjectType { CONSTRUCTION, MAINTENANCE, TECHNICAL_SERVICE, LOGISTICS, OTHER }
    public enum ProjectStatus { PLANNED, ACTIVE, SUSPENDED, COMPLETED, CANCELLED }
    public record SaveContract(@NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_/-]{1,63}") String contractNo,@NotBlank @Size(max=200) String name,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String supplierId,@NotNull ContractType type,@NotNull @DecimalMin("0.00") BigDecimal amount,
        @NotBlank @Pattern(regexp="^[A-Z]{3}$") String currency,LocalDate signedDate,@NotNull LocalDate startDate,@NotNull LocalDate endDate,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String ownerId,@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String fileId,@NotNull @PositiveOrZero Integer version) {}
    public record ChangeContractStatus(@NotNull ContractStatus status,@Size(max=500) String reason,@NotNull @PositiveOrZero Integer version) {}
    public record ContractRow(long id,long organizationId,long supplierId,String supplierCode,String supplierName,String contractNo,String contractName,String contractType,BigDecimal amount,String currency,LocalDate signedDate,LocalDate startDate,LocalDate endDate,long ownerId,long fileId,String status,String terminationReason,long createdBy,int version,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record ContractSummary(String id,String organizationId,String supplierId,String supplierCode,String supplierName,String contractNo,String name,ContractType type,BigDecimal amount,String currency,LocalDate startDate,LocalDate endDate,ContractStatus status,int version,LocalDateTime updatedAt) {}
    public record ContractView(String id,String organizationId,String supplierId,String supplierCode,String supplierName,String contractNo,String name,ContractType type,BigDecimal amount,String currency,LocalDate signedDate,LocalDate startDate,LocalDate endDate,String ownerId,String fileId,ContractStatus status,String terminationReason,int version,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record ContractPage(long total,int page,int size,List<ContractSummary> items) {}
    public record SaveProject(@NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_-]{1,63}") String projectCode,@NotBlank @Size(max=200) String name,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String supplierId,String contractId,@NotNull ProjectType type,@Size(max=300) String siteAddress,
        @NotNull LocalDate plannedStartDate,@NotNull LocalDate plannedEndDate,@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String managerId,
        @DecimalMin("0.00") BigDecimal budgetAmount,@Pattern(regexp="^[A-Z]{3}$") String currency,@NotNull @PositiveOrZero Integer version) {}
    public record ChangeProjectStatus(@NotNull ProjectStatus status,@Size(max=500) String reason,@NotNull @PositiveOrZero Integer version) {}
    public record ProjectRow(long id,long organizationId,long supplierId,Long contractId,String supplierCode,String supplierName,String contractNo,String projectCode,String projectName,String projectType,String siteAddress,LocalDate plannedStartDate,LocalDate plannedEndDate,LocalDate actualStartDate,LocalDate actualEndDate,long managerId,BigDecimal budgetAmount,String currency,String status,String statusReason,long createdBy,int version,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record ProjectSummary(String id,String organizationId,String supplierId,String supplierCode,String supplierName,String contractId,String contractNo,String projectCode,String name,ProjectType type,LocalDate plannedStartDate,LocalDate plannedEndDate,ProjectStatus status,int version,LocalDateTime updatedAt) {}
    public record ProjectView(String id,String organizationId,String supplierId,String supplierCode,String supplierName,String contractId,String contractNo,String projectCode,String name,ProjectType type,String siteAddress,LocalDate plannedStartDate,LocalDate plannedEndDate,LocalDate actualStartDate,LocalDate actualEndDate,String managerId,BigDecimal budgetAmount,String currency,ProjectStatus status,String statusReason,int version,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record ProjectPage(long total,int page,int size,List<ProjectSummary> items) {}
}

package io.github.turbopro.ism.resource.supplier;

import jakarta.validation.constraints.*;import java.time.*;import java.util.List;
public final class SupplierResourceModels {
 private SupplierResourceModels(){}
 public enum PersonStatus{PENDING,ACTIVE,SUSPENDED,EXITED} public enum IdType{NATIONAL_ID,PASSPORT,OTHER}
 public enum SpecialWorkType{ELECTRICAL,WELDING,WORK_AT_HEIGHT,OTHER}
 public enum AssetType{VEHICLE,EQUIPMENT,TOOL} public enum AssetStatus{PENDING,AVAILABLE,IN_USE,MAINTENANCE,RETIRED}
 public record SavePerson(@NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_-]{1,63}") String code,@NotBlank @Size(max=100) String name,@NotBlank String supplierId,String projectId,@NotNull IdType idType,@NotBlank @Size(max=64) String idNumber,@Pattern(regexp="^$|^[0-9+() -]{6,32}$") String mobile,@Size(max=100) String jobTitle,@Size(max=100) String tradeType,SpecialWorkType specialWorkType,LocalDate entryDate,@NotNull @PositiveOrZero Integer version){}
 public record PersonStatusCommand(@NotNull PersonStatus status,@Size(max=500) String reason,@NotNull @PositiveOrZero Integer version){}
 public record PersonRow(long id,long organizationId,long supplierId,Long projectId,String supplierCode,String supplierName,String projectCode,String personCode,String personName,String idType,String idNumberMasked,String mobile,String jobTitle,String tradeType,String specialWorkType,LocalDate entryDate,LocalDate exitDate,String status,String statusReason,long createdBy,int version,LocalDateTime updatedAt){}
 public record PersonView(String id,String organizationId,String supplierId,String projectId,String supplierCode,String supplierName,String projectCode,String code,String name,IdType idType,String idNumberMasked,String mobile,String jobTitle,String tradeType,SpecialWorkType specialWorkType,LocalDate entryDate,LocalDate exitDate,PersonStatus status,String statusReason,int version,LocalDateTime updatedAt){}
 public record PersonPage(long total,int page,int size,List<PersonView> items){}
 public record SaveAsset(@NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_-]{1,63}") String code,@NotBlank @Size(max=150) String name,@NotBlank String supplierId,String projectId,@NotNull AssetType type,@Size(max=32) String plateNo,@Size(max=100) String serialNo,@Size(max=100) String brand,@Size(max=100) String model,LocalDate inspectionExpiryDate,String fileId,@NotNull @PositiveOrZero Integer version){}
 public record AssetStatusCommand(@NotNull AssetStatus status,@Size(max=500) String reason,@NotNull @PositiveOrZero Integer version){}
 public record AssetRow(long id,long organizationId,long supplierId,Long projectId,String supplierCode,String supplierName,String projectCode,String assetCode,String assetName,String assetType,String plateNo,String serialNo,String brand,String model,LocalDate inspectionExpiryDate,Long fileId,String status,String statusReason,long createdBy,int version,LocalDateTime updatedAt,LocalDateTime handedOverAt,String handoverRecipient,String handoverNote){}
 public record AssetView(String id,String organizationId,String supplierId,String projectId,String supplierCode,String supplierName,String projectCode,String code,String name,AssetType type,String plateNo,String serialNo,String brand,String model,LocalDate inspectionExpiryDate,String fileId,AssetStatus status,String statusReason,int version,LocalDateTime updatedAt,LocalDateTime handedOverAt,String handoverRecipient,String handoverNote){}
 public record AssetHandoverCommand(@NotBlank @Size(max=1000) String note,@NotBlank @Size(max=100) String recipient,@NotNull @PositiveOrZero Integer version){}
 public record AssetPage(long total,int page,int size,List<AssetView> items){}
}

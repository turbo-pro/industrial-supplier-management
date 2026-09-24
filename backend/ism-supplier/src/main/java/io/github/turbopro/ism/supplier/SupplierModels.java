package io.github.turbopro.ism.supplier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class SupplierModels {
    private SupplierModels() {}
    public enum Type { MANUFACTURER, TRADER, SERVICE_PROVIDER, CONTRACTOR, OTHER }
    public enum Status { DRAFT, ACTIVE, SUSPENDED, EXITED }
    public enum RiskLevel { LOW, MEDIUM, HIGH }
    public record ContactCommand(@NotBlank @Size(max=100) String name,@Size(max=100) String position,
        @Pattern(regexp="^$|^[0-9+() -]{6,32}$") String mobile,@Pattern(regexp="^$|^[0-9+() -]{6,32}$") String telephone,
        @Email @Size(max=200) String email,boolean primary,@PositiveOrZero int sortOrder) {}
    public record SaveSupplier(@NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_-]{1,63}") String code,@NotBlank @Size(max=200) String name,
        @Size(max=100) String shortName,@Pattern(regexp="^$|^[0-9A-Z]{18}$") String unifiedSocialCreditCode,@NotNull Type type,@Size(max=100) String industry,
        @Pattern(regexp="^[A-Z]{2}$") String countryCode,@Size(max=100) String province,@Size(max=100) String city,@Size(max=300) String address,
        @Size(max=100) String legalRepresentative,@DecimalMin("0.00") BigDecimal registeredCapital,@Pattern(regexp="^[A-Z]{3}$") String currency,
        @PastOrPresent LocalDate establishedDate,@Size(max=300) String website,@NotNull RiskLevel riskLevel,@Size(max=1000) String remark,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String organizationId,@Valid @Size(max=20) List<ContactCommand> contacts,@NotNull @PositiveOrZero Integer version) {
        public SaveSupplier { contacts=contacts==null?List.of():List.copyOf(contacts);countryCode=countryCode==null||countryCode.isBlank()?"CN":countryCode; }
    }
    public record ChangeStatus(@NotNull Status status,@Size(max=500) String reason,@NotNull @PositiveOrZero Integer version) {}
    public record SupplierRow(long id,long organizationId,String supplierCode,String supplierName,String shortName,String unifiedSocialCreditCode,String supplierType,String industry,String countryCode,String province,String city,String address,String legalRepresentative,BigDecimal registeredCapital,String currency,LocalDate establishedDate,String website,String source,String status,String riskLevel,String remark,long createdBy,int version,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record ContactRow(long id,String contactName,String positionName,String mobile,String telephone,String email,boolean primary,int sortOrder) {}
    public record ContactView(String id,String name,String position,String mobile,String telephone,String email,boolean primary,int sortOrder) {}
    public record SupplierView(String id,String organizationId,String code,String name,String shortName,String unifiedSocialCreditCode,Type type,String industry,String countryCode,String province,String city,String address,String legalRepresentative,BigDecimal registeredCapital,String currency,LocalDate establishedDate,String website,String source,Status status,RiskLevel riskLevel,String remark,int version,LocalDateTime createdAt,LocalDateTime updatedAt,List<ContactView> contacts) {}
    public record SupplierSummary(String id,String organizationId,String code,String name,String shortName,String unifiedSocialCreditCode,Type type,Status status,RiskLevel riskLevel,int version,LocalDateTime updatedAt) {}
    public record SupplierPage(long total,int page,int size,List<SupplierSummary> items) {}
}

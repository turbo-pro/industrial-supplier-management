package io.github.turbopro.ism.platform.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class TenantModels {
    private TenantModels() {}

    public record CreateTenant(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max=50) String timezone,
            @NotBlank @Size(max=20) String locale,
            @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String packageVersionId) {}

    public record PackageOption(String id,String packageCode,String packageName,int versionNo,String versionName){}
    public record PackageOptions(long total,int page,int size,List<PackageOption> items){}
    public record InitializeTenant(
            @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9._-]{2,99}") String adminUsername,
            @NotBlank @Size(max = 100) String adminDisplayName,
            @NotBlank @Size(min = 12, max = 128) String initialPassword,
            @Size(max = 200) String headquartersName,
            @NotBlank @Pattern(regexp="SINGLE_SITE|MULTI_SITE") String operationMode,
            @NotBlank @Size(max=20) String siteLabel,
            @NotBlank @Size(max=200) String siteName) {
        /** Legacy trusted callers retain the original unclassified structure; HTTP requires a mode. */
        public InitializeTenant(String username,String displayName,String password,String headquartersName){
            this(username,displayName,password,headquartersName,null,null,null);
        }
    }

    public record UpgradeToMultiSite(@NotBlank @Size(max=200) String headquartersName,
                                     @NotNull @jakarta.validation.constraints.PositiveOrZero Integer version){}

    public record UpdateTenant(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max=50) String timezone,
            @NotBlank @Size(max=20) String locale,
            int version) {}

    public record TenantRow(long id, String tenantCode, String tenantName, String status,
                            String initializationStatus, String timezone, String locale,
                            Long packageVersionId, int version, LocalDateTime initializedAt, String operationMode, String siteLabel, Long defaultSiteId) {}

    public record TenantView(String id, String code, String name, String status,
                             String initializationStatus, String timezone, String locale,
                             String packageVersionId, int version, LocalDateTime initializedAt, String operationMode, String siteLabel, String defaultSiteId) {}
}

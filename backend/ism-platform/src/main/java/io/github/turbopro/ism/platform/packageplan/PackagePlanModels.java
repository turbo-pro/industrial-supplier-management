package io.github.turbopro.ism.platform.packageplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.*;

public final class PackagePlanModels {
    private PackagePlanModels() {}

    public record CreatePackage(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String code,
                                @NotBlank @Size(max = 100) String name) {}
    public record UpdatePackage(@NotBlank @Size(max = 100) String name, @PositiveOrZero int version) {}
    public record ModuleGrant(@NotBlank String moduleCode, boolean enabled,
                              Map<String, @PositiveOrZero Long> quotas) {
        public ModuleGrant { quotas = quotas == null ? Map.of() : Map.copyOf(quotas); }
    }
    public record CreateVersion(@Positive int versionNo, @NotBlank @Size(max = 100) String name,
                                LocalDateTime effectiveFrom,
                                @NotEmpty List<@Valid ModuleGrant> modules) {}
    public record PublishCommand(@PositiveOrZero int version) {}
    public record AssignSubscription(@NotNull String packageVersionId,
                                     @NotNull LocalDateTime effectiveFrom,
                                     LocalDateTime effectiveTo,
                                     Map<String, Object> exceptions) {
        public AssignSubscription { exceptions = exceptions == null ? Map.of() : Map.copyOf(exceptions); }
    }
    public record PackageRow(long id, String packageCode, String packageName, String status, int version) {}
    public record VersionRow(long id, long packageId, int versionNo, String versionName,
                             LocalDateTime effectiveFrom, String status, int version) {}
    public record ModuleRow(long packageVersionId, long moduleId, String moduleCode,
                            String moduleName, boolean enabled, String quotaJson) {}
    public record PackageView(String id, String code, String name, String status, int version,
                              List<VersionView> versions) {}
    public record VersionView(String id, int versionNo, String name, LocalDateTime effectiveFrom,
                              String status, int version, List<ModuleGrant> modules) {}
    public record ValidationResult(boolean valid, List<String> errors) {}
    public record SubscriptionPreview(String tenantId, String currentPackageVersionId,
                                      String targetPackageVersionId, Set<String> removedModules,
                                      Map<String, QuotaChange> quotaChanges, List<String> recommendations) {}
    public record QuotaChange(long currentLimit, long targetLimit, long usedValue, boolean exceedsTarget) {}
    public record QuotaUsage(String quotaCode, long limit, long used, long remaining,
                             boolean warning, boolean hardLimited) {}
}

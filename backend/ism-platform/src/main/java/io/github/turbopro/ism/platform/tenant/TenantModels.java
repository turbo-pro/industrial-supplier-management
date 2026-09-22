package io.github.turbopro.ism.platform.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class TenantModels {
    private TenantModels() {}

    public record CreateTenant(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank String timezone,
            @NotBlank String locale,
            @NotNull String packageVersionId) {}

    public record InitializeTenant(
            @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9._-]{2,99}") String adminUsername,
            @NotBlank @Size(max = 100) String adminDisplayName,
            @NotBlank @Size(min = 12, max = 128) String initialPassword,
            @NotBlank @Size(max = 200) String headquartersName) {}

    public record UpdateTenant(
            @NotBlank @Size(max = 200) String name,
            @NotBlank String timezone,
            @NotBlank String locale,
            int version) {}

    public record TenantRow(long id, String tenantCode, String tenantName, String status,
                            String initializationStatus, String timezone, String locale,
                            Long packageVersionId, int version, LocalDateTime initializedAt) {}

    public record TenantView(String id, String code, String name, String status,
                             String initializationStatus, String timezone, String locale,
                             String packageVersionId, int version, LocalDateTime initializedAt) {}
}

package io.github.turbopro.ism.iam.console;

import jakarta.validation.constraints.*;

import java.util.Set;

public final class ConsoleUserModels {
    private ConsoleUserModels() {}

    public record Create(@NotBlank @Pattern(regexp="[a-zA-Z][a-zA-Z0-9._-]{2,99}") String username,
                         @NotBlank @Size(max=100) String displayName,
                         @NotBlank @Size(min=12,max=128) String initialPassword,
                         @NotBlank @Pattern(regexp="PLATFORM_ADMIN|PLATFORM_SUPPORT") String roleCode) {}
    public record ChangeStatus(@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,
                               @PositiveOrZero int version) {}
    public record Row(long id,String username,String displayName,String status,boolean forcePasswordChange,int version) {}
    public record View(String id,String username,String displayName,String status,boolean passwordChangeRequired,
                       int version,Set<String> roleCodes) {}
    public record UserRole(long userId,String roleCode) {}
}

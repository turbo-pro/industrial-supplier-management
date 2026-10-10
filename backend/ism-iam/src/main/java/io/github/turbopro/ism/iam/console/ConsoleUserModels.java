package io.github.turbopro.ism.iam.console;

import jakarta.validation.constraints.*;

import java.util.Set;
import java.time.LocalDateTime;

public final class ConsoleUserModels {
    private ConsoleUserModels() {}

    public record Create(@NotBlank @Pattern(regexp="[a-zA-Z][a-zA-Z0-9._-]{2,99}") String username,
                         @NotBlank @Size(max=100) String displayName,
                         @NotBlank @Size(min=12,max=128) String initialPassword,
                         @NotBlank @Pattern(regexp="PLATFORM_ADMIN|PLATFORM_SUPPORT") String roleCode) {}
    public record ChangeStatus(@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,
                               @PositiveOrZero int version) {}
    public record ChangeRole(@NotBlank @Pattern(regexp="PLATFORM_ADMIN|PLATFORM_SUPPORT") String roleCode,
                             @PositiveOrZero int version) {}
    public record RoleImpactPreview(String userId,String username,int version,Set<String> currentRoleCodes,
                                    String proposedRoleCode,Set<String> addedPermissions,Set<String> removedPermissions,
                                    boolean canApply,Set<String> blockers) {}
    public record ResetPassword(@NotBlank @Size(min=12,max=128) String temporaryPassword,
                                @PositiveOrZero int version) {}
    public record ChangeLoginLock(boolean locked,@NotBlank @Size(max=500) String reason,@PositiveOrZero int version) {}
    public record Row(long id,String username,String displayName,String status,boolean forcePasswordChange,
                      boolean manualLocked,String manualLockReason,LocalDateTime lockedUntil,int version) {}
    public record View(String id,String username,String displayName,String status,boolean passwordChangeRequired,
                       boolean manualLocked,String manualLockReason,LocalDateTime automaticLockedUntil,
                       int version,Set<String> roleCodes) {}
    public record UserRole(long userId,String roleCode) {}
}

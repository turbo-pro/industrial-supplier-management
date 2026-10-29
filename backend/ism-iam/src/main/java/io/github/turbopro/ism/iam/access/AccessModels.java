package io.github.turbopro.ism.iam.access;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

public final class AccessModels {
    private AccessModels() {}

    public record CreateRole(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String code,
                             @NotBlank @Size(max = 100) String name) {}
    public record UpdateRole(@NotBlank @Size(max = 100) String name, @PositiveOrZero int version) {}
    public record DataScopeGrant(@NotBlank String resourceCode, @NotBlank String scopeType,
                                 Set<String> organizationIds) {
        public DataScopeGrant { organizationIds = organizationIds == null ? Set.of() : Set.copyOf(organizationIds); }
    }
    public record GrantRole(@NotNull @PositiveOrZero Integer version, Set<String> permissionCodes, Set<String> menuCodes,
                            @NotEmpty List<@Valid DataScopeGrant> dataScopes) {
        public GrantRole {
            permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
            menuCodes = menuCodes == null ? Set.of() : Set.copyOf(menuCodes);
        }
    }
    public record CreateUser(@NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9._-]{2,99}") String username,
                             @NotBlank @Size(max = 100) String displayName,
                             @NotBlank @Size(min = 12, max = 128) String initialPassword,
                             @NotBlank String primaryOrganizationId,
                             Set<String> roleIds) {
        public CreateUser { roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds); }
    }
    public record AssignUserRoles(@NotEmpty Set<String> roleIds, @PositiveOrZero int version) {}
    public record RoleScopeGrant(String roleId,String resourceCode,String scopeType,Set<String> organizationIds) {}
    public record RoleImpactPreview(String userId,String username,int version,Set<String> currentRoleIds,
                                    Set<String> proposedRoleIds,Set<String> addedPermissions,Set<String> removedPermissions,
                                    Set<String> addedMenus,Set<String> removedMenus,
                                    List<RoleScopeGrant> currentScopeGrants,List<RoleScopeGrant> proposedScopeGrants,
                                    boolean canApply,Set<String> blockers) {}
    public record RoleScopeRow(long roleId,String resourceCode,String scopeType) {}
    public record RoleScopeOrganizationRow(long roleId,String resourceCode,long organizationId) {}
    public record ChangeUserStatus(@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,
                                   @PositiveOrZero int version) {}
    public record StatusTarget(@NotBlank String id,@PositiveOrZero int version) {}
    public record BatchChangeUserStatus(@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,
                                        @NotEmpty @Size(max=100) List<@Valid StatusTarget> targets) {}
    public record BatchAssignUserRoles(@NotEmpty @Size(max=100) Set<@NotBlank String> roleIds,
                                       @NotEmpty @Size(max=100) List<@NotNull @Valid StatusTarget> targets) {}
    public record BatchRolePreview(List<RoleImpactPreview> items,int changeCount,boolean canApply,Set<String> blockers) {}
    public record UserRoleChange(String userId,Set<String> previousRoleIds,Set<String> roleIds,int version) {}
    public record BatchRoleResult(List<UserView> users,List<UserRoleChange> changes,Set<String> unchangedUserIds) {}
    public record ResetUserPassword(@NotBlank @Size(min=12,max=128) String temporaryPassword,
                                    @PositiveOrZero int version) {}
    public record ChangeLoginLock(boolean locked, @NotBlank @Size(max=500) String reason,
                                  @PositiveOrZero int version) {}
    public record RoleRow(long id, String roleCode, String roleName, boolean builtIn, String status, int version) {}
    public record RoleView(String id, String code, String name, boolean builtIn, String status, int version) {}
    public record RoleGrantsView(RoleView role, Set<String> permissionCodes, Set<String> menuCodes,
                                 List<DataScopeGrant> dataScopes) {}
    public record RoleGrantCatalog(List<String> permissionCodes, List<String> menuCodes,
                                   List<String> resourceCodes, boolean canManage,
                                   List<PermissionOption> permissions, List<MenuOption> menus,
                                   List<OrganizationOption> organizations) {}
    public record PermissionOption(String code, String name, String type) {}
    public record MenuOption(String code, String name, String parentCode, String routePath) {}
    public record OrganizationOption(String id, String code, String name, String type) {}
    public record UserRow(long id, String username, String displayName, String status,
                          boolean forcePasswordChange, boolean manualLocked, String manualLockReason,
                          LocalDateTime lockedUntil, int version) {}
    public record UserView(String id, String username, String displayName, String status,
                           boolean passwordChangeRequired, boolean manualLocked, String manualLockReason,
                           LocalDateTime automaticLockedUntil, int version, Set<String> roleIds) {}
    public record UserRoleRow(long userId, long roleId) {}
}

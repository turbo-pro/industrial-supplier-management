package io.github.turbopro.ism.iam.access;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public final class AccessModels {
    private AccessModels() {}

    public record CreateRole(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String code,
                             @NotBlank @Size(max = 100) String name) {}
    public record UpdateRole(@NotBlank @Size(max = 100) String name, @PositiveOrZero int version) {}
    public record DataScopeGrant(@NotBlank String resourceCode, @NotBlank String scopeType,
                                 Set<String> organizationIds) {
        public DataScopeGrant { organizationIds = organizationIds == null ? Set.of() : Set.copyOf(organizationIds); }
    }
    public record GrantRole(Set<String> permissionCodes, Set<String> menuCodes,
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
    public record AssignUserRoles(@NotEmpty Set<String> roleIds) {}
    public record RoleRow(long id, String roleCode, String roleName, boolean builtIn, String status, int version) {}
    public record RoleView(String id, String code, String name, boolean builtIn, String status, int version) {}
    public record UserRow(long id, String username, String displayName, String status,
                          boolean forcePasswordChange, int version) {}
    public record UserView(String id, String username, String displayName, String status,
                           boolean passwordChangeRequired, int version) {}
}

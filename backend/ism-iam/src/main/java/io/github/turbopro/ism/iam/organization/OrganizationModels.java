package io.github.turbopro.ism.iam.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class OrganizationModels {
    private OrganizationModels() {}

    public record CreateOrganization(
            @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String parentId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,63}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank String type,
            @PositiveOrZero int sortOrder) {}

    public record UpdateOrganization(
            @NotBlank @Size(max = 200) String name,
            @PositiveOrZero int sortOrder,
            @PositiveOrZero int version) {}

    public record SwitchOrganization(
            @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String organizationId) {}

    public record OrganizationRow(long id, Long parentId, String organizationCode,
                                  String organizationName, String organizationType,
                                  String status, int sortOrder, int version) {}

    public record OrganizationNode(String id, String parentId, String code, String name,
                                   String type, String status, int sortOrder, int version,
                                   List<OrganizationNode> children) {}

    public record CurrentOrganization(String id, String code, String name, String type) {}
}

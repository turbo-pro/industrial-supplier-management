package io.github.turbopro.ism.common.infrastructure.authorization;

import java.util.Set;

public record DataScope(Type type, Set<Long> organizationIds, Set<Long> projectIds) {
    public DataScope {
        type = type == null ? Type.NONE : type;
        organizationIds = organizationIds == null ? Set.of() : Set.copyOf(organizationIds);
        projectIds = projectIds == null ? Set.of() : Set.copyOf(projectIds);
    }

    public static DataScope all() {
        return new DataScope(Type.TENANT_ALL, Set.of(), Set.of());
    }

    public static DataScope organizations(Set<Long> organizationIds) {
        return new DataScope(Type.ORGANIZATION_SET, organizationIds, Set.of());
    }

    public static DataScope projects(Set<Long> projectIds) {
        return new DataScope(Type.PROJECT_SET, Set.of(), projectIds);
    }

    public static DataScope owned() {
        return new DataScope(Type.OWNED, Set.of(), Set.of());
    }

    public static DataScope created() {
        return new DataScope(Type.CREATED, Set.of(), Set.of());
    }

    public static DataScope none() {
        return new DataScope(Type.NONE, Set.of(), Set.of());
    }

    public boolean allows(DataTarget target, long actorId) {
        return switch (type) {
            case TENANT_ALL -> true;
            case ORGANIZATION_SET -> target.organizationId() != null
                    && organizationIds.contains(target.organizationId());
            case PROJECT_SET -> target.projectId() != null && projectIds.contains(target.projectId());
            case OWNED -> target.ownerId() != null && target.ownerId() == actorId;
            case CREATED -> target.creatorId() != null && target.creatorId() == actorId;
            case NONE -> false;
        };
    }

    public enum Type {
        TENANT_ALL,
        ORGANIZATION_SET,
        PROJECT_SET,
        OWNED,
        CREATED,
        NONE
    }
}

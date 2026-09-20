package io.github.turbopro.ism.common.infrastructure.authorization;

public record DataTarget(Long organizationId, Long projectId, Long ownerId, Long creatorId) {
    public static DataTarget organization(long organizationId) {
        return new DataTarget(organizationId, null, null, null);
    }
}

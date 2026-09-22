package io.github.turbopro.ism.iam.authorization;

import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationGrantLoader;
import io.github.turbopro.ism.common.infrastructure.authorization.DataScope;
import io.github.turbopro.ism.common.infrastructure.authorization.PermissionSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DatabaseAuthorizationGrantLoader implements AuthorizationGrantLoader {
    private final TenantAuthorizationMapper mapper;

    public DatabaseAuthorizationGrantLoader(TenantAuthorizationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PermissionSnapshot load(long tenantId, long actorId) {
        Set<String> actions = Set.copyOf(mapper.actionPermissions(tenantId, actorId));
        Set<String> fields = Set.copyOf(mapper.fieldPermissions(tenantId, actorId));
        List<TenantAuthorizationMapper.DataScopeRow> rows = mapper.dataScopes(tenantId, actorId);
        Map<RoleResource, Set<Long>> organizations = new HashMap<>();
        for (var row : mapper.organizationScopes(tenantId, actorId)) {
            organizations.computeIfAbsent(new RoleResource(row.roleId(), row.resourceCode()), ignored -> new HashSet<>())
                    .add(row.organizationId());
        }
        Map<String, List<DataScope>> candidates = new HashMap<>();
        for (var row : rows) {
            DataScope scope = toScope(row.scopeType(), organizations.getOrDefault(
                    new RoleResource(row.roleId(), row.resourceCode()), Set.of()));
            candidates.computeIfAbsent(row.resourceCode(), ignored -> new ArrayList<>()).add(scope);
        }
        Map<String, DataScope> merged = new HashMap<>();
        candidates.forEach((resource, scopes) -> merged.put(resource, merge(scopes)));
        return new PermissionSnapshot(actions, merged, fields);
    }

    private DataScope toScope(String type, Set<Long> organizations) {
        return switch (DataScope.Type.valueOf(type)) {
            case TENANT_ALL -> DataScope.all();
            case ORGANIZATION_SET -> DataScope.organizations(organizations);
            case PROJECT_SET -> DataScope.projects(Set.of());
            case OWNED -> DataScope.owned();
            case CREATED -> DataScope.created();
            case NONE -> DataScope.none();
        };
    }

    private DataScope merge(List<DataScope> scopes) {
        if (scopes.stream().anyMatch(scope -> scope.type() == DataScope.Type.TENANT_ALL)) return DataScope.all();
        Set<Long> organizations = new HashSet<>();
        scopes.stream().filter(scope -> scope.type() == DataScope.Type.ORGANIZATION_SET)
                .forEach(scope -> organizations.addAll(scope.organizationIds()));
        if (!organizations.isEmpty()) return DataScope.organizations(organizations);
        if (scopes.stream().anyMatch(scope -> scope.type() == DataScope.Type.PROJECT_SET)) {
            Set<Long> projects = new HashSet<>();
            scopes.forEach(scope -> projects.addAll(scope.projectIds()));
            return DataScope.projects(projects);
        }
        if (scopes.stream().anyMatch(scope -> scope.type() == DataScope.Type.OWNED)) return DataScope.owned();
        if (scopes.stream().anyMatch(scope -> scope.type() == DataScope.Type.CREATED)) return DataScope.created();
        return DataScope.none();
    }

    private record RoleResource(long roleId, String resourceCode) {}
}

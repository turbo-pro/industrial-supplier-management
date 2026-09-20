package io.github.turbopro.ism.common.infrastructure.authorization;

import java.util.Map;
import java.util.Set;

public record PermissionSnapshot(
        Set<String> actions,
        Map<String, DataScope> dataScopes,
        Set<String> fields) {

    public PermissionSnapshot {
        actions = actions == null ? Set.of() : Set.copyOf(actions);
        dataScopes = dataScopes == null ? Map.of() : Map.copyOf(dataScopes);
        fields = fields == null ? Set.of() : Set.copyOf(fields);
    }

    public static PermissionSnapshot denyAll() {
        return new PermissionSnapshot(Set.of(), Map.of(), Set.of());
    }

    public boolean hasAction(String permission) {
        return actions.contains(permission);
    }

    public boolean hasField(String permission) {
        return fields.contains(permission);
    }

    public DataScope dataScope(String resource) {
        return dataScopes.getOrDefault(resource, DataScope.none());
    }
}

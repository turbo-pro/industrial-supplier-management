package io.github.turbopro.ism.common.api.authorization;

import java.util.List;
import java.util.Set;

/** Global active capability metadata only; never tenant users, role grants or business data. */
public interface ModuleCapabilityCatalog {
    Impact compare(Set<String> currentModules, Set<String> targetModules);
    record Capability(String kind, String code, String name, String moduleCode, String change) {}
    record Counts(long menus, long actions, long fields) {}
    record Impact(Counts removed, Counts added, long total, int limit, boolean truncated,
                  List<Capability> items) {}
}

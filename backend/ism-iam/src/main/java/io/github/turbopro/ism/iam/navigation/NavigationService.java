package io.github.turbopro.ism.iam.navigation;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.iam.authorization.GrantEntitlements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NavigationService {
    private final NavigationMapper mapper;
    private final GrantEntitlements entitlements;

    public NavigationService(NavigationMapper mapper, GrantEntitlements entitlements) {
        this.mapper = mapper;
        this.entitlements = entitlements;
    }

    public List<NavigationModels.MenuNode> currentMenus() {
        TenantContext.Identity identity = TenantContext.require();
        Map<Long, List<NavigationModels.MenuRow>> byParent = new HashMap<>();
        for (var row : mapper.menus(identity.tenantId(), identity.actorId(), entitlements.allowedModules(identity.tenantId()))) {
            byParent.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row);
        }
        return nodes(byParent, null);
    }

    private List<NavigationModels.MenuNode> nodes(Map<Long, List<NavigationModels.MenuRow>> rows, Long parentId) {
        return rows.getOrDefault(parentId, List.of()).stream()
                .map(row -> new NavigationModels.MenuNode(Long.toString(row.id()), row.menuCode(), row.menuName(),
                        row.routePath(), row.componentKey(), row.icon(), nodes(rows, row.id())))
                .filter(node -> !"Layout".equals(node.component()) || !node.children().isEmpty())
                .toList();
    }
}

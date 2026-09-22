package io.github.turbopro.ism.iam.navigation;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NavigationService {
    private final NavigationMapper mapper;

    public NavigationService(NavigationMapper mapper) {
        this.mapper = mapper;
    }

    public List<NavigationModels.MenuNode> currentMenus() {
        TenantContext.Identity identity = TenantContext.require();
        Map<Long, List<NavigationModels.MenuRow>> byParent = new HashMap<>();
        for (var row : mapper.menus(identity.tenantId(), identity.actorId())) {
            byParent.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row);
        }
        return nodes(byParent, null);
    }

    private List<NavigationModels.MenuNode> nodes(Map<Long, List<NavigationModels.MenuRow>> rows, Long parentId) {
        return rows.getOrDefault(parentId, List.of()).stream()
                .map(row -> new NavigationModels.MenuNode(Long.toString(row.id()), row.menuCode(), row.menuName(),
                        row.routePath(), row.componentKey(), row.icon(), nodes(rows, row.id())))
                .toList();
    }
}

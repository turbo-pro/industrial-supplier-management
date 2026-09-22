package io.github.turbopro.ism.iam.navigation;

import java.util.List;

public final class NavigationModels {
    private NavigationModels() {}

    public record MenuRow(long id, Long parentId, String menuCode, String menuName,
                          String routePath, String componentKey, String icon, int sortOrder) {}
    public record MenuNode(String id, String code, String name, String route, String component,
                           String icon, List<MenuNode> children) {}
}

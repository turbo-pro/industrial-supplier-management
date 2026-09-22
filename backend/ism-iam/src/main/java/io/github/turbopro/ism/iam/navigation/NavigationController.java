package io.github.turbopro.ism.iam.navigation;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/navigation")
public class NavigationController {
    private final NavigationService service;
    private final ApiResponseFactory responses;

    public NavigationController(NavigationService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping("/menus")
    @RequiresPermission("iam:menu:view")
    ApiResponse<List<NavigationModels.MenuNode>> menus() {
        return responses.success(service.currentMenus());
    }
}

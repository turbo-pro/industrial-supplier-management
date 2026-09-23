package io.github.turbopro.ism.integration.search;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService service;private final ApiResponseFactory responses;
    public SearchController(SearchService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @PostMapping @RequiresPermission("search:global:use") ApiResponse<SearchModels.SearchResult> search(@Valid @RequestBody SearchModels.SearchRequest request){return responses.success(service.search(request));}
    @GetMapping("/saved") @RequiresPermission("search:saved:manage") ApiResponse<List<SearchModels.SavedView>> saved(){return responses.success(service.saved());}
    @PostMapping("/saved") @RequiresPermission("search:saved:manage") ApiResponse<SearchModels.SavedView> create(@Valid @RequestBody SearchModels.SaveSearch command){return responses.success(service.create(command));}
    @PutMapping("/saved/{id}") @RequiresPermission("search:saved:manage") ApiResponse<SearchModels.SavedView> update(@PathVariable long id,@Valid @RequestBody SearchModels.SaveSearch command){return responses.success(service.update(id,command));}
    @DeleteMapping("/saved/{id}") @RequiresPermission("search:saved:manage") ApiResponse<Void> delete(@PathVariable long id,@RequestParam int version){service.delete(id,version);return responses.success(null);}
}

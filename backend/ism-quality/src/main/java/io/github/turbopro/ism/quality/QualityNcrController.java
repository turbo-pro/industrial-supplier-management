package io.github.turbopro.ism.quality;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/quality/nonconformances")
public class QualityNcrController {
    private final QualityNcrService service;
    private final ApiResponseFactory responses;
    public QualityNcrController(QualityNcrService service, ApiResponseFactory responses) {
        this.service = service; this.responses = responses;
    }
    @GetMapping @RequiresPermission("quality:ncr:view")
    public ApiResponse<QualityNcrModels.Page> list(@RequestParam(defaultValue = "") @Size(max = 100) String keyword,
                                                    @RequestParam(defaultValue = "") String status,
                                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return responses.success(service.list(keyword, status, page, size));
    }
    @GetMapping("/{id}") @RequiresPermission("quality:ncr:view")
    public ApiResponse<QualityNcrModels.View> get(@PathVariable long id) { return responses.success(service.get(id)); }
    @GetMapping("/{id}/events") @RequiresPermission("quality:ncr:view")
    public ApiResponse<List<QualityNcrModels.Event>> events(@PathVariable long id) { return responses.success(service.events(id)); }
    @PostMapping @RequiresPermission("quality:ncr:create")
    public ApiResponse<QualityNcrModels.View> create(@Valid @RequestBody QualityNcrModels.Create command) {
        return responses.success(service.create(command));
    }
    @PostMapping("/{id}/rectification") @RequiresPermission("quality:ncr:rectify")
    public ApiResponse<QualityNcrModels.View> rectify(@PathVariable long id, @Valid @RequestBody QualityNcrModels.Rectify command) {
        return responses.success(service.rectify(id, command));
    }
    @PostMapping("/{id}/verification") @RequiresPermission("quality:ncr:verify")
    public ApiResponse<QualityNcrModels.View> verify(@PathVariable long id, @Valid @RequestBody QualityNcrModels.Verify command) {
        return responses.success(service.verify(id, command));
    }
}

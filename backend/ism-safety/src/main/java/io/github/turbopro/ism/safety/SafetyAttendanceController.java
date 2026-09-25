package io.github.turbopro.ism.safety;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/safety/attendance")
public class SafetyAttendanceController {
    private final SafetyAttendanceService service;
    private final ApiResponseFactory responses;
    public SafetyAttendanceController(SafetyAttendanceService service, ApiResponseFactory responses) {
        this.service = service; this.responses = responses;
    }
    @GetMapping
    @RequiresPermission("safety:attendance:view")
    public ApiResponse<SafetyAttendanceModels.Page> list(@RequestParam(defaultValue = "") String personId,
                                                           @RequestParam(defaultValue = "false") boolean openOnly,
                                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return responses.success(service.list(personId, openOnly, page, size));
    }
    @PostMapping("/check-in")
    @RequiresPermission("safety:attendance:checkin")
    public ApiResponse<SafetyAttendanceModels.View> checkIn(@Valid @RequestBody SafetyAttendanceModels.CheckIn command) {
        return responses.success(service.checkIn(command));
    }
    @PostMapping("/{id}/check-out")
    @RequiresPermission("safety:attendance:checkout")
    public ApiResponse<SafetyAttendanceModels.View> checkOut(@PathVariable long id,
                                                               @Valid @RequestBody SafetyAttendanceModels.CheckOut command) {
        return responses.success(service.checkOut(id, command));
    }
}

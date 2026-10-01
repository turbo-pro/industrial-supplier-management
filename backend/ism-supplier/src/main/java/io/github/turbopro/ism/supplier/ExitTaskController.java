package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/supplier-exit-tasks")
public class ExitTaskController {
    private final ExitTaskService service;private final ApiResponseFactory responses;
    public ExitTaskController(ExitTaskService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping("/monitor") @RequiresPermission("supplier:exit:monitor")
    public ApiResponse<ExitTaskModels.MonitorPage> monitor(@RequestParam(required=false) @Size(max=100) String keyword,
        @RequestParam(required=false) String code,@RequestParam(defaultValue="false") boolean unassignedOnly,
        @RequestParam(defaultValue="false") boolean overdueOnly,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.monitor(keyword,code,unassignedOnly,overdueOnly,page,size));}
    @GetMapping("/mine") @RequiresPermission("supplier:exit:view")
    public ApiResponse<ExitTaskModels.Page> mine(@RequestParam(required=false) @Size(max=100) String keyword,
        @RequestParam(required=false) String code,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.mine(keyword,code,page,size));}
    @GetMapping("/access/mine") @RequiresPermission("supplier:exit:view")
    public ApiResponse<ExitTaskModels.AccessPage> accessMine(@RequestParam(required=false) @Size(max=100) String keyword,
        @RequestParam(required=false) String channel,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.accessMine(keyword,channel,page,size));}
}

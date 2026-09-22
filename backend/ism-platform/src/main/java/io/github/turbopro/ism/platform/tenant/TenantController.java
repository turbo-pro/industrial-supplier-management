package io.github.turbopro.ism.platform.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/console/tenants")
public class TenantController {
    private final TenantService service;
    private final ApiResponseFactory responses;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final ObjectMapper json;

    public TenantController(TenantService service, ApiResponseFactory responses,
                            IdempotencyService idempotency, AuditService audit, ObjectMapper json) {
        this.service = service;
        this.responses = responses;
        this.idempotency = idempotency;
        this.audit = audit;
        this.json = json;
    }

    @GetMapping
    @RequiresPermission("platform:tenant:view")
    ApiResponse<List<TenantModels.TenantView>> list() {
        return responses.success(service.list());
    }

    @GetMapping("/{id}")
    @RequiresPermission("platform:tenant:view")
    ApiResponse<TenantModels.TenantView> get(@PathVariable long id) {
        return responses.success(service.get(id));
    }

    @PostMapping
    @RequiresPermission("platform:tenant:create")
    ApiResponse<TenantModels.TenantView> create(@RequestHeader("Idempotency-Key") String key,
                                                @Valid @RequestBody TenantModels.CreateTenant command) {
        return responses.success(once("TENANT_CREATE", key, command, () -> {
            TenantModels.TenantView result = service.create(command);
            audit("TENANT_CREATE", result, Map.of(), Map.of("status", result.status()));
            return result;
        }));
    }

    @PutMapping("/{id}")
    @RequiresPermission("platform:tenant:update")
    ApiResponse<TenantModels.TenantView> update(@PathVariable long id,
                                                @RequestHeader("Idempotency-Key") String key,
                                                @Valid @RequestBody TenantModels.UpdateTenant command) {
        return responses.success(once("TENANT_UPDATE:" + id, key, command,
                () -> service.update(id, command)));
    }

    @PostMapping("/{id}/initialize")
    @RequiresPermission("platform:tenant:initialize")
    ApiResponse<TenantModels.TenantView> initialize(@PathVariable long id,
                                                    @RequestHeader("Idempotency-Key") String key,
                                                    @Valid @RequestBody TenantModels.InitializeTenant command) {
        return responses.success(once("TENANT_INITIALIZE:" + id, key, command, () -> {
            TenantModels.TenantView result = service.initialize(id, command);
            audit("TENANT_INITIALIZE", result, Map.of("status", "PROVISIONING"),
                    Map.of("status", result.status()));
            return result;
        }));
    }

    @PostMapping("/{id}/suspend")
    @RequiresPermission("platform:tenant:suspend")
    ApiResponse<TenantModels.TenantView> suspend(@PathVariable long id,
                                                 @RequestHeader("Idempotency-Key") String key) {
        return responses.success(transition(id, key, "TENANT_SUSPEND", service::suspend));
    }

    @PostMapping("/{id}/resume")
    @RequiresPermission("platform:tenant:resume")
    ApiResponse<TenantModels.TenantView> resume(@PathVariable long id,
                                                @RequestHeader("Idempotency-Key") String key) {
        return responses.success(transition(id, key, "TENANT_RESUME", service::resume));
    }

    @PostMapping("/{id}/cancel")
    @RequiresPermission("platform:tenant:cancel")
    ApiResponse<TenantModels.TenantView> cancel(@PathVariable long id,
                                                @RequestHeader("Idempotency-Key") String key) {
        return responses.success(transition(id, key, "TENANT_CANCEL", service::cancel));
    }

    private TenantModels.TenantView transition(long id, String key, String action,
                                                java.util.function.LongFunction<TenantModels.TenantView> change) {
        return once(action + ":" + id, key, Map.of("tenantId", id), () -> {
            TenantModels.TenantView before = service.get(id);
            TenantModels.TenantView result = change.apply(id);
            audit(action, result, Map.of("status", before.status()), Map.of("status", result.status()));
            return result;
        });
    }

    private void audit(String action, TenantModels.TenantView tenant,
                       Map<String, Object> before, Map<String, Object> after) {
        audit.append(new AuditService.AuditCommand(action, "TENANT", Long.parseLong(tenant.id()),
                null, before, after, null, null));
    }

    private TenantModels.TenantView once(String operation, String key, Object request,
                                         Supplier<TenantModels.TenantView> action) {
        String value = idempotency.execute(operation, key, write(request), Duration.ofHours(24),
                () -> write(action.get()));
        try {
            return json.readValue(value, TenantModels.TenantView.class);
        } catch (Exception exception) {
            throw new IllegalStateException("幂等响应无法解析", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("请求无法序列化", exception);
        }
    }
}

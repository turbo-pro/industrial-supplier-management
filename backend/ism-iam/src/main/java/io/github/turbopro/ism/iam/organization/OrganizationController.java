package io.github.turbopro.ism.iam.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationService service;
    private final ApiResponseFactory responses;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final ObjectMapper json;

    public OrganizationController(OrganizationService service, ApiResponseFactory responses,
                                  IdempotencyService idempotency, AuditService audit, ObjectMapper json) {
        this.service = service;
        this.responses = responses;
        this.idempotency = idempotency;
        this.audit = audit;
        this.json = json;
    }

    @GetMapping("/tree")
    @RequiresPermission("iam:organization:view")
    ApiResponse<List<OrganizationModels.OrganizationNode>> tree() {
        return responses.success(service.tree());
    }

    @PostMapping
    @RequiresPermission("iam:organization:manage")
    ApiResponse<OrganizationModels.OrganizationNode> create(@RequestHeader("Idempotency-Key") String key,
                                                             @Valid @RequestBody OrganizationModels.CreateOrganization command) {
        return responses.success(once("ORGANIZATION_CREATE", key, command, OrganizationModels.OrganizationNode.class,
                () -> {
                    var result = service.create(command);
                    append("ORGANIZATION_CREATE", result.id(), Map.of(), Map.of("type", result.type()));
                    return result;
                }));
    }

    @PutMapping("/{id}")
    @RequiresPermission("iam:organization:manage")
    ApiResponse<OrganizationModels.OrganizationNode> update(@PathVariable long id,
                                                             @RequestHeader("Idempotency-Key") String key,
                                                             @Valid @RequestBody OrganizationModels.UpdateOrganization command) {
        return responses.success(once("ORGANIZATION_UPDATE:" + id, key, command,
                OrganizationModels.OrganizationNode.class, () -> service.update(id, command)));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("iam:organization:manage")
    ApiResponse<Void> disable(@PathVariable long id, @RequestParam int version,
                              @RequestHeader("Idempotency-Key") String key) {
        once("ORGANIZATION_DISABLE:" + id, key, Map.of("version", version), VoidResult.class, () -> {
            service.disable(id, version);
            append("ORGANIZATION_DISABLE", Long.toString(id), Map.of("status", "ACTIVE"),
                    Map.of("status", "DISABLED"));
            return new VoidResult(true);
        });
        return responses.success(null);
    }

    @GetMapping("/current")
    ApiResponse<OrganizationModels.CurrentOrganization> current() {
        return responses.success(service.current());
    }

    @PostMapping("/switch")
    ApiResponse<OrganizationModels.CurrentOrganization> switchOrganization(
            @Valid @RequestBody OrganizationModels.SwitchOrganization command) {
        return responses.success(service.switchTo(command.organizationId()));
    }

    private void append(String action, String id, Object before, Object after) {
        audit.append(new AuditService.AuditCommand(action, "ORGANIZATION", Long.parseLong(id), null,
                before, after, null, null));
    }

    private <T> T once(String operation, String key, Object request, Class<T> type, Supplier<T> action) {
        String value = idempotency.execute(operation, key, write(request), Duration.ofHours(24),
                () -> write(action.get()));
        try {
            return json.readValue(value, type);
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

    private record VoidResult(boolean completed) {}
}

package io.github.turbopro.ism.iam.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.operation.IdempotencyService;
import io.github.turbopro.ism.operation.AuditService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/access")
public class AccessController {
    private final AccessService service; private final ApiResponseFactory responses;
    private final IdempotencyService idempotency; private final AuditService audit; private final ObjectMapper json;
    public AccessController(AccessService service,ApiResponseFactory responses,IdempotencyService idempotency,AuditService audit,ObjectMapper json){this.service=service;this.responses=responses;this.idempotency=idempotency;this.audit=audit;this.json=json;}
    @GetMapping("/roles") @RequiresPermission("iam:role:view")
    ApiResponse<List<AccessModels.RoleView>> roles(){return responses.success(service.roles());}
    @PostMapping("/roles") @RequiresPermission("iam:role:manage")
    ApiResponse<AccessModels.RoleView> createRole(@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody AccessModels.CreateRole command){return responses.success(once("ROLE_CREATE",key,command,AccessModels.RoleView.class,()->{var r=service.createRole(command);append("ROLE_CREATE","ROLE",r.id(),Map.of(),Map.of("code",r.code()));return r;}));}
    @PutMapping("/roles/{id}") @RequiresPermission("iam:role:manage")
    ApiResponse<AccessModels.RoleView> updateRole(@PathVariable long id,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody AccessModels.UpdateRole command){return responses.success(once("ROLE_UPDATE:"+id,key,command,AccessModels.RoleView.class,()->{var r=service.updateRole(id,command);append("ROLE_UPDATE","ROLE",r.id(),Map.of(),Map.of("name",r.name()));return r;}));}
    @PutMapping("/roles/{id}/grants") @RequiresPermission("iam:role:manage")
    ApiResponse<AccessModels.RoleView> grantRole(@PathVariable long id,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody AccessModels.GrantRole command){return responses.success(once("ROLE_GRANT:"+id,key,command,AccessModels.RoleView.class,()->{var r=service.grantRole(id,command);append("ROLE_GRANT","ROLE",r.id(),Map.of(),Map.of("permissionCount",command.permissionCodes().size(),"menuCount",command.menuCodes().size()));return r;}));}
    @GetMapping("/users") @RequiresPermission("iam:user:view")
    ApiResponse<List<AccessModels.UserView>> users(){return responses.success(service.users());}
    @PostMapping("/users") @RequiresPermission("iam:user:manage")
    ApiResponse<AccessModels.UserView> createUser(@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody AccessModels.CreateUser command){return responses.success(once("USER_CREATE",key,command,AccessModels.UserView.class,()->{var r=service.createUser(command);append("USER_CREATE","USER",r.id(),Map.of(),Map.of("username",r.username()));return r;}));}
    @PutMapping("/users/{id}/roles") @RequiresPermission("iam:user:manage")
    ApiResponse<Void> assignRoles(@PathVariable long id,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody AccessModels.AssignUserRoles command){once("USER_ROLE_ASSIGN:"+id,key,command,VoidResult.class,()->{service.assignUserRoles(id,command);append("USER_ROLE_ASSIGN","USER",Long.toString(id),Map.of(),Map.of("roleCount",command.roleIds().size()));return new VoidResult(true);});return responses.success(null);}
    private void append(String action,String type,String id,Object before,Object after){audit.append(new AuditService.AuditCommand(action,type,Long.parseLong(id),null,before,after,null,null));}
    private <T>T once(String operation,String key,Object request,Class<T> type,Supplier<T> action){String value=idempotency.execute(operation,key,write(request),Duration.ofHours(24),()->write(action.get()));try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("幂等响应无法解析",e);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("请求无法序列化",e);}}
    private record VoidResult(boolean completed){}
}

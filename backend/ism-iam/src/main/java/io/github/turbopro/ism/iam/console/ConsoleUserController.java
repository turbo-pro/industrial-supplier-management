package io.github.turbopro.ism.iam.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/console/users")
public class ConsoleUserController {
    private final ConsoleUserService service; private final ApiResponseFactory responses;
    private final IdempotencyService idempotency; private final AuditService audit; private final ObjectMapper json;
    public ConsoleUserController(ConsoleUserService service,ApiResponseFactory responses,IdempotencyService idempotency,
                                 AuditService audit,ObjectMapper json){this.service=service;this.responses=responses;this.idempotency=idempotency;this.audit=audit;this.json=json;}

    @GetMapping @RequiresPermission("platform:user:view")
    ApiResponse<List<ConsoleUserModels.View>> users(){return responses.success(service.users());}

    @PostMapping @RequiresPermission("platform:user:manage")
    ApiResponse<ConsoleUserModels.View> create(@RequestHeader("Idempotency-Key") String key,
                                                @Valid @RequestBody ConsoleUserModels.Create command){
        return responses.success(once("PLATFORM_USER_CREATE",key,command,()->{
            var result=service.create(command);
            audit.append(new AuditService.AuditCommand("PLATFORM_USER_CREATE","PLATFORM_USER",Long.parseLong(result.id()),null,
                    Map.of(),Map.of("username",result.username(),"roleCode",command.roleCode()),null,null));
            return result;
        }));
    }

    @PutMapping("/{id}/status") @RequiresPermission("platform:user:manage")
    ApiResponse<ConsoleUserModels.View> changeStatus(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ConsoleUserModels.ChangeStatus command,@AuthenticationPrincipal ConsolePrincipal principal){
        return responses.success(once("PLATFORM_USER_STATUS:"+id,key,command,()->{
            var result=service.changeStatus(id,command,principal.userId());
            audit.append(new AuditService.AuditCommand("PLATFORM_USER_STATUS","PLATFORM_USER",id,null,
                    Map.of(),Map.of("status",result.status(),"version",result.version()),null,null));
            return result;
        }));
    }

    @PutMapping("/{id}/role") @RequiresPermission("platform:user:manage")
    ApiResponse<ConsoleUserModels.View> changeRole(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ConsoleUserModels.ChangeRole command,@AuthenticationPrincipal ConsolePrincipal principal){
        return responses.success(once("PLATFORM_USER_ROLE:"+id,key,command,()->{
            var result=service.changeRole(id,command,principal.userId());
            audit.append(new AuditService.AuditCommand("PLATFORM_USER_ROLE","PLATFORM_USER",id,null,
                    Map.of(),Map.of("roleCodes",result.roleCodes(),"version",result.version()),null,null));
            return result;
        }));
    }

    @PutMapping("/{id}/password-reset") @RequiresPermission("platform:user:manage")
    ApiResponse<ConsoleUserModels.View> resetPassword(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ConsoleUserModels.ResetPassword command,@AuthenticationPrincipal ConsolePrincipal principal){
        return responses.success(once("PLATFORM_USER_PASSWORD_RESET:"+id,key,command,()->{
            var result=service.resetPassword(id,command,principal.userId());
            audit.append(new AuditService.AuditCommand("PLATFORM_USER_PASSWORD_RESET","PLATFORM_USER",id,null,
                    Map.of(),Map.of("forcePasswordChange",result.passwordChangeRequired(),"version",result.version()),null,null));
            return result;
        }));
    }

    @PutMapping("/{id}/login-lock") @RequiresPermission("platform:user:manage")
    ApiResponse<ConsoleUserModels.View> changeLoginLock(@PathVariable long id,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ConsoleUserModels.ChangeLoginLock command,@AuthenticationPrincipal ConsolePrincipal principal){
        return responses.success(once("PLATFORM_USER_LOGIN_LOCK:"+id,key,command,()->{
            var result=service.changeLoginLock(id,command,principal.userId());
            audit.append(new AuditService.AuditCommand("PLATFORM_USER_LOGIN_LOCK","PLATFORM_USER",id,null,
                    Map.of(),Map.of("locked",result.manualLocked(),"reason",command.reason().trim(),"version",result.version()),null,null));
            return result;
        }));
    }

    private ConsoleUserModels.View once(String operation,String key,Object request,Supplier<ConsoleUserModels.View> action){
        String value=idempotency.execute(operation,key,write(request),Duration.ofHours(24),()->write(action.get()));
        try{return json.readValue(value,ConsoleUserModels.View.class);}catch(Exception e){throw new IllegalStateException("幂等响应无法解析",e);}
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("请求无法序列化",e);}}
}

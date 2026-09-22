package io.github.turbopro.ism.iam.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/configuration")
public class ConfigurationController {
    private final ConfigurationService service;private final ApiResponseFactory responses;private final IdempotencyService idempotency;private final AuditService audit;private final ObjectMapper json;
    public ConfigurationController(ConfigurationService service,ApiResponseFactory responses,IdempotencyService idempotency,AuditService audit,ObjectMapper json){this.service=service;this.responses=responses;this.idempotency=idempotency;this.audit=audit;this.json=json;}
    @GetMapping("/dictionaries") @RequiresPermission("system:dictionary:view") ApiResponse<List<ConfigurationModels.DictionaryTypeView>> dictionaries(){return responses.success(service.dictionaries());}
    @GetMapping("/dictionaries/{typeCode}") @RequiresPermission("system:dictionary:view") ApiResponse<ConfigurationModels.DictionaryTypeView> dictionary(@PathVariable String typeCode){return responses.success(service.dictionary(typeCode));}
    @PutMapping("/dictionaries/{typeCode}/items/{itemCode}") @RequiresPermission("system:dictionary:manage") ApiResponse<ConfigurationModels.DictionaryTypeView> upsert(@PathVariable String typeCode,@PathVariable String itemCode,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody ConfigurationModels.UpsertDictionaryItem command){if(!itemCode.equals(command.code()))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"路径与条目代码不一致");return responses.success(once("DICTIONARY_ITEM_UPSERT:"+typeCode+":"+itemCode,key,command,ConfigurationModels.DictionaryTypeView.class,()->{var result=service.upsertItem(typeCode,command);append("DICTIONARY_ITEM_UPSERT",itemCode,Map.of("typeCode",typeCode));return result;}));}
    @DeleteMapping("/dictionaries/{typeCode}/items/{itemCode}") @RequiresPermission("system:dictionary:manage") ApiResponse<Void> remove(@PathVariable String typeCode,@PathVariable String itemCode,@RequestParam int version,@RequestHeader("Idempotency-Key")String key){once("DICTIONARY_ITEM_DELETE:"+typeCode+":"+itemCode,key,Map.of("version",version),Done.class,()->{service.removeItem(typeCode,itemCode,version);append("DICTIONARY_ITEM_DELETE",itemCode,Map.of("typeCode",typeCode));return new Done(true);});return responses.success(null);}
    @GetMapping("/settings") @RequiresPermission("system:setting:view") ApiResponse<List<ConfigurationModels.SettingView>> settings(){return responses.success(service.settings());}
    @PutMapping("/settings/{settingKey}") @RequiresPermission("system:setting:manage") ApiResponse<ConfigurationModels.SettingView> setting(@PathVariable String settingKey,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody ConfigurationModels.UpdateSetting command){return responses.success(once("SETTING_UPDATE:"+settingKey,key,command,ConfigurationModels.SettingView.class,()->{var result=service.updateSetting(settingKey,command);append("SETTING_UPDATE",settingKey,Map.of("valueType",result.valueType()));return result;}));}
    private void append(String action,String key,Object after){audit.append(new AuditService.AuditCommand(action,"CONFIGURATION",Integer.toUnsignedLong(key.hashCode()),null,Map.of(),after,null,null));}
    private <T>T once(String operation,String key,Object request,Class<T> type,Supplier<T> action){String value=idempotency.execute(operation,key,write(request),Duration.ofHours(24),()->write(action.get()));try{return json.readValue(value,type);}catch(Exception exception){throw new IllegalStateException("幂等响应无法解析",exception);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception exception){throw new IllegalArgumentException("请求无法序列化",exception);}}
    private record Done(boolean completed){}
}

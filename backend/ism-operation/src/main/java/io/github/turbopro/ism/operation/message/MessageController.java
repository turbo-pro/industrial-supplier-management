package io.github.turbopro.ism.operation.message;
import io.github.turbopro.ism.common.api.ApiResponse;import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;import jakarta.validation.Valid;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/messages") public class MessageController{private final MessageService service;private final ApiResponseFactory responses;public MessageController(MessageService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
 @GetMapping("/templates")@RequiresPermission("message:template:view")ApiResponse<List<MessageModels.TemplateView>>templates(){return responses.success(service.templates());}
 @PostMapping("/templates")@RequiresPermission("message:template:manage")ApiResponse<MessageModels.TemplateView>create(@Valid@RequestBody MessageModels.SaveTemplate c){return responses.success(service.create(c));}
 @PutMapping("/templates/{id}")@RequiresPermission("message:template:manage")ApiResponse<MessageModels.TemplateView>update(@PathVariable long id,@Valid@RequestBody MessageModels.SaveTemplate c){return responses.success(service.update(id,c));}
 @PostMapping("/send")@RequiresPermission("message:send")ApiResponse<MessageModels.SendResult>send(@Valid@RequestBody MessageModels.SendMessage c){return responses.success(service.send(c));}
 @GetMapping("/inbox")@RequiresPermission("message:inbox:view")ApiResponse<List<MessageModels.InboxView>>inbox(){return responses.success(service.inbox());}
 @GetMapping("/inbox/unread-count")@RequiresPermission("message:inbox:view")ApiResponse<Map<String,Integer>>unread(){return responses.success(Map.of("count",service.unread()));}
 @PutMapping("/inbox/{id}/read")@RequiresPermission("message:inbox:view")ApiResponse<Void>read(@PathVariable long id){service.read(id);return responses.success(null);}
 @PutMapping("/inbox/read-all")@RequiresPermission("message:inbox:view")ApiResponse<Map<String,Integer>>readAll(){return responses.success(Map.of("updated",service.readAll()));}
}

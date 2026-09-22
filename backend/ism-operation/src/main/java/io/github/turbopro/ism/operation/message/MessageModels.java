package io.github.turbopro.ism.operation.message;
import jakarta.validation.constraints.*;import java.time.LocalDateTime;import java.util.*;
public final class MessageModels{private MessageModels(){}
 public record SaveTemplate(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}")String code,@NotBlank @Size(max=100)String name,@NotBlank @Pattern(regexp="IN_APP|EMAIL|SMS|WE_COM|DINGTALK")String channel,@NotBlank @Size(max=200)String titleTemplate,@NotBlank @Size(max=10000)String contentTemplate,Set<String> requiredVariables,@PositiveOrZero int version){public SaveTemplate{requiredVariables=requiredVariables==null?Set.of():Set.copyOf(requiredVariables);}}
 public record SendMessage(@NotBlank String templateCode,@NotEmpty Set<String> recipientIds,Map<String,String> variables,String businessType,String businessId){public SendMessage{variables=variables==null?Map.of():Map.copyOf(variables);}}
 public record TemplateRow(long id,String templateCode,String templateName,String channel,String titleTemplate,String contentTemplate,String variableSchema,String status,int version){}
 public record TemplateView(String id,String code,String name,String channel,String titleTemplate,String contentTemplate,Set<String> requiredVariables,String status,int version){}
 public record InboxRow(long id,long deliveryId,String title,String content,String businessType,Long businessId,LocalDateTime readAt,LocalDateTime createdAt){}
 public record InboxView(String id,String title,String content,String businessType,String businessId,LocalDateTime readAt,LocalDateTime createdAt){}
 public record SendResult(int requested,int delivered,List<String> deliveryIds){}
}

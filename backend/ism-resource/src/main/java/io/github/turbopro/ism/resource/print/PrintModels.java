package io.github.turbopro.ism.resource.print;
import jakarta.validation.constraints.*;import java.util.*;
public final class PrintModels{private PrintModels(){}
 public record SaveTemplate(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}")String code,@NotBlank @Size(max=100)String name,@NotBlank @Size(max=64)String businessType,@NotBlank @Size(max=200000)String html,Set<String> requiredVariables,@Pattern(regexp="A4|A5|LETTER")String pageSize,@Pattern(regexp="PORTRAIT|LANDSCAPE")String orientation,@PositiveOrZero int version){public SaveTemplate{requiredVariables=requiredVariables==null?Set.of():Set.copyOf(requiredVariables);pageSize=pageSize==null?"A4":pageSize;orientation=orientation==null?"PORTRAIT":orientation;}}
 public record RenderRequest(Map<String,String> variables,String businessType,String businessId){public RenderRequest{variables=variables==null?Map.of():Map.copyOf(variables);}}
 public record TemplateRow(long id,String templateCode,String templateName,String businessType,String draftHtml,String variableSchema,String pageConfig,String status,int currentVersion,int version){}
 public record VersionRow(long id,long templateId,int versionNo,String htmlContent,String variableSchema,String pageConfig){}
 public record TemplateView(String id,String code,String name,String businessType,String html,Set<String> requiredVariables,String pageSize,String orientation,String status,int currentVersion,int version){}
 public record Preview(String title,String html,String pageSize,String orientation){}
 public record PrintJob(String id,String taskId,int templateVersion){}
}

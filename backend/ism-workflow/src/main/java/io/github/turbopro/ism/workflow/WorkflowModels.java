package io.github.turbopro.ism.workflow;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Map;

public final class WorkflowModels {
    private WorkflowModels() {}
    public record SaveDefinition(
        @NotBlank @Pattern(regexp="[a-z][a-z0-9_]{2,63}") String code,
        @NotBlank @Size(max=100) String name,
        @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") String businessType,
        @NotBlank @Size(max=500_000) String bpmnXml,
        @NotNull @Min(0) Integer version) {}
    public record DefinitionRow(long id,long tenantId,String definitionCode,String definitionName,String businessType,String draftBpmn,String status,int currentVersion,int version,LocalDateTime updatedAt) {}
    public record DefinitionView(String id,String code,String name,String businessType,String bpmnXml,String status,int currentVersion,int version,LocalDateTime updatedAt) {}
    public record DefinitionVersionRow(long id,long definitionId,int definitionVersion,String processDefinitionId) {}
    public record StartInstance(@NotBlank @Size(max=64) String definitionCode,@NotBlank @Size(max=128) String businessId,Map<String,Object> variables) {}
    public record InstanceRow(long id,long definitionId,int definitionVersion,String processInstanceId,long starterId,String businessType,String businessId,String status,String result,LocalDateTime startedAt,LocalDateTime finishedAt) {}
    public record InstanceView(String id,String definitionId,int definitionVersion,String processInstanceId,String businessType,String businessId,String status,String result,LocalDateTime startedAt,LocalDateTime finishedAt) {}
    public record TaskView(String id,String processInstanceId,String taskDefinitionKey,String name,String assignee,LocalDateTime createdAt) {}
    public enum Decision { APPROVE, REJECT }
    public record CompleteTask(@NotNull Decision decision,@Size(max=500) String comment) {}
}

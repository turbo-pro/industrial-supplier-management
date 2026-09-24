package io.github.turbopro.ism.workflow;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowService service;private final ApiResponseFactory responses;
    public WorkflowController(WorkflowService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping("/definitions") @RequiresPermission("workflow:definition:view") ApiResponse<List<WorkflowModels.DefinitionView>> definitions(){return responses.success(service.definitions());}
    @GetMapping("/definitions/{id}") @RequiresPermission("workflow:definition:view") ApiResponse<WorkflowModels.DefinitionView> definition(@PathVariable long id){return responses.success(service.definition(id));}
    @PostMapping("/definitions") @RequiresPermission("workflow:definition:manage") ApiResponse<WorkflowModels.DefinitionView> create(@Valid @RequestBody WorkflowModels.SaveDefinition command){return responses.success(service.create(command));}
    @PutMapping("/definitions/{id}") @RequiresPermission("workflow:definition:manage") ApiResponse<WorkflowModels.DefinitionView> update(@PathVariable long id,@Valid @RequestBody WorkflowModels.SaveDefinition command){return responses.success(service.update(id,command));}
    @PostMapping("/definitions/{id}/publish") @RequiresPermission("workflow:definition:manage") ApiResponse<WorkflowModels.DefinitionView> publish(@PathVariable long id,@RequestParam int version){return responses.success(service.publish(id,version));}
    @PostMapping("/instances") @RequiresPermission("workflow:instance:start") ApiResponse<WorkflowModels.InstanceView> start(@Valid @RequestBody WorkflowModels.StartInstance command){return responses.success(service.start(command));}
    @GetMapping("/tasks/my") @RequiresPermission("workflow:task:view") ApiResponse<List<WorkflowModels.TaskView>> myTasks(){return responses.success(service.myTasks());}
    @PostMapping("/tasks/{taskId}/claim") @RequiresPermission("workflow:task:operate") ApiResponse<WorkflowModels.TaskView> claim(@PathVariable String taskId){return responses.success(service.claim(taskId));}
    @PostMapping("/tasks/{taskId}/complete") @RequiresPermission("workflow:task:operate") ApiResponse<Void> complete(@PathVariable String taskId,@Valid @RequestBody WorkflowModels.CompleteTask command){service.complete(taskId,command);return responses.success(null);}
}

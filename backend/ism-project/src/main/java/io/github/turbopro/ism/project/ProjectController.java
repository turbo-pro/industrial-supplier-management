package io.github.turbopro.ism.project;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api")
public class ProjectController {
    private final ProjectService service;private final ApiResponseFactory responses;
    public ProjectController(ProjectService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping("/contracts") @RequiresPermission("contract:view") ApiResponse<ProjectModels.ContractPage> contracts(@RequestParam(defaultValue="") @Size(max=100) String keyword,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.contracts(keyword,status,page,size));}
    @GetMapping("/contracts/{id}") @RequiresPermission("contract:view") ApiResponse<ProjectModels.ContractView> contract(@PathVariable long id){return responses.success(service.contract(id));}
    @PostMapping("/contracts") @RequiresPermission("contract:manage") ApiResponse<ProjectModels.ContractView> createContract(@Valid @RequestBody ProjectModels.SaveContract c){return responses.success(service.createContract(c));}
    @PutMapping("/contracts/{id}") @RequiresPermission("contract:manage") ApiResponse<ProjectModels.ContractView> updateContract(@PathVariable long id,@Valid @RequestBody ProjectModels.SaveContract c){return responses.success(service.updateContract(id,c));}
    @PostMapping("/contracts/{id}/status") @RequiresPermission("contract:status") ApiResponse<ProjectModels.ContractView> contractStatus(@PathVariable long id,@Valid @RequestBody ProjectModels.ChangeContractStatus c){return responses.success(service.changeContractStatus(id,c));}
    @GetMapping("/projects") @RequiresPermission("project:view") ApiResponse<ProjectModels.ProjectPage> projects(@RequestParam(defaultValue="") @Size(max=100) String keyword,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.projects(keyword,status,page,size));}
    @GetMapping("/projects/{id}") @RequiresPermission("project:view") ApiResponse<ProjectModels.ProjectView> project(@PathVariable long id){return responses.success(service.project(id));}
    @PostMapping("/projects") @RequiresPermission("project:manage") ApiResponse<ProjectModels.ProjectView> createProject(@Valid @RequestBody ProjectModels.SaveProject c){return responses.success(service.createProject(c));}
    @PutMapping("/projects/{id}") @RequiresPermission("project:manage") ApiResponse<ProjectModels.ProjectView> updateProject(@PathVariable long id,@Valid @RequestBody ProjectModels.SaveProject c){return responses.success(service.updateProject(id,c));}
    @PostMapping("/projects/{id}/status") @RequiresPermission("project:status") ApiResponse<ProjectModels.ProjectView> projectStatus(@PathVariable long id,@Valid @RequestBody ProjectModels.ChangeProjectStatus c){return responses.success(service.changeProjectStatus(id,c));}
}

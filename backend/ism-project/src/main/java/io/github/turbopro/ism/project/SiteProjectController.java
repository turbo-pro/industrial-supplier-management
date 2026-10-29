package io.github.turbopro.ism.project;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api")
public class SiteProjectController {
    private final ProjectService service;private final ApiResponseFactory responses;
    public SiteProjectController(ProjectService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping("/site-projects") @RequiresPermission("project:view") ApiResponse<ProjectModels.ProjectPage> projects(@RequestParam(defaultValue="") @Size(max=100) String keyword,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){requireSite();return responses.success(service.projects(keyword,status,page,size));}
    @GetMapping("/site-projects/{id}") @RequiresPermission("project:view") ApiResponse<ProjectModels.ProjectView> project(@PathVariable long id){requireSite();return responses.success(service.project(id));}
    @PostMapping("/site-projects") @RequiresPermission("project:manage") ApiResponse<ProjectModels.ProjectView> createProject(@Valid @RequestBody ProjectModels.SaveProject c){requireSite();return responses.success(service.createProject(c));}
    @PutMapping("/site-projects/{id}") @RequiresPermission("project:manage") ApiResponse<ProjectModels.ProjectView> updateProject(@PathVariable long id,@Valid @RequestBody ProjectModels.SaveProject c){requireSite();return responses.success(service.updateProject(id,c));}
    @PostMapping("/site-projects/{id}/status") @RequiresPermission("project:status") ApiResponse<ProjectModels.ProjectView> projectStatus(@PathVariable long id,@Valid @RequestBody ProjectModels.ChangeProjectStatus c){requireSite();return responses.success(service.changeProjectStatus(id,c));}
    public record Access(boolean canManage,boolean canChangeStatus){}
    @GetMapping("/site-projects/access") @RequiresPermission("project:view") ApiResponse<Access> access(){requireSite();var grants=io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext.require();return responses.success(new Access(grants.hasAction("project:manage"),grants.hasAction("project:status")));}
    private void requireSite(){if(io.github.turbopro.ism.common.infrastructure.tenant.SiteContext.current().isEmpty())throw new io.github.turbopro.ism.common.api.error.ApiException(io.github.turbopro.ism.common.api.error.CommonErrorCode.VALIDATION_FAILED,"请先选择并进入经营场站");}
}

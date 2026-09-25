package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.infrastructure.authorization.RequiresPermission;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Validated @RestController @RequestMapping("/api/qualifications")
public class QualificationController {
    private final QualificationService service;private final ApiResponseFactory responses;
    public QualificationController(QualificationService service,ApiResponseFactory responses){this.service=service;this.responses=responses;}
    @GetMapping("/types") @RequiresPermission("supplier:qualification:view") ApiResponse<List<QualificationModels.TypeView>> types(){return responses.success(service.types());}
    @PostMapping("/types") @RequiresPermission("supplier:qualification:type") ApiResponse<QualificationModels.TypeView> createType(@Valid @RequestBody QualificationModels.SaveType c){return responses.success(service.createType(c));}
    @PutMapping("/types/{id}") @RequiresPermission("supplier:qualification:type") ApiResponse<QualificationModels.TypeView> updateType(@PathVariable long id,@Valid @RequestBody QualificationModels.SaveType c){return responses.success(service.updateType(id,c));}
    @GetMapping @RequiresPermission("supplier:qualification:view") ApiResponse<QualificationModels.Page> list(@RequestParam(defaultValue="") @Size(max=100) String keyword,@RequestParam(defaultValue="") String status,@RequestParam(required=false) Long supplierId,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return responses.success(service.list(keyword,status,supplierId,page,size));}
    @GetMapping("/{id}") @RequiresPermission("supplier:qualification:view") ApiResponse<QualificationModels.View> get(@PathVariable long id){return responses.success(service.get(id));}
    @PostMapping @RequiresPermission("supplier:qualification:manage") ApiResponse<QualificationModels.View> create(@Valid @RequestBody QualificationModels.SaveQualification c){return responses.success(service.create(c));}
    @PutMapping("/{id}") @RequiresPermission("supplier:qualification:manage") ApiResponse<QualificationModels.View> update(@PathVariable long id,@Valid @RequestBody QualificationModels.SaveQualification c){return responses.success(service.update(id,c));}
    @PostMapping("/{id}/verify") @RequiresPermission("supplier:qualification:verify") ApiResponse<QualificationModels.View> verify(@PathVariable long id,@Valid @RequestBody QualificationModels.VerifyCommand c){return responses.success(service.verify(id,c));}
    @PostMapping("/{id}/revoke") @RequiresPermission("supplier:qualification:verify") ApiResponse<QualificationModels.View> revoke(@PathVariable long id,@Valid @RequestBody QualificationModels.RevokeCommand c){return responses.success(service.revoke(id,c));}
    @PostMapping("/{id}/renew") @RequiresPermission("supplier:qualification:manage") ApiResponse<QualificationModels.View> renew(@PathVariable long id,@Valid @RequestBody QualificationModels.SaveQualification c){return responses.success(service.renew(id,c));}
}

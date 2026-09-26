package io.github.turbopro.ism.integration.finance;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.resource.file.FileReferenceService;
import io.github.turbopro.ism.supplier.SupplierService;
import io.github.turbopro.ism.supplier.SupplierReferenceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Map;

@Service
public class ManualClearanceService {
    private final ManualClearanceMapper mapper;
    private final SupplierService suppliers;
    private final SupplierReferenceService references;
    private final FileReferenceService files;
    private final AuditService audit;
    private final boolean enabled;
    private final Duration maxAge;
    public ManualClearanceService(ManualClearanceMapper mapper,SupplierService suppliers,
            SupplierReferenceService references,FileReferenceService files,AuditService audit,
            @Value("${ism.integration.finance.mode:ERP}") String mode,
            @Value("${ism.integration.finance.clearance-max-age:PT15M}") Duration maxAge) {
        this.mapper=mapper;this.suppliers=suppliers;this.references=references;this.files=files;this.audit=audit;
        if(!mode.equals("ERP")&&!mode.equals("MANUAL"))throw new IllegalArgumentException("Finance mode must be ERP or MANUAL");
        this.enabled=mode.equals("MANUAL");this.maxAge=maxAge;
    }
    public ManualClearanceModels.View get(long supplierId) {
        suppliers.get(supplierId);
        var row=mapper.get(TenantContext.require().tenantId(),supplierId);
        return view(row);
    }
    @Transactional public ManualClearanceModels.View submit(long supplierId,ManualClearanceModels.Submit command) {
        writable(supplierId);
        var i=TenantContext.require();
        long fileId;
        try {fileId=Long.parseLong(command.evidenceFileId());}catch(NumberFormatException e){throw invalid("证据文件 ID 无效");}
        if(!files.active(fileId))throw invalid("证据文件不存在或不可用");
        var before=mapper.get(i.tenantId(),supplierId);
        if(before!=null && (before.status().equals("SUBMITTED") || (before.status().equals("APPROVED")
                && before.reviewedAt()!=null && !before.reviewedAt().isBefore(Instant.now().minus(maxAge)))))
            throw invalid("待复核或有效确认不能覆盖，请先复核或撤销");
        if(before==null) {
            if(command.version()!=0)throw new ApiException(CommonErrorCode.CONFLICT);
            mapper.insert(i.tenantId(),supplierId,fileId,command.statement().trim(),i.actorId());
        } else if(mapper.resubmit(i.tenantId(),supplierId,fileId,command.statement().trim(),i.actorId(),command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        recordAudit("FINANCE_MANUAL_SUBMIT",supplierId,Map.of("evidenceFileId",command.evidenceFileId(),"statement",command.statement().trim()));
        return get(supplierId);
    }
    @Transactional public ManualClearanceModels.View review(long supplierId,ManualClearanceModels.Review command) {
        writable(supplierId);
        var i=TenantContext.require();var before=mapper.get(i.tenantId(),supplierId);
        if(before==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        boolean revoke=command.decision()==ManualClearanceModels.Decision.REVOKE;
        if(!(revoke?"APPROVED":"SUBMITTED").equals(before.status()))throw invalid("当前核验状态不允许此操作");
        if(!revoke && before.submittedBy()==i.actorId())throw invalid("提交人不能复核自己的财务确认");
        if(!revoke && !files.active(before.evidenceFileId()))throw invalid("核验证据文件已不可用");
        String next=switch(command.decision()){case APPROVE->"APPROVED";case REJECT->"REJECTED";case REVOKE->"REVOKED";};
        if(mapper.review(i.tenantId(),supplierId,next,command.comment().trim(),i.actorId(),command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        recordAudit("FINANCE_MANUAL_"+command.decision(),supplierId,Map.of("status",next,"comment",command.comment().trim()));
        return get(supplierId);
    }
    private void writable(long supplierId) {
        if(!enabled)throw invalid("人工财务核验模式未启用");
        suppliers.get(supplierId);
        if(!references.lockForExitVerification(supplierId))throw invalid("供应商不存在或已退出");
    }
    private ManualClearanceModels.View view(ManualClearanceModels.Row row) {
        return new ManualClearanceModels.View(enabled,row==null?null:new ManualClearanceModels.Record(row.status(),
            Long.toString(row.evidenceFileId()),row.statement(),Long.toString(row.submittedBy()),row.submittedAt(),
            row.reviewedBy()==null?null:row.reviewedBy().toString(),row.reviewedAt(),row.reviewComment(),row.version()));
    }
    private ApiException invalid(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
    private void recordAudit(String action,long id,Map<String,Object> after) {
        audit.append(new AuditService.AuditCommand(action,"MANUAL_FINANCIAL_CLEARANCE",id,null,Map.of(),after,null,null));
    }
}

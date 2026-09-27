package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

/** Advisory snapshot only. Business commands must still enforce their own current checks. */
@Service
public class RestrictionExplanationService {
    public enum Action { QUALIFICATION_APPLY, PROJECT_CREATE, RESOURCE_ASSIGN, SITE_ENTER, START_WORK, RESUME_WORK }
    public enum Decision { ALLOW, WARN, DENY }
    public record Hit(String code,String sourceId,Decision decision,String explanation) {}
    public record View(String supplierId,Action action,Decision decision,LocalDate businessDate,
                       String scope,boolean advisoryOnly,List<Hit> hits) {}
    private final SupplierService scope;
    private final SupplierMapper suppliers;
    private final SupplierRestrictionEvaluator evaluator;
    private final AuditService audit;
    public RestrictionExplanationService(SupplierService scope,SupplierMapper suppliers,SupplierRestrictionEvaluator evaluator,AuditService audit){
        this.scope=scope;this.suppliers=suppliers;this.evaluator=evaluator;this.audit=audit;
    }
    @Transactional
    public View explain(long supplierId,Action action){
        if(action==null)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"请选择受控业务动作");
        scope.get(supplierId);
        var identity=TenantContext.require();
        var row=suppliers.findForNewBusiness(identity.tenantId(),supplierId);
        if(row==null||!AuthorizationContext.require().dataScope("supplier:master")
            .allows(new DataTarget(row.organizationId(),null,row.createdBy(),row.createdBy()),identity.actorId()))
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        var evaluation=evaluator.evaluateLocked(supplierId,row.status(),SupplierRestrictionEvaluator.Action.valueOf(action.name()));
        var hits=evaluation.hits().stream().map(hit->new Hit(hit.code(),hit.sourceId(),Decision.valueOf(hit.decision().name()),hit.explanation())).toList();
        var decision=Decision.valueOf(evaluation.decision().name());var date=evaluation.businessDate();
        var view=new View(Long.toString(supplierId),action,decision,date,"TENANT_ENTERPRISE",true,hits);
        // Only stable identifiers/codes are recorded; restriction reasons can contain sensitive information.
        audit.append(new AuditService.AuditCommand("SUPPLIER_RESTRICTION_EXPLAIN","SUPPLIER",supplierId,null,Map.of(),
            Map.of("action",action,"decision",decision,"businessDate",date.toString(),"scope",view.scope(),
                "advisoryOnly",true,"hits",view.hits()),null,null));
        return view;
    }
}

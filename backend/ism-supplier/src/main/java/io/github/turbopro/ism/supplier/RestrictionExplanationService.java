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
    private final BlacklistMapper restrictions;
    private final AuditService audit;
    public RestrictionExplanationService(SupplierService scope,SupplierMapper suppliers,BlacklistMapper restrictions,AuditService audit){
        this.scope=scope;this.suppliers=suppliers;this.restrictions=restrictions;this.audit=audit;
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
        var hits=new ArrayList<Hit>();
        var allowed=action==Action.QUALIFICATION_APPLY?Set.of("DRAFT","ACTIVE","SUSPENDED"):Set.of("ACTIVE");
        if(!allowed.contains(row.status()))hits.add(new Hit("SUPPLIER_STATUS",Long.toString(supplierId),Decision.DENY,"供应商当前状态不允许此新增业务："+row.status()));
        LocalDate date=RestrictionBusinessDate.today();
        for(var restriction:restrictions.currentEffectiveCases(identity.tenantId(),supplierId,date)){
            boolean warning=restriction.restrictionType()==BlacklistModels.RestrictionType.WATCH;
            hits.add(new Hit(restriction.restrictionType().name(),Long.toString(restriction.id()),warning?Decision.WARN:Decision.DENY,
                warning?"供应商处于观察期，请核对风险记录":"存在有效企业级限制，请核对限制记录"));
        }
        for(var id:suppliers.currentPendingExits(identity.tenantId(),supplierId))
            hits.add(new Hit("PENDING_EXIT",Long.toString(id),Decision.DENY,"退出申请待处置，禁止新增业务"));
        Decision decision=hits.stream().anyMatch(hit->hit.decision()==Decision.DENY)?Decision.DENY:
            hits.isEmpty()?Decision.ALLOW:Decision.WARN;
        var view=new View(Long.toString(supplierId),action,decision,date,"TENANT_ENTERPRISE",true,List.copyOf(hits));
        // Only stable identifiers/codes are recorded; restriction reasons can contain sensitive information.
        audit.append(new AuditService.AuditCommand("SUPPLIER_RESTRICTION_EXPLAIN","SUPPLIER",supplierId,null,Map.of(),
            Map.of("action",action,"decision",decision,"businessDate",date.toString(),"scope",view.scope(),
                "advisoryOnly",true,"hits",view.hits()),null,null));
        return view;
    }
}

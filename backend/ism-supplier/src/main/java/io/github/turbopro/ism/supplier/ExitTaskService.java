package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class ExitTaskService {
    private static final Set<String> CODES=Set.of("OPEN_CONTRACT","OPEN_PROJECT","OPEN_PERSON","OPEN_ASSET","OPEN_SAFETY","OPEN_ATTENDANCE","OPEN_QUALITY","OPEN_IMPROVEMENT");
    private final ExitTaskMapper mapper;
    public ExitTaskService(ExitTaskMapper mapper){this.mapper=mapper;}
    @Transactional(readOnly=true)
    public ExitTaskModels.MonitorPage monitor(String keyword,String code,boolean unassignedOnly,boolean overdueOnly,int page,int size){
        if(!AuthorizationContext.require().hasAction("supplier:exit:monitor"))throw new ApiException(CommonErrorCode.FORBIDDEN);
        if(page<0||page>10000||size<1||size>100||keyword!=null&&keyword.length()>100)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"监控查询参数无效");
        String type=code==null||code.isBlank()?null:code;
        if(type!=null&&!CODES.contains(type))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"事项类型无效");
        var identity=TenantContext.require();var scope=AuthorizationContext.require().dataScope("supplier:master");var today=RestrictionBusinessDate.today();
        String like=keyword==null||keyword.isBlank()?null:"%"+keyword.trim().replace("=","==").replace("%","=%").replace("_","=_")+"%";
        long total=mapper.monitorCount(identity.tenantId(),identity.actorId(),scope.type().name(),scope.organizationIds(),like,type,unassignedOnly,overdueOnly,today);
        var items=mapper.monitorList(identity.tenantId(),identity.actorId(),scope.type().name(),scope.organizationIds(),like,type,unassignedOnly,overdueOnly,today,Math.multiplyExact(page,size),size).stream().map(r->new ExitTaskModels.MonitorItem(
            Long.toString(r.id()),Long.toString(r.applicationId()),Long.toString(r.supplierId()),r.supplierCode(),r.supplierName(),Long.toString(r.organizationId()),r.checkCode(),Long.toString(r.sourceId()),r.route(),r.note(),r.assigneeId()==null?null:Long.toString(r.assigneeId()),r.assignedAt(),r.checkedAt(),r.dueDate(),r.lastRemindedAt(),r.dueDate()!=null&&r.dueDate().isBefore(today))).toList();
        return new ExitTaskModels.MonitorPage(total,page,size,items);
    }
    @Transactional(readOnly=true)
    public ExitTaskModels.Page mine(String keyword,String code,int page,int size){
        if(page<0||page>10000||size<1||size>100||keyword!=null&&keyword.length()>100)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"待办查询参数无效");
        String type=code==null||code.isBlank()?null:code;
        if(type!=null&&!CODES.contains(type))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"事项类型无效");
        var identity=TenantContext.require();var scope=AuthorizationContext.require().dataScope("supplier:master");
        String like=keyword==null||keyword.isBlank()?null:"%"+keyword.trim().replace("=","==").replace("%","=%").replace("_","=_")+"%";
        long total=mapper.count(identity.tenantId(),identity.actorId(),scope.type().name(),scope.organizationIds(),like,type);
        var items=mapper.list(identity.tenantId(),identity.actorId(),scope.type().name(),scope.organizationIds(),like,type,Math.multiplyExact(page,size),size).stream().map(r->new ExitTaskModels.Task(
            Long.toString(r.id()),Long.toString(r.applicationId()),Long.toString(r.supplierId()),r.supplierCode(),r.supplierName(),Long.toString(r.organizationId()),r.checkCode(),Long.toString(r.sourceId()),r.route(),r.note(),r.assignedAt(),r.checkedAt(),r.version(),r.applicationVersion(),r.dueDate(),r.lastRemindedAt(),r.dueDate()!=null&&r.dueDate().isBefore(RestrictionBusinessDate.today()))).toList();
        return new ExitTaskModels.Page(total,page,size,items);
    }
}

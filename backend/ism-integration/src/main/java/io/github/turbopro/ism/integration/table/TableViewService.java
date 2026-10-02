package io.github.turbopro.ism.integration.table;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class TableViewService {
    private static final List<TableViewModels.Definition> SUPPLIER_COLUMNS=List.of(
        new TableViewModels.Definition("code","供应商编码",true,160),new TableViewModels.Definition("name","供应商名称",true,240),
        new TableViewModels.Definition("type","类型",false,110),new TableViewModels.Definition("riskLevel","风险",false,90),
        new TableViewModels.Definition("status","状态",false,110),new TableViewModels.Definition("updatedAt","更新时间",false,180));
    private record Registration(String permission,List<TableViewModels.Definition> columns){}
    private static final Map<String,Registration> TABLES=Map.ofEntries(
        Map.entry("supplier.master",new Registration("supplier:master:view",SUPPLIER_COLUMNS)),
        Map.entry("supplier.admission",new Registration("supplier:admission:view",List.of(
            new TableViewModels.Definition("applicationNo","申请单号",true,190),new TableViewModels.Definition("supplierName","供应商",true,220),
            new TableViewModels.Definition("purchaseCategory","采购类别",false,150),new TableViewModels.Definition("status","状态",false,110),
            new TableViewModels.Definition("submittedAt","提交时间",false,180)))),
        Map.entry("supplier.qualification",new Registration("supplier:qualification:view",List.of(
            new TableViewModels.Definition("supplierName","供应商",true,210),new TableViewModels.Definition("typeName","资质类型",false,150),
            new TableViewModels.Definition("certificateNo","证书编号",true,160),new TableViewModels.Definition("expiryDate","有效期",false,150),
            new TableViewModels.Definition("status","状态",false,110)))),
        Map.entry("contract.ledger",new Registration("contract:view",List.of(
            new TableViewModels.Definition("contractNo","合同编号",true,160),new TableViewModels.Definition("name","合同/供应商",true,250),
            new TableViewModels.Definition("amount","金额",false,150),new TableViewModels.Definition("period","期限",false,240),
            new TableViewModels.Definition("status","状态",false,110)))),
        Map.entry("project.ledger",new Registration("project:view",List.of(
            new TableViewModels.Definition("projectCode","项目编码",true,150),new TableViewModels.Definition("name","项目/供应商",true,250),
            new TableViewModels.Definition("contractNo","关联合同",false,150),new TableViewModels.Definition("period","计划周期",false,240),
            new TableViewModels.Definition("status","状态",false,110)))),
        Map.entry("resource.person",new Registration("resource:person:view",List.of(
            new TableViewModels.Definition("code","人员编码",true,140),new TableViewModels.Definition("name","人员/供应商",true,230),
            new TableViewModels.Definition("idNumberMasked","证件号码",false,180),new TableViewModels.Definition("tradeType","工种",false,130),
            new TableViewModels.Definition("projectCode","所属项目",false,140),new TableViewModels.Definition("status","状态",false,100)))),
        Map.entry("resource.asset",new Registration("resource:asset:view",List.of(
            new TableViewModels.Definition("code","资产编码",true,140),new TableViewModels.Definition("name","资产/供应商",true,230),
            new TableViewModels.Definition("type","类型",false,100),new TableViewModels.Definition("plateNo","车牌号",false,130),
            new TableViewModels.Definition("projectCode","所属项目",false,140),new TableViewModels.Definition("status","状态",false,100)))),
        Map.entry("quality.ncr",new Registration("quality:ncr:view",List.of(
            new TableViewModels.Definition("ncrNo","编号",true,150),new TableViewModels.Definition("title","不符合项/项目/供应商",true,230),
            new TableViewModels.Definition("severity","等级",false,100),new TableViewModels.Definition("quantity","缺陷/验收",false,150),
            new TableViewModels.Definition("deadline","整改期限",false,130),new TableViewModels.Definition("status","状态",false,120)))),
        Map.entry("performance.evaluation",new Registration("performance:evaluation:view",List.of(
            new TableViewModels.Definition("supplierName","供应商",true,200),new TableViewModels.Definition("period","评价周期",true,230),
            new TableViewModels.Definition("score","总分/等级",false,130),new TableViewModels.Definition("facts","质量/安全事实",false,190),
            new TableViewModels.Definition("status","状态",false,120)))),
        Map.entry("safety.issue",new Registration("safety:issue:view",List.of(
            new TableViewModels.Definition("issueNo","隐患编号",true,150),new TableViewModels.Definition("title","隐患/项目/供应商",true,260),
            new TableViewModels.Definition("severity","等级",false,100),new TableViewModels.Definition("deadline","整改期限",false,130),
            new TableViewModels.Definition("status","状态",false,120)))),
        Map.entry("safety.attendance",new Registration("safety:attendance:view",List.of(
            new TableViewModels.Definition("personName","人员",true,190),new TableViewModels.Definition("supplierName","供应商",false,180),
            new TableViewModels.Definition("projectName","项目",false,180),new TableViewModels.Definition("siteName","现场位置",true,160),
            new TableViewModels.Definition("checkInAt","签到时间",false,190),new TableViewModels.Definition("checkOutAt","签退时间",false,190),
            new TableViewModels.Definition("status","状态",false,110)))));
    private final TableViewMapper mapper;private final OperationIdGenerator ids;private final ObjectMapper json;private final AuditService audit;
    public TableViewService(TableViewMapper mapper,OperationIdGenerator ids,ObjectMapper json,AuditService audit){this.mapper=mapper;this.ids=ids;this.json=json;this.audit=audit;}
    public TableViewModels.Page list(String tableKey){var catalog=catalog(tableKey);var i=TenantContext.require();return new TableViewModels.Page(tableKey,catalog,20,
        mapper.list(i.tenantId(),i.actorId(),tableKey).stream().map(this::view).toList(),
        mapper.list(i.tenantId(),0,tableKey).stream().map(this::view).toList(),AuthorizationContext.require().hasAction("table:view:publish")&&AuthorizationContext.require().hasAction("table:view:manage"));}
    @Transactional public TableViewModels.View create(String tableKey,TableViewModels.Save command){
        validate(tableKey,command);if(command.version()!=0)throw invalid("新建方案版本必须为 0");lock(tableKey);
        var i=TenantContext.require();if(mapper.count(i.tenantId(),i.actorId(),tableKey)>=20)throw invalid("每张表最多保存 20 套个人方案");
        long id=ids.nextId();try{
            if(command.defaultView())mapper.clearDefault(i.tenantId(),i.actorId(),tableKey,id);
            mapper.insert(id,i.tenantId(),i.actorId(),tableKey,command.name().trim(),write(command.columns()),command.defaultView());
        }catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"方案名称已存在或默认方案冲突");}
        audit("TABLE_VIEW_CREATE",tableKey,id);return view(require(tableKey,id));
    }
    @Transactional public TableViewModels.View update(String tableKey,long id,TableViewModels.Save command){
        validate(tableKey,command);lock(tableKey);var before=require(tableKey,id);if(before.version()!=command.version())throw conflict();
        var i=TenantContext.require();try{
            if(command.defaultView())mapper.clearDefault(i.tenantId(),i.actorId(),tableKey,id);
            if(mapper.update(i.tenantId(),i.actorId(),tableKey,id,command.name().trim(),write(command.columns()),command.defaultView(),command.version())!=1)throw conflict();
        }catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"方案名称已存在或默认方案冲突");}
        audit("TABLE_VIEW_UPDATE",tableKey,id);return view(require(tableKey,id));
    }
    @Transactional public void delete(String tableKey,long id,int version){
        catalog(tableKey);lock(tableKey);var row=require(tableKey,id);if(version<0||row.version()!=version)throw conflict();var i=TenantContext.require();
        if(mapper.delete(i.tenantId(),i.actorId(),tableKey,id,version)!=1)throw conflict();audit("TABLE_VIEW_DELETE",tableKey,id);
    }
    @Transactional public TableViewModels.View createShared(String tableKey,TableViewModels.Save command){
        publisher();validate(tableKey,command);if(command.version()!=0)throw invalid("新建方案版本必须为 0");lock(tableKey,0);
        var i=TenantContext.require();if(mapper.count(i.tenantId(),0,tableKey)>=20)throw invalid("每张表最多发布 20 套共享方案");
        long id=ids.nextId();try{
            if(command.defaultView())mapper.clearDefault(i.tenantId(),0,tableKey,id);
            mapper.insert(id,i.tenantId(),0,tableKey,command.name().trim(),write(command.columns()),command.defaultView());
        }catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"共享方案名称或默认方案冲突");}
        audit("TABLE_VIEW_SHARED_CREATE",tableKey,id);return view(require(tableKey,0,id));
    }
    @Transactional public TableViewModels.View updateShared(String tableKey,long id,TableViewModels.Save command){
        publisher();validate(tableKey,command);lock(tableKey,0);var before=require(tableKey,0,id);if(before.version()!=command.version())throw conflict();
        var i=TenantContext.require();try{
            if(command.defaultView())mapper.clearDefault(i.tenantId(),0,tableKey,id);
            if(mapper.update(i.tenantId(),0,tableKey,id,command.name().trim(),write(command.columns()),command.defaultView(),command.version())!=1)throw conflict();
        }catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"共享方案名称或默认方案冲突");}
        audit("TABLE_VIEW_SHARED_UPDATE",tableKey,id);return view(require(tableKey,0,id));
    }
    @Transactional public void deleteShared(String tableKey,long id,int version){
        publisher();catalog(tableKey);lock(tableKey,0);var row=require(tableKey,0,id);if(version<0||row.version()!=version)throw conflict();var i=TenantContext.require();
        if(mapper.delete(i.tenantId(),0,tableKey,id,version)!=1)throw conflict();audit("TABLE_VIEW_SHARED_DELETE",tableKey,id);
    }
    private void publisher(){var permissions=AuthorizationContext.require();if(!permissions.hasAction("table:view:publish")||!permissions.hasAction("table:view:manage"))throw new ApiException(CommonErrorCode.FORBIDDEN);}
    private List<TableViewModels.Definition> catalog(String key){
        var table=key==null?null:TABLES.get(key);
        if(table==null)throw invalid("此表尚未注册列配置");
        if(!AuthorizationContext.require().hasAction(table.permission()))throw new ApiException(CommonErrorCode.FORBIDDEN);
        return table.columns();
    }
    private void validate(String key,TableViewModels.Save c){
        var definitions=catalog(key);
        if(c==null||c.name()==null||c.name().isBlank()||c.name().trim().length()>60||c.version()<0||c.columns()==null||c.columns().size()!=definitions.size())throw invalid("方案名称、版本或列数量无效");
        var seen=new HashSet<String>();
        for(var column:c.columns()){
            if(column==null||column.width()<80||column.width()>600||!seen.add(column.key()))throw invalid("列重复或宽度超出 80–600");
            var definition=definitions.stream().filter(d->d.key().equals(column.key())).findFirst().orElseThrow(()->invalid("包含未注册列"));
            if(definition.required()&&!column.visible())throw invalid("编码与名称列不能隐藏");
        }
    }
    private void lock(String key){var i=TenantContext.require();mapper.ensureOwner(i.tenantId(),i.actorId(),key);if(mapper.lockOwner(i.tenantId(),i.actorId(),key)==null)throw conflict();}
    private void lock(String key,long ownerId){var i=TenantContext.require();mapper.ensureOwner(i.tenantId(),ownerId,key);if(mapper.lockOwner(i.tenantId(),ownerId,key)==null)throw conflict();}
    private TableViewModels.Row require(String key,long id){var i=TenantContext.require();var row=mapper.find(i.tenantId(),i.actorId(),key,id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
    private TableViewModels.Row require(String key,long ownerId,long id){var i=TenantContext.require();var row=mapper.find(i.tenantId(),ownerId,key,id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
    private TableViewModels.View view(TableViewModels.Row row){try{return new TableViewModels.View(Long.toString(row.id()),row.viewName(),json.readValue(row.columnsJson(),new TypeReference<List<TableViewModels.Column>>(){}),row.defaultView(),row.version(),row.updatedAt());}catch(Exception e){throw new IllegalStateException("列方案数据无效",e);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void audit(String action,String key,long id){long actor=TenantContext.require().actorId();audit.append(new AuditService.AuditCommand(action,"TABLE_VIEW",id,null,Map.of(),
        action.startsWith("TABLE_VIEW_SHARED_")?Map.of("tableKey",key,"ownerId",0,"publisherId",actor):Map.of("tableKey",key,"ownerId",actor),null,null));}
    private ApiException invalid(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
    private ApiException conflict(){return new ApiException(CommonErrorCode.CONFLICT);}
}

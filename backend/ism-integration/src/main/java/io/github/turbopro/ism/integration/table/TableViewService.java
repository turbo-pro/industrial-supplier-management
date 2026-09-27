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
    private static final Map<String,Registration> TABLES=Map.of(
        "supplier.master",new Registration("supplier:master:view",SUPPLIER_COLUMNS),
        "contract.ledger",new Registration("contract:view",List.of(
            new TableViewModels.Definition("contractNo","合同编号",true,160),new TableViewModels.Definition("name","合同/供应商",true,250),
            new TableViewModels.Definition("amount","金额",false,150),new TableViewModels.Definition("period","期限",false,240),
            new TableViewModels.Definition("status","状态",false,110))),
        "project.ledger",new Registration("project:view",List.of(
            new TableViewModels.Definition("projectCode","项目编码",true,150),new TableViewModels.Definition("name","项目/供应商",true,250),
            new TableViewModels.Definition("contractNo","关联合同",false,150),new TableViewModels.Definition("period","计划周期",false,240),
            new TableViewModels.Definition("status","状态",false,110))));
    private final TableViewMapper mapper;private final OperationIdGenerator ids;private final ObjectMapper json;private final AuditService audit;
    public TableViewService(TableViewMapper mapper,OperationIdGenerator ids,ObjectMapper json,AuditService audit){this.mapper=mapper;this.ids=ids;this.json=json;this.audit=audit;}
    public TableViewModels.Page list(String tableKey){var catalog=catalog(tableKey);var i=TenantContext.require();return new TableViewModels.Page(tableKey,catalog,20,mapper.list(i.tenantId(),i.actorId(),tableKey).stream().map(this::view).toList());}
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
    private TableViewModels.Row require(String key,long id){var i=TenantContext.require();var row=mapper.find(i.tenantId(),i.actorId(),key,id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
    private TableViewModels.View view(TableViewModels.Row row){try{return new TableViewModels.View(Long.toString(row.id()),row.viewName(),json.readValue(row.columnsJson(),new TypeReference<List<TableViewModels.Column>>(){}),row.defaultView(),row.version(),row.updatedAt());}catch(Exception e){throw new IllegalStateException("列方案数据无效",e);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void audit(String action,String key,long id){audit.append(new AuditService.AuditCommand(action,"TABLE_VIEW",id,null,Map.of(),Map.of("tableKey",key,"ownerId",TenantContext.require().actorId()),null,null));}
    private ApiException invalid(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
    private ApiException conflict(){return new ApiException(CommonErrorCode.CONFLICT);}
}

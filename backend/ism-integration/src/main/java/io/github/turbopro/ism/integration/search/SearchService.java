package io.github.turbopro.ism.integration.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SearchService {
    private static final Map<SearchModels.EntityType,String> REQUIRED_PERMISSION=Map.of(
        SearchModels.EntityType.USER,"iam:user:view",SearchModels.EntityType.ORGANIZATION,"iam:organization:view",
        SearchModels.EntityType.FILE,"resource:file:view",SearchModels.EntityType.PRINT_TEMPLATE,"print:template:view",
        SearchModels.EntityType.MESSAGE,"message:inbox:view",SearchModels.EntityType.TASK,"task:center:view");
    private static final Map<SearchModels.EntityType,String> DATA_RESOURCE=Map.of(
        SearchModels.EntityType.USER,"iam:user",SearchModels.EntityType.ORGANIZATION,"iam:organization",
        SearchModels.EntityType.FILE,"resource:file",SearchModels.EntityType.PRINT_TEMPLATE,"print:template");
    private final SearchMapper mapper;private final OperationIdGenerator ids;private final ObjectMapper json;
    public SearchService(SearchMapper mapper,OperationIdGenerator ids,ObjectMapper json){this.mapper=mapper;this.ids=ids;this.json=json;}

    public SearchModels.SearchResult search(SearchModels.SearchRequest request){
        validate(request);var identity=TenantContext.require();Set<SearchModels.EntityType> requested=request.types().isEmpty()?EnumSet.allOf(SearchModels.EntityType.class):request.types();
        Set<SearchModels.EntityType> allowed=new LinkedHashSet<>();requested.stream().sorted().filter(this::canSearchType).forEach(allowed::add);
        int offset=Math.multiplyExact(request.page(),request.size());int required=Math.addExact(offset,request.size()+1);String keyword=like(request.keyword());List<SearchModels.SearchRow> rows=new ArrayList<>();
        for(var type:allowed)rows.addAll(load(type,identity.tenantId(),identity.actorId(),keyword,request,required));
        rows.sort(Comparator.comparing(SearchModels.SearchRow::updatedAt,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(SearchModels.SearchRow::id,Comparator.reverseOrder()));
        int from=Math.min(offset,rows.size()),to=Math.min(from+request.size(),rows.size());List<SearchModels.SearchItem> items=rows.subList(from,to).stream().map(this::item).toList();
        return new SearchModels.SearchResult(rows.size()>to,request.page(),request.size(),Set.copyOf(allowed),items);
    }
    public List<SearchModels.SavedView> saved(){var i=TenantContext.require();return mapper.saved(i.tenantId(),i.actorId()).stream().map(this::view).toList();}
    @Transactional public SearchModels.SavedView create(SearchModels.SaveSearch command){validate(command.query());var i=TenantContext.require();long id=ids.nextId();try{if(command.defaultSearch())mapper.clearDefault(i.tenantId(),i.actorId(),id);mapper.insertSaved(i.tenantId(),i.actorId(),id,command.name().trim(),write(command.query()),command.defaultSearch());}catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"搜索方案名称已存在");}return view(require(id));}
    @Transactional public SearchModels.SavedView update(long id,SearchModels.SaveSearch command){validate(command.query());var i=TenantContext.require();if(command.defaultSearch())mapper.clearDefault(i.tenantId(),i.actorId(),id);try{if(mapper.updateSaved(i.tenantId(),i.actorId(),id,command.name().trim(),write(command.query()),command.defaultSearch(),command.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);}catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"搜索方案名称已存在");}return view(require(id));}
    @Transactional public void delete(long id,int version){var i=TenantContext.require();if(mapper.deleteSaved(i.tenantId(),i.actorId(),id,version)!=1)throw new ApiException(CommonErrorCode.CONFLICT);}
    private List<SearchModels.SearchRow> load(SearchModels.EntityType type,long tenantId,long userId,String keyword,SearchModels.SearchRequest q,int limit){var scope=DATA_RESOURCE.containsKey(type)?AuthorizationContext.require().dataScope(DATA_RESOURCE.get(type)):null;String scopeType=scope==null?"OWNED":scope.type().name();Set<Long> organizations=scope==null?Set.of():scope.organizationIds();return switch(type){case USER->mapper.users(tenantId,keyword,q.statuses(),q.updatedFrom(),q.updatedTo(),limit,scopeType,organizations,userId);case ORGANIZATION->mapper.organizations(tenantId,keyword,q.statuses(),q.updatedFrom(),q.updatedTo(),limit,scopeType,organizations,userId);case FILE->mapper.files(tenantId,keyword,q.statuses(),q.updatedFrom(),q.updatedTo(),limit,scopeType,organizations,userId);case PRINT_TEMPLATE->mapper.printTemplates(tenantId,keyword,q.statuses(),q.updatedFrom(),q.updatedTo(),limit);case MESSAGE->mapper.messages(tenantId,userId,keyword,q.statuses(),q.updatedFrom(),q.updatedTo(),limit);case TASK->mapper.tasks(tenantId,userId,keyword,q.statuses(),q.updatedFrom(),q.updatedTo(),limit);};}
    private void validate(SearchModels.SearchRequest q){if(q.updatedFrom()!=null&&q.updatedTo()!=null&&q.updatedFrom().isAfter(q.updatedTo()))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"开始时间不能晚于结束时间");if(q.keyword().isBlank()&&q.types().isEmpty()&&q.statuses().isEmpty()&&q.updatedFrom()==null&&q.updatedTo()==null)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"至少提供一个搜索条件");}
    private String like(String value){return "%"+value.replace("=","==").replace("%","=%").replace("_","=_")+"%";}
    private SearchModels.SearchItem item(SearchModels.SearchRow row){return new SearchModels.SearchItem(Long.toString(row.id()),SearchModels.EntityType.valueOf(row.entityType()),row.title(),row.subtitle(),row.status(),row.route(),row.updatedAt());}
    private boolean canSearchType(SearchModels.EntityType type){if(!AuthorizationContext.require().hasAction(REQUIRED_PERMISSION.get(type)))return false;if(type==SearchModels.EntityType.MESSAGE||type==SearchModels.EntityType.TASK)return true;var scope=AuthorizationContext.require().dataScope(DATA_RESOURCE.get(type));return switch(type){case USER,ORGANIZATION->scope.type()==io.github.turbopro.ism.common.infrastructure.authorization.DataScope.Type.TENANT_ALL||(scope.type()==io.github.turbopro.ism.common.infrastructure.authorization.DataScope.Type.ORGANIZATION_SET&&!scope.organizationIds().isEmpty());case FILE->Set.of(io.github.turbopro.ism.common.infrastructure.authorization.DataScope.Type.TENANT_ALL,io.github.turbopro.ism.common.infrastructure.authorization.DataScope.Type.OWNED,io.github.turbopro.ism.common.infrastructure.authorization.DataScope.Type.CREATED).contains(scope.type());case PRINT_TEMPLATE->scope.type()==io.github.turbopro.ism.common.infrastructure.authorization.DataScope.Type.TENANT_ALL;default->false;};}
    private SearchModels.SavedRow require(long id){var i=TenantContext.require();var row=mapper.savedOne(i.tenantId(),i.actorId(),id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
    private SearchModels.SavedView view(SearchModels.SavedRow row){try{return new SearchModels.SavedView(Long.toString(row.id()),row.searchName(),json.readValue(row.queryJson(),SearchModels.SearchRequest.class),row.defaultSearch(),row.version(),row.updatedAt());}catch(Exception e){throw new IllegalStateException("搜索方案数据无效",e);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}

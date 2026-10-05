package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class PurchaseCategoryService {
    private final PurchaseCategoryMapper mapper;
    private final SupplierService suppliers;
    private final OperationIdGenerator ids;
    private final AuditService audit;
    public PurchaseCategoryService(PurchaseCategoryMapper mapper,SupplierService suppliers,OperationIdGenerator ids,AuditService audit){this.mapper=mapper;this.suppliers=suppliers;this.ids=ids;this.audit=audit;}
    public List<PurchaseCategoryModels.Category> categories(){return mapper.categories(TenantContext.require().tenantId()).stream().map(this::view).toList();}
    @Transactional public PurchaseCategoryModels.Category create(PurchaseCategoryModels.Save command){
        if(command.version()!=0)throw validation("新建品类版本必须为 0");
        var i=TenantContext.require();long id=ids.nextId();
        try{mapper.insert(id,i.tenantId(),command.code(),command.name().trim(),command.status().name());}
        catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"采购品类编码已存在");}
        audit.append(new AuditService.AuditCommand("PURCHASE_CATEGORY_CREATE","PURCHASE_CATEGORY",id,null,Map.of(),Map.of("code",command.code()),null,null));
        return view(mapper.category(i.tenantId(),id));
    }
    @Transactional public PurchaseCategoryModels.Category update(long id,PurchaseCategoryModels.Save command){
        var i=TenantContext.require();var before=mapper.category(i.tenantId(),id);
        if(before==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(!before.categoryCode().equals(command.code()))throw validation("采购品类编码不可修改");
        if(mapper.update(i.tenantId(),id,command.code(),command.name().trim(),command.status().name(),command.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        audit.append(new AuditService.AuditCommand("PURCHASE_CATEGORY_UPDATE","PURCHASE_CATEGORY",id,null,Map.of("status",before.status(),"name",before.categoryName()),Map.of("status",command.status().name(),"name",command.name()),null,null));
        return view(mapper.category(i.tenantId(),id));
    }
    public PurchaseCategoryModels.Assignment assignment(long supplierId){
        var supplier=suppliers.get(supplierId);
        return new PurchaseCategoryModels.Assignment(supplier.id(),mapper.assigned(TenantContext.require().tenantId(),supplierId).stream().map(String::valueOf).toList(),supplier.version());
    }
    @Transactional public PurchaseCategoryModels.Assignment assign(long supplierId,PurchaseCategoryModels.Assign command){
        var supplier=suppliers.get(supplierId);
        if(supplier.status()==SupplierModels.Status.EXITED)throw validation("已退出供应商不可修改采购品类");
        List<Long> categoryIds;
        try{categoryIds=command.categoryIds().stream().map(Long::parseLong).toList();}
        catch(NumberFormatException e){throw validation("采购品类 ID 超出有效范围");}
        if(new HashSet<>(categoryIds).size()!=categoryIds.size())throw validation("采购品类不可重复");
        var i=TenantContext.require();
        var before=mapper.assigned(i.tenantId(),supplierId);
        var additions=categoryIds.stream().filter(id->!before.contains(id)).toList();
        if(!additions.isEmpty()&&mapper.activeIdsForUpdate(i.tenantId(),additions).size()!=additions.size())throw validation("品类不存在或已停用");
        if(mapper.claimSupplier(i.tenantId(),supplierId,command.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        mapper.clearAssignments(i.tenantId(),supplierId);
        for(long categoryId:categoryIds)mapper.assign(i.tenantId(),supplierId,categoryId);
        audit.append(new AuditService.AuditCommand("SUPPLIER_PURCHASE_CATEGORIES_UPDATE","SUPPLIER",supplierId,null,Map.of("categoryIds",before),Map.of("categoryIds",categoryIds),null,null));
        return new PurchaseCategoryModels.Assignment(supplier.id(),categoryIds.stream().map(String::valueOf).toList(),command.version()+1);
    }
    private PurchaseCategoryModels.Category view(PurchaseCategoryModels.Row row){return new PurchaseCategoryModels.Category(String.valueOf(row.id()),row.categoryCode(),row.categoryName(),PurchaseCategoryModels.Status.valueOf(row.status()),row.version(),row.updatedAt());}
    private ApiException validation(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
}

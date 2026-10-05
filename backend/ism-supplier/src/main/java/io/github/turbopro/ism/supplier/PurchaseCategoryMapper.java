package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PurchaseCategoryMapper extends TenantScopedMapper {
    @Select("SELECT id,category_code,category_name,status,version,updated_at FROM sup_purchase_category WHERE tenant_id=#{tenantId} ORDER BY category_code")
    List<PurchaseCategoryModels.Row> categories(long tenantId);
    @Select("SELECT id,category_code,category_name,status,version,updated_at FROM sup_purchase_category WHERE tenant_id=#{tenantId} AND id=#{id}")
    PurchaseCategoryModels.Row category(long tenantId,long id);
    @Insert("INSERT INTO sup_purchase_category(id,tenant_id,category_code,category_name,status) VALUES(#{id},#{tenantId},#{code},#{name},#{status})")
    int insert(long id,long tenantId,String code,String name,String status);
    @Update("UPDATE sup_purchase_category SET category_name=#{name},status=#{status},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND category_code=#{code} AND version=#{version}")
    int update(long tenantId,long id,String code,String name,String status,int version);
    @Select("SELECT category_id FROM sup_supplier_purchase_category WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} ORDER BY category_id")
    List<Long> assigned(long tenantId,long supplierId);
    @Select("<script>SELECT id FROM sup_purchase_category WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> FOR UPDATE</script>")
    List<Long> activeIdsForUpdate(long tenantId,List<Long> ids);
    @Update("UPDATE sup_supplier SET version=version+1 WHERE tenant_id=#{tenantId} AND id=#{supplierId} AND deleted=0 AND status<>'EXITED' AND version=#{version}")
    int claimSupplier(long tenantId,long supplierId,int version);
    @Delete("DELETE FROM sup_supplier_purchase_category WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}")
    int clearAssignments(long tenantId,long supplierId);
    @Insert("INSERT INTO sup_supplier_purchase_category(tenant_id,supplier_id,category_id) VALUES(#{tenantId},#{supplierId},#{categoryId})")
    int assign(long tenantId,long supplierId,long categoryId);
}

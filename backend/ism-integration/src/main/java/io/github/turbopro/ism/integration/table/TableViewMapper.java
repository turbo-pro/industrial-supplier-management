package io.github.turbopro.ism.integration.table;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface TableViewMapper extends TenantScopedMapper {
    @Insert("INSERT INTO ui_table_view_owner(tenant_id,owner_id,table_key) VALUES(#{tenantId},#{ownerId},#{tableKey}) ON DUPLICATE KEY UPDATE owner_id=VALUES(owner_id)")
    int ensureOwner(long tenantId,long ownerId,String tableKey);
    @Select("SELECT owner_id FROM ui_table_view_owner WHERE tenant_id=#{tenantId} AND owner_id=#{ownerId} AND table_key=#{tableKey} FOR UPDATE")
    Long lockOwner(long tenantId,long ownerId,String tableKey);
    String FIELDS="id,view_name,columns_json,is_default default_view,version,updated_at";
    @Select("SELECT "+FIELDS+" FROM ui_table_view WHERE tenant_id=#{tenantId} AND owner_id=#{ownerId} AND table_key=#{tableKey} ORDER BY is_default DESC,updated_at DESC,id DESC")
    List<TableViewModels.Row> list(long tenantId,long ownerId,String tableKey);
    @Select("SELECT "+FIELDS+" FROM ui_table_view WHERE tenant_id=#{tenantId} AND owner_id=#{ownerId} AND table_key=#{tableKey} AND id=#{id} FOR UPDATE")
    TableViewModels.Row find(long tenantId,long ownerId,String tableKey,long id);
    @Select("SELECT COUNT(*) FROM ui_table_view WHERE tenant_id=#{tenantId} AND owner_id=#{ownerId} AND table_key=#{tableKey} FOR UPDATE")
    int count(long tenantId,long ownerId,String tableKey);
    @Insert("INSERT INTO ui_table_view(id,tenant_id,owner_id,table_key,view_name,columns_json,is_default) VALUES(#{id},#{tenantId},#{ownerId},#{tableKey},#{name},CAST(#{columns} AS JSON),#{defaultView})")
    int insert(long id,long tenantId,long ownerId,String tableKey,String name,String columns,boolean defaultView);
    @Update("UPDATE ui_table_view SET view_name=#{name},columns_json=CAST(#{columns} AS JSON),is_default=#{defaultView},version=version+1 WHERE tenant_id=#{tenantId} AND owner_id=#{ownerId} AND table_key=#{tableKey} AND id=#{id} AND version=#{version}")
    int update(long tenantId,long ownerId,String tableKey,long id,String name,String columns,boolean defaultView,int version);
    @Update("UPDATE ui_table_view SET is_default=0,version=version+1 WHERE tenant_id=#{tenantId} AND owner_id=#{ownerId} AND table_key=#{tableKey} AND is_default=1 AND id!=#{exceptId}")
    int clearDefault(long tenantId,long ownerId,String tableKey,long exceptId);
    @Delete("DELETE FROM ui_table_view WHERE tenant_id=#{tenantId} AND owner_id=#{ownerId} AND table_key=#{tableKey} AND id=#{id} AND version=#{version}")
    int delete(long tenantId,long ownerId,String tableKey,long id,int version);
}

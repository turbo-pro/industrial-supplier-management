package io.github.turbopro.ism.iam.configuration;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConfigurationMapper extends TenantScopedMapper {
    @Select("""
        SELECT COALESCE(t.id,s.id) id,s.item_code,COALESCE(t.item_label,s.item_label) item_label,
          COALESCE(t.item_value,s.item_value) item_value,COALESCE(t.sort_order,s.sort_order) sort_order,
          COALESCE(t.status,s.status) status,COALESCE(t.version,0) version,
          CASE WHEN t.id IS NULL THEN 'SYSTEM' ELSE 'TENANT_OVERRIDE' END source
        FROM sys_dictionary_item s LEFT JOIN cfg_tenant_dictionary_item t
          ON t.tenant_id=#{tenantId} AND t.type_code=s.type_code AND t.item_code=s.item_code
        WHERE s.type_code=#{typeCode}
        UNION ALL
        SELECT t.id,t.item_code,t.item_label,t.item_value,t.sort_order,t.status,t.version,'TENANT_CUSTOM'
        FROM cfg_tenant_dictionary_item t LEFT JOIN sys_dictionary_item s
          ON s.type_code=t.type_code AND s.item_code=t.item_code
        WHERE t.tenant_id=#{tenantId} AND t.type_code=#{typeCode} AND s.id IS NULL
        ORDER BY sort_order,item_code
        """)
    List<ConfigurationModels.DictionaryItemRow> items(long tenantId,String typeCode);
    @Select("SELECT version FROM cfg_tenant_dictionary_item WHERE tenant_id=#{tenantId} AND type_code=#{typeCode} AND item_code=#{itemCode}")
    Integer itemVersion(long tenantId,String typeCode,String itemCode);
    @Insert("INSERT INTO cfg_tenant_dictionary_item(id,tenant_id,type_code,item_code,item_label,item_value,sort_order,status) VALUES(#{id},#{tenantId},#{typeCode},#{itemCode},#{label},#{value},#{sortOrder},#{status})")
    int insertItem(long tenantId,long id,String typeCode,String itemCode,String label,String value,int sortOrder,String status);
    @Update("UPDATE cfg_tenant_dictionary_item SET item_label=#{label},item_value=#{value},sort_order=#{sortOrder},status=#{status},version=version+1 WHERE tenant_id=#{tenantId} AND type_code=#{typeCode} AND item_code=#{itemCode} AND version=#{version}")
    int updateItem(long tenantId,String typeCode,String itemCode,String label,String value,int sortOrder,String status,int version);
    @Delete("DELETE FROM cfg_tenant_dictionary_item WHERE tenant_id=#{tenantId} AND type_code=#{typeCode} AND item_code=#{itemCode} AND version=#{version}")
    int deleteItem(long tenantId,String typeCode,String itemCode,int version);
    @Select("SELECT setting_key,value_type,setting_value,version FROM cfg_tenant_setting WHERE tenant_id=#{tenantId}")
    List<ConfigurationModels.SettingRow> tenantSettings(long tenantId);
    @Select("SELECT version FROM cfg_tenant_setting WHERE tenant_id=#{tenantId} AND setting_key=#{key}")
    Integer tenantSettingVersion(long tenantId,String key);
    @Insert("INSERT INTO cfg_tenant_setting(id,tenant_id,setting_key,value_type,setting_value) VALUES(#{id},#{tenantId},#{key},#{type},#{value})")
    int insertSetting(long tenantId,long id,String key,String type,String value);
    @Update("UPDATE cfg_tenant_setting SET setting_value=#{value},version=version+1 WHERE tenant_id=#{tenantId} AND setting_key=#{key} AND version=#{version}")
    int updateSetting(long tenantId,String key,String value,int version);
}

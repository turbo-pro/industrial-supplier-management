package io.github.turbopro.ism.iam.configuration;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConfigurationMapper extends TenantScopedMapper {
    @Select("SELECT COUNT(*) FROM iam_user WHERE tenant_id=#{tenantId} AND id=#{userId} AND status='ACTIVE' AND deleted=0")
    int activeTenantUser(long tenantId,long userId);
    String ESCALATION_USER_FILTER=" FROM iam_user WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND deleted=0 AND (#{keyword}='' OR INSTR(LOWER(username),LOWER(#{keyword}))>0 OR INSTR(LOWER(display_name),LOWER(#{keyword}))>0 OR CAST(id AS CHAR)=#{keyword})";
    @Select("SELECT COUNT(*)"+ESCALATION_USER_FILTER)
    long countEscalationUsers(long tenantId,String keyword);
    @Select("SELECT id,username,display_name"+ESCALATION_USER_FILTER+" ORDER BY display_name,id LIMIT #{size} OFFSET #{offset}")
    List<ConfigurationModels.EscalationUserRow> escalationUsers(long tenantId,String keyword,int offset,int size);
    @Select("SELECT setting_value FROM cfg_tenant_setting WHERE tenant_id=#{tenantId} AND setting_key=#{key} FOR UPDATE")
    String currentSettingValue(long tenantId,String key);
    @Insert("INSERT INTO cfg_tenant_dictionary_type(tenant_id,type_code) VALUES(#{tenantId},#{typeCode}) ON DUPLICATE KEY UPDATE tenant_id=VALUES(tenant_id)")
    int ensureDictionary(long tenantId,String typeCode);
    @Select("SELECT display_name,overridden,version FROM cfg_tenant_dictionary_type WHERE tenant_id=#{tenantId} AND type_code=#{typeCode} FOR UPDATE")
    ConfigurationModels.DictionaryNameRow lockDictionary(long tenantId,String typeCode);
    @Select("SELECT display_name,overridden,version FROM cfg_tenant_dictionary_type WHERE tenant_id=#{tenantId} AND type_code=#{typeCode}")
    ConfigurationModels.DictionaryNameRow dictionaryName(long tenantId,String typeCode);
    @Update("UPDATE cfg_tenant_dictionary_type SET display_name=#{name},overridden=1,version=version+1 WHERE tenant_id=#{tenantId} AND type_code=#{typeCode} AND version=#{version}")
    int renameDictionary(long tenantId,String typeCode,String name,int version);
    @Update("UPDATE cfg_tenant_dictionary_type SET overridden=0,version=version+1 WHERE tenant_id=#{tenantId} AND type_code=#{typeCode} AND version=#{version} AND overridden=1")
    int restoreDictionaryName(long tenantId,String typeCode,int version);
    @Select("""
        SELECT CASE WHEN t.overridden=1 THEN t.id ELSE s.id END id,s.item_code,
          CASE WHEN t.overridden=1 THEN t.item_label ELSE s.item_label END item_label,
          CASE WHEN t.overridden=1 THEN t.item_value ELSE s.item_value END item_value,
          CASE WHEN t.overridden=1 THEN t.sort_order ELSE s.sort_order END sort_order,
          CASE WHEN t.overridden=1 THEN t.status ELSE s.status END status,COALESCE(t.version,0) version,
          CASE WHEN t.overridden=1 THEN 'TENANT_OVERRIDE' ELSE 'SYSTEM' END source
        FROM sys_dictionary_item s LEFT JOIN cfg_tenant_dictionary_item t
          ON t.tenant_id=#{tenantId} AND t.type_code=s.type_code AND t.item_code=s.item_code
        WHERE s.type_code=#{typeCode}
        UNION ALL
        SELECT t.id,t.item_code,t.item_label,t.item_value,t.sort_order,t.status,t.version,'TENANT_CUSTOM'
        FROM cfg_tenant_dictionary_item t LEFT JOIN sys_dictionary_item s
          ON s.type_code=t.type_code AND s.item_code=t.item_code
        WHERE t.tenant_id=#{tenantId} AND t.type_code=#{typeCode} AND t.overridden=1 AND s.id IS NULL
        ORDER BY sort_order,item_code
        """)
    List<ConfigurationModels.DictionaryItemRow> items(long tenantId,String typeCode);
    @Select("SELECT t.id,t.item_code,t.item_label,t.item_value,t.sort_order,'DISABLED' status,t.version,'TENANT_REMOVED' source FROM cfg_tenant_dictionary_item t WHERE t.tenant_id=#{tenantId} AND t.type_code=#{typeCode} AND t.overridden=0 AND NOT EXISTS(SELECT 1 FROM sys_dictionary_item s WHERE s.type_code=t.type_code AND s.item_code=t.item_code) ORDER BY t.sort_order,t.item_code")
    List<ConfigurationModels.DictionaryItemRow> removedItems(long tenantId,String typeCode);
    @Select("SELECT version FROM cfg_tenant_dictionary_item WHERE tenant_id=#{tenantId} AND type_code=#{typeCode} AND item_code=#{itemCode} FOR UPDATE")
    Integer itemVersion(long tenantId,String typeCode,String itemCode);
    @Insert("INSERT INTO cfg_tenant_dictionary_item(id,tenant_id,type_code,item_code,item_label,item_value,sort_order,status,version) VALUES(#{id},#{tenantId},#{typeCode},#{itemCode},#{label},#{value},#{sortOrder},#{status},1)")
    int insertItem(long tenantId,long id,String typeCode,String itemCode,String label,String value,int sortOrder,String status);
    @Update("UPDATE cfg_tenant_dictionary_item SET item_label=#{label},item_value=#{value},sort_order=#{sortOrder},status=#{status},overridden=1,version=version+1 WHERE tenant_id=#{tenantId} AND type_code=#{typeCode} AND item_code=#{itemCode} AND version=#{version}")
    int updateItem(long tenantId,String typeCode,String itemCode,String label,String value,int sortOrder,String status,int version);
    @Update("UPDATE cfg_tenant_dictionary_item SET overridden=0,version=version+1 WHERE tenant_id=#{tenantId} AND type_code=#{typeCode} AND item_code=#{itemCode} AND version=#{version} AND overridden=1")
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

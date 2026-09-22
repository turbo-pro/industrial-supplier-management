package io.github.turbopro.ism.iam.configuration;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemConfigurationMapper {
    @Select("SELECT type_code,type_name,tenant_extensible FROM sys_dictionary_type WHERE status='ACTIVE' ORDER BY type_code")
    List<ConfigurationModels.DictionaryTypeRow> types();
    @Select("SELECT type_code,type_name,tenant_extensible FROM sys_dictionary_type WHERE type_code=#{typeCode} AND status='ACTIVE'")
    ConfigurationModels.DictionaryTypeRow type(String typeCode);
    @Select("SELECT COUNT(*) FROM sys_dictionary_item WHERE type_code=#{typeCode} AND item_code=#{itemCode}")
    int systemItemExists(String typeCode,String itemCode);
    @Select("SELECT value_type FROM cfg_setting_definition WHERE setting_key=#{key} AND status='ACTIVE'")
    String settingType(String key);
    @Select("SELECT setting_key,value_type,default_value FROM cfg_setting_definition WHERE status='ACTIVE' ORDER BY setting_key")
    List<ConfigurationModels.SettingDefinitionRow> settings();
}

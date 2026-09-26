package io.github.turbopro.ism.iam.configuration;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.net.URI;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConfigurationService {
    private final ConfigurationMapper mapper;private final SystemConfigurationMapper systemMapper;private final OperationIdGenerator ids;
    public ConfigurationService(ConfigurationMapper mapper,SystemConfigurationMapper systemMapper,OperationIdGenerator ids){this.mapper=mapper;this.systemMapper=systemMapper;this.ids=ids;}
    public List<ConfigurationModels.DictionaryTypeView> dictionaries(){long tenantId=tenant();return systemMapper.types().stream().map(type->view(tenantId,type)).toList();}
    public ConfigurationModels.DictionaryTypeView dictionary(String code){long tenantId=tenant();return view(tenantId,requireType(tenantId,code));}
    @Transactional
    public ConfigurationModels.DictionaryTypeView upsertItem(String typeCode,ConfigurationModels.UpsertDictionaryItem command){
        long tenantId=tenant();var type=requireType(tenantId,typeCode);boolean system=systemMapper.systemItemExists(typeCode,command.code())>0;
        if(!system&&!type.tenantExtensible())throw new ApiException(ConfigurationErrorCode.DICTIONARY_NOT_EXTENSIBLE);
        String status=command.status()==null?"ACTIVE":command.status();Integer current=mapper.itemVersion(tenantId,typeCode,command.code());
        if(current==null){if(command.version()!=0)throw new ApiException(CommonErrorCode.CONFLICT);mapper.insertItem(tenantId,ids.nextId(),typeCode,command.code(),command.label(),command.value(),command.sortOrder(),status);}
        else if(current!=command.version()||mapper.updateItem(tenantId,typeCode,command.code(),command.label(),command.value(),command.sortOrder(),status,command.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        return view(tenantId,type);
    }
    @Transactional
    public void removeItem(String typeCode,String itemCode,int version){long tenantId=tenant();requireType(tenantId,typeCode);if(mapper.deleteItem(tenantId,typeCode,itemCode,version)!=1)throw new ApiException(CommonErrorCode.CONFLICT);}
    public List<ConfigurationModels.SettingView> settings(){var overrides=mapper.tenantSettings(tenant()).stream().collect(Collectors.toMap(ConfigurationModels.SettingRow::settingKey,Function.identity()));return systemMapper.settings().stream().map(definition->{var value=overrides.get(definition.settingKey());return value==null?new ConfigurationModels.SettingView(definition.settingKey(),definition.valueType(),definition.defaultValue(),0):new ConfigurationModels.SettingView(value.settingKey(),value.valueType(),value.settingValue(),value.version());}).toList();}
    @Transactional
    public ConfigurationModels.SettingView updateSetting(String key,ConfigurationModels.UpdateSetting command){
        long tenantId=tenant();String type=systemMapper.settingType(key);
        if(type==null)throw new ApiException(ConfigurationErrorCode.UNKNOWN_SETTING);
        validate(type,command.value());validateKey(key,command.value());
        Integer current=mapper.tenantSettingVersion(tenantId,key);
        if(current==null){
            if(command.version()!=0)throw new ApiException(CommonErrorCode.CONFLICT);
            try{mapper.insertSetting(tenantId,ids.nextId(),key,type,command.value());}
            catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT);}
        }else if(current!=command.version()||mapper.updateSetting(tenantId,key,command.value(),command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        return settings().stream().filter(item->item.key().equals(key)).findFirst().orElseThrow();
    }
    private void validate(String type,String value){try{switch(type){case "INTEGER"->{if(Long.parseLong(value)<0)throw new IllegalArgumentException();}case "URL"->{if(!value.isBlank()){URI uri=URI.create(value);if(!value.startsWith("/")&&!List.of("http","https").contains(uri.getScheme()))throw new IllegalArgumentException();}}default->{}}}catch(Exception exception){throw new ApiException(ConfigurationErrorCode.INVALID_SETTING_VALUE);}}
    private void validateKey(String key,String value){
        if("restriction.watchPeriodDays".equals(key)){
            try{long days=Long.parseLong(value);if(days<1||days>3650)throw new IllegalArgumentException();}
            catch(IllegalArgumentException e){throw new ApiException(ConfigurationErrorCode.INVALID_SETTING_VALUE);}
        }
    }
    private ConfigurationModels.DictionaryTypeView view(long tenantId,ConfigurationModels.DictionaryTypeRow type){return new ConfigurationModels.DictionaryTypeView(type.typeCode(),type.typeName(),type.tenantExtensible(),mapper.items(tenantId,type.typeCode()).stream().map(item->new ConfigurationModels.DictionaryItemView(Long.toString(item.id()),item.itemCode(),item.itemLabel(),item.itemValue(),item.sortOrder(),item.status(),item.version(),item.source())).toList());}
    private ConfigurationModels.DictionaryTypeRow requireType(long tenantId,String code){var type=systemMapper.type(code);if(type==null)throw new ApiException(ConfigurationErrorCode.UNKNOWN_DICTIONARY);return type;}
    private long tenant(){return TenantContext.require().tenantId();}
}

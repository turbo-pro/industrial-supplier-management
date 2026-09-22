package io.github.turbopro.ism.iam.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ConfigurationModels {
    private ConfigurationModels() {}
    public record DictionaryTypeView(String code,String name,boolean tenantExtensible,List<DictionaryItemView> items) {}
    public record DictionaryItemView(String id,String code,String label,String value,int sortOrder,String status,int version,String source) {}
    public record UpsertDictionaryItem(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") String code,
        @NotBlank @Size(max=100) String label,@NotBlank @Size(max=200) String value,int sortOrder,
        @Pattern(regexp="ACTIVE|DISABLED") String status,@PositiveOrZero int version) {}
    public record SettingView(String key,String valueType,String value,int version) {}
    public record UpdateSetting(@NotNull @Size(max=2000) String value,@PositiveOrZero int version) {}
    public record DictionaryTypeRow(String typeCode,String typeName,boolean tenantExtensible) {}
    public record DictionaryItemRow(Long id,String itemCode,String itemLabel,String itemValue,int sortOrder,String status,int version,String source) {}
    public record SettingRow(String settingKey,String valueType,String settingValue,int version) {}
    public record SettingDefinitionRow(String settingKey,String valueType,String defaultValue) {}
}

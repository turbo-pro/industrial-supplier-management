package io.github.turbopro.ism.iam.configuration;

import io.github.turbopro.ism.common.api.error.ErrorCode;
public enum ConfigurationErrorCode implements ErrorCode {
    UNKNOWN_DICTIONARY("CFG_001","字典类型不存在",404),
    DICTIONARY_NOT_EXTENSIBLE("CFG_002","该系统字典不允许租户扩展条目",422),
    UNKNOWN_SETTING("CFG_003","配置键不存在或不允许修改",404),
    INVALID_SETTING_VALUE("CFG_004","配置值格式不正确",422);
    private final String code;private final String message;private final int status;
    ConfigurationErrorCode(String code,String message,int status){this.code=code;this.message=message;this.status=status;}
    public String code(){return code;}public String defaultMessage(){return message;}public int httpStatus(){return status;}public boolean retryable(){return false;}
}

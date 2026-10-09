package io.github.turbopro.ism.iam.console;

import io.github.turbopro.ism.common.api.error.ErrorCode;

public enum ConsoleUserErrorCode implements ErrorCode {
    DUPLICATE("PLATFORM_USER_DUPLICATE","平台用户名已存在",409),
    SELF_DISABLE("PLATFORM_USER_SELF_DISABLE","不能停用当前登录账号",409),
    LAST_ADMIN("PLATFORM_USER_LAST_ADMIN","不能停用最后一名有效平台管理员",409);

    private final String code,message; private final int status;
    ConsoleUserErrorCode(String code,String message,int status){this.code=code;this.message=message;this.status=status;}
    public String code(){return code;} public String defaultMessage(){return message;}
    public int httpStatus(){return status;} public boolean retryable(){return false;}
}

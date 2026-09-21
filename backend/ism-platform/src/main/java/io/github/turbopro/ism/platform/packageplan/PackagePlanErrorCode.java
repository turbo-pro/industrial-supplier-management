package io.github.turbopro.ism.platform.packageplan;

import io.github.turbopro.ism.common.api.error.ErrorCode;

public enum PackagePlanErrorCode implements ErrorCode {
    DUPLICATE("PLATFORM_PACKAGE_DUPLICATE", "套餐代码或版本号已存在", 409),
    IMMUTABLE("PLATFORM_PACKAGE_VERSION_IMMUTABLE", "已发布套餐版本不可修改", 409),
    INVALID("PLATFORM_PACKAGE_VERSION_INVALID", "套餐版本校验未通过", 422),
    QUOTA_EXCEEDED("PLATFORM_QUOTA_EXCEEDED", "租户额度已达到上限", 422),
    MODULE_UNAVAILABLE("PLATFORM_MODULE_UNAVAILABLE", "模块未被套餐授权", 403);
    private final String code; private final String message; private final int status;
    PackagePlanErrorCode(String code, String message, int status) { this.code=code; this.message=message; this.status=status; }
    public String code(){return code;} public String defaultMessage(){return message;}
    public int httpStatus(){return status;} public boolean retryable(){return false;}
}

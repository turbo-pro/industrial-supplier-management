package io.github.turbopro.ism.platform.tenant;

import io.github.turbopro.ism.common.api.error.ErrorCode;

public enum TenantErrorCode implements ErrorCode {
    DUPLICATE("PLATFORM_TENANT_DUPLICATE", "租户编码已存在", 409),
    INVALID_STATE("PLATFORM_TENANT_INVALID_STATE", "租户当前状态不允许此操作", 409),
    PACKAGE_UNAVAILABLE("PLATFORM_TENANT_PACKAGE_UNAVAILABLE", "套餐版本不存在或尚未发布", 422);

    private final String code;
    private final String message;
    private final int status;

    TenantErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() { return code; }
    public String defaultMessage() { return message; }
    public int httpStatus() { return status; }
    public boolean retryable() { return false; }
}

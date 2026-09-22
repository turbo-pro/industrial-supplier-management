package io.github.turbopro.ism.iam.access;

import io.github.turbopro.ism.common.api.error.ErrorCode;

public enum AccessErrorCode implements ErrorCode {
    DUPLICATE("IAM_ACCESS_DUPLICATE", "角色编码或用户名已存在", 409),
    INVALID_GRANT("IAM_ACCESS_INVALID_GRANT", "授权项不存在或不属于当前租户", 422),
    BUILT_IN_ROLE("IAM_BUILT_IN_ROLE_PROTECTED", "内置角色不能停用", 409);

    private final String code; private final String message; private final int status;
    AccessErrorCode(String code, String message, int status) { this.code=code;this.message=message;this.status=status; }
    public String code(){return code;} public String defaultMessage(){return message;}
    public int httpStatus(){return status;} public boolean retryable(){return false;}
}

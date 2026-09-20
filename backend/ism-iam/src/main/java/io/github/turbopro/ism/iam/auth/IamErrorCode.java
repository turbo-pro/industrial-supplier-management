package io.github.turbopro.ism.iam.auth;

import io.github.turbopro.ism.common.api.error.ErrorCode;

public enum IamErrorCode implements ErrorCode {
    INVALID_CREDENTIALS("IAM_INVALID_CREDENTIALS", "租户、用户名或密码不正确", 401, false),
    ACCOUNT_LOCKED("IAM_ACCOUNT_LOCKED", "账号已临时锁定，请稍后重试", 423, false),
    PASSWORD_CHANGE_REQUIRED("IAM_PASSWORD_CHANGE_REQUIRED", "请先修改密码", 403, false),
    TOKEN_INVALID("IAM_UNAUTHENTICATED_TOKEN_INVALID", "登录状态已失效，请重新登录", 401, false),
    TOKEN_REUSED("IAM_TOKEN_REUSED", "检测到刷新令牌重复使用，请重新登录", 401, false),
    PASSWORD_POLICY("IAM_PASSWORD_POLICY_VIOLATION", "新密码不符合安全要求", 400, false);

    private final String code;
    private final String message;
    private final int status;
    private final boolean retryable;

    IamErrorCode(String code, String message, int status, boolean retryable) {
        this.code = code;
        this.message = message;
        this.status = status;
        this.retryable = retryable;
    }

    public String code() { return code; }
    public String defaultMessage() { return message; }
    public int httpStatus() { return status; }
    public boolean retryable() { return retryable; }
}

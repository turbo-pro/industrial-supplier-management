package io.github.turbopro.ism.common.api.error;

public enum CommonErrorCode implements ErrorCode {
    VALIDATION_FAILED("COMMON_VALIDATION_FAILED", "请求参数校验失败", 400, false),
    MALFORMED_REQUEST("COMMON_VALIDATION_MALFORMED_REQUEST", "请求内容格式不正确", 400, false),
    UNAUTHENTICATED("IAM_UNAUTHENTICATED", "请先登录或重新登录", 401, false),
    FORBIDDEN("IAM_FORBIDDEN", "无权执行此操作", 403, false),
    NOT_FOUND("COMMON_NOT_FOUND", "请求的资源不存在", 404, false),
    METHOD_NOT_ALLOWED("COMMON_METHOD_NOT_ALLOWED", "请求方法不受支持", 405, false),
    CONFLICT("COMMON_STATE_CONFLICT", "当前状态不允许执行此操作", 409, false),
    MEDIA_TYPE_NOT_SUPPORTED("COMMON_MEDIA_TYPE_NOT_SUPPORTED", "请求内容类型不受支持", 415, false),
    RULE_BLOCKED("COMMON_RULE_BLOCKED", "业务规则阻止了此操作", 422, false),
    RATE_LIMITED("COMMON_RATE_LIMITED", "请求过于频繁，请稍后重试", 429, true),
    EXTERNAL_UNAVAILABLE("COMMON_EXTERNAL_UNAVAILABLE", "外部服务暂时不可用", 503, true),
    INTERNAL_ERROR("COMMON_INTERNAL_ERROR", "系统暂时无法处理请求", 500, true);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;
    private final boolean retryable;

    CommonErrorCode(String code, String defaultMessage, int httpStatus, boolean retryable) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public boolean retryable() {
        return retryable;
    }
}

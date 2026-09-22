package io.github.turbopro.ism.iam.organization;

import io.github.turbopro.ism.common.api.error.ErrorCode;

public enum OrganizationErrorCode implements ErrorCode {
    DUPLICATE("IAM_ORGANIZATION_DUPLICATE", "组织编码已存在", 409),
    INVALID_HIERARCHY("IAM_ORGANIZATION_INVALID_HIERARCHY", "组织层级不合法", 422),
    NOT_SWITCHABLE("IAM_ORGANIZATION_NOT_SWITCHABLE", "当前用户无权切换到该组织", 403),
    HAS_ACTIVE_CHILDREN("IAM_ORGANIZATION_HAS_CHILDREN", "存在有效下级组织，不能停用", 409);

    private final String code;
    private final String message;
    private final int status;

    OrganizationErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() { return code; }
    public String defaultMessage() { return message; }
    public int httpStatus() { return status; }
    public boolean retryable() { return false; }
}

package io.github.turbopro.ism.iam.site;
import io.github.turbopro.ism.common.api.error.ErrorCode;
public enum SiteContextError implements ErrorCode {
    INVALID("IAM_SITE_CONTEXT_INVALID","场站上下文无效、已过期或不属于当前登录，请重新选取",403),
    UNAVAILABLE("IAM_SITE_CONTEXT_UNAVAILABLE","当前场站授权已失效，请重新选取",403),
    UNSUPPORTED("IAM_SITE_CONTEXT_UNSUPPORTED","当前接口尚未接入场站上下文，请勿附带场站请求头",422);
    private final String code,message;private final int status;
    SiteContextError(String code,String message,int status){this.code=code;this.message=message;this.status=status;}
    public String code(){return code;}public String defaultMessage(){return message;}public int httpStatus(){return status;}public boolean retryable(){return false;}
}

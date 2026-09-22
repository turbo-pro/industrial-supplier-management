package io.github.turbopro.ism.resource.file;

import io.github.turbopro.ism.common.api.error.ErrorCode;

public enum FileErrorCode implements ErrorCode {
    FILE_TOO_LARGE("FILE_001","文件超过允许大小",413),UPLOAD_EXPIRED("FILE_002","上传会话已过期",410),
    INVALID_CHUNK("FILE_003","分片编号、大小或摘要不正确",422),UPLOAD_INCOMPLETE("FILE_004","上传分片不完整",409),
    STORAGE_UNAVAILABLE("FILE_005","对象存储暂不可用",503);
    private final String code;private final String message;private final int status;
    FileErrorCode(String code,String message,int status){this.code=code;this.message=message;this.status=status;}
    public String code(){return code;}public String defaultMessage(){return message;}public int httpStatus(){return status;}public boolean retryable(){return status==503;}
}

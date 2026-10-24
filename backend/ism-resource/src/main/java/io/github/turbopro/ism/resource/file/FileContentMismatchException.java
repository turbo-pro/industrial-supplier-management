package io.github.turbopro.ism.resource.file;

/** Client-supplied size/digest differs from assembled bytes, not a transient storage outage. */
public class FileContentMismatchException extends FileStorageException {
    public FileContentMismatchException(){super("文件大小或 SHA-256 校验失败");}
}

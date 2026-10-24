package io.github.turbopro.ism.resource.file;

import java.io.InputStream;

public interface ObjectStorage {
    String provider();
    void writeChunk(long tenantId,long uploadId,int index,byte[] content);
    StoredObject complete(long tenantId,long uploadId,int totalChunks,String expectedSha256,long expectedSize);
    /** Verify/assemble without removing chunks. Caller cleans chunks only after transaction commit. */
    default StoredObject prepareUpload(long tenantId,long uploadId,int totalChunks,String expectedSha256,long expectedSize){throw new FileStorageException("存储不支持可回滚上传提交");}
    StoredObject storeGenerated(long tenantId,String sha256,byte[] content);
    InputStream open(String objectKey);
    void discardUpload(long tenantId,long uploadId);
    record StoredObject(String objectKey,long size,String sha256){}
}

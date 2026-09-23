package io.github.turbopro.ism.resource.file;

import java.io.InputStream;

public interface ObjectStorage {
    String provider();
    void writeChunk(long tenantId,long uploadId,int index,byte[] content);
    StoredObject complete(long tenantId,long uploadId,int totalChunks,String expectedSha256,long expectedSize);
    StoredObject storeGenerated(long tenantId,String sha256,byte[] content);
    InputStream open(String objectKey);
    void discardUpload(long tenantId,long uploadId);
    record StoredObject(String objectKey,long size,String sha256){}
}

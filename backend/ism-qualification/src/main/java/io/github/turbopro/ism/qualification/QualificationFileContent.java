package io.github.turbopro.ism.qualification;

import java.io.InputStream;

/** Storage boundary. Caller must first authorize the business record referencing this file. */
public interface QualificationFileContent {
    Download open(long fileId);
    record Download(String name,long size,InputStream stream) {}
}

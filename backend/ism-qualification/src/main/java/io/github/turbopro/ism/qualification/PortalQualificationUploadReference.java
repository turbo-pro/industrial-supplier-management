package io.github.turbopro.ism.qualification;

/** Transactional business attachment boundary; validates the current portal account's completed upload. */
public interface PortalQualificationUploadReference {
    Ready requireReady(long uploadId);
    record Ready(long fileId,String fileName) {}
}

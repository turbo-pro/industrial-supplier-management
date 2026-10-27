package io.github.turbopro.ism.qualification;

/** Shared QUALIFICATION capability upload boundary (certificates and admission materials). Validates current account ownership, completion, expiry and active file. Consumption is recorded by the business caller. */
public interface PortalQualificationUploadReference {
    Ready requireReady(long uploadId);
    record Ready(long fileId,String fileName) {}
}

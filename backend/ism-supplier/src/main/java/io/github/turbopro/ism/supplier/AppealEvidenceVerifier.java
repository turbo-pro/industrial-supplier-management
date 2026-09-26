package io.github.turbopro.ism.supplier;

/** Resource module implements this boundary without reversing module dependencies. */
public interface AppealEvidenceVerifier {
    boolean available(long fileId);
}

package io.github.turbopro.ism.qualification;

/** Implemented by the resource module so qualification stays independent of storage. */
public interface QualificationFileReference {
    boolean active(long fileId);
}

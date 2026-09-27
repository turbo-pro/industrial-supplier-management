package io.github.turbopro.ism.supplier;

/** IAM supplies current, same-tenant active account eligibility without sharing its mapper. */
public interface ExitAssigneeVerifier {
    boolean active(long userId);
}

package io.github.turbopro.ism.common.infrastructure.portal;

/** Recheck current supplier eligibility under its row lock in the current tenant. */
public interface PortalSupplierAccessGate {
    void lockAvailable(long supplierId);
    /** Lock an existing enterprise for resolving a pending request, even when no longer available. */
    void lockForResolution(long supplierId);
}

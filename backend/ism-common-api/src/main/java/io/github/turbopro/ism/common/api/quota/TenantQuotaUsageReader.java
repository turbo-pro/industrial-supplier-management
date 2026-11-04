package io.github.turbopro.ism.common.api.quota;

/** Trusted aggregate-only domain port, never a supplier/body lookup or an authorization grant. */
public interface TenantQuotaUsageReader {
    String quotaCode();
    long used(long tenantId);
}

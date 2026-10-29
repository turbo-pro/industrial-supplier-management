package io.github.turbopro.ism.common.infrastructure.tenant;
/** Platform-owned classification; consumers must not query platform persistence directly. */
public interface TenantSiteOperationPolicy {
    boolean requiresSiteContext(long tenantId);
}

package io.github.turbopro.ism.common.api.quota;

import java.util.function.LongSupplier;

/** Internal write boundary. The caller must own a transaction and count its own domain after the quota lock. */
public interface TenantCreationQuota {
    void reserve(String quotaCode, long delta, LongSupplier currentUsed);
}

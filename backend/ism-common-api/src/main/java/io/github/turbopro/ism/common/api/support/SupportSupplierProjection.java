package io.github.turbopro.ism.common.api.support;

import java.util.Set;

/** Internal supplier projection for an already validated support grant. */
public interface SupportSupplierProjection {
    record Row(long id, String code, String name, String type, String status) {}

    Row find(long supplierId, boolean tenantAll, Set<String> organizations);
}

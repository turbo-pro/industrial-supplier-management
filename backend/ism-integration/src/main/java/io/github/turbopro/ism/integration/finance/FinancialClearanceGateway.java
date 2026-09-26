package io.github.turbopro.ism.integration.finance;

import java.time.Instant;

/**
 * Read-only ERP boundary. Implementations must verify the tenant/supplier mapping
 * and cover all unsettled obligations, not merely a net outstanding balance.
 */
public interface FinancialClearanceGateway {
    Result check(long tenantId, long supplierId);

    enum Status { CLEAR, OPEN, UNKNOWN }

    record Result(Status status, long openItems, Instant checkedAt, String evidenceReference) {
        public Result {
            if (status == null || openItems < 0
                    || (status == Status.CLEAR && openItems != 0)
                    || (status == Status.OPEN && openItems == 0)) {
                throw new IllegalArgumentException("Inconsistent financial clearance result");
            }
        }
    }
}

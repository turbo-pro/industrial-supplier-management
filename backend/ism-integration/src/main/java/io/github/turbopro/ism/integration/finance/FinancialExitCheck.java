package io.github.turbopro.ism.integration.finance;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.supplier.SupplierExitCheck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
public class FinancialExitCheck implements SupplierExitCheck {
    private final FinancialClearanceGateway gateway;
    private final Duration maxAge;
    private final Clock clock;

    @Autowired
    public FinancialExitCheck(List<FinancialClearanceGateway> gateways,
                              @Value("${ism.integration.finance.clearance-max-age:PT15M}") Duration maxAge) {
        this(gateways, maxAge, Clock.systemUTC());
    }

    FinancialExitCheck(List<FinancialClearanceGateway> gateways, Duration maxAge, Clock clock) {
        if (gateways.size() > 1 || maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("One financial gateway and a positive freshness limit are required");
        }
        this.gateway = gateways.isEmpty() ? null : gateways.get(0);
        this.maxAge = maxAge;
        this.clock = clock;
    }

    @Override public List<Blocker> blockers(long supplierId) {
        if (gateway == null) return unknown();
        var identity = TenantContext.require();
        final FinancialClearanceGateway.Result result;
        try {
            result = gateway.check(identity.tenantId(), supplierId);
        } catch (RuntimeException unavailable) {
            return unknown();
        }
        var now = clock.instant();
        if (result == null || result.status() == FinancialClearanceGateway.Status.UNKNOWN
                || result.checkedAt() == null || result.checkedAt().isAfter(now)
                || result.checkedAt().isBefore(now.minus(maxAge))
                || result.evidenceReference() == null || result.evidenceReference().isBlank()) return unknown();
        return List.of(new Blocker("OPEN_FINANCIAL", "财务未结事项",
                result.openItems(), "/suppliers/master"));
    }

    private List<Blocker> unknown() {
        return List.of(new Blocker("FINANCIAL_UNKNOWN", "财务结清未接入、不可用或待核验", 1, "/suppliers/master"));
    }
}

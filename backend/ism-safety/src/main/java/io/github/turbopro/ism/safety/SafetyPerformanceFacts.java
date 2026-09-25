package io.github.turbopro.ism.safety;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class SafetyPerformanceFacts {
    private final SafetyIssueMapper mapper;
    public SafetyPerformanceFacts(SafetyIssueMapper mapper) { this.mapper = mapper; }
    public Counts forSupplier(long supplierId, LocalDate start, LocalDate end) {
        var row = mapper.performanceFacts(TenantContext.require().tenantId(), supplierId, start, end);
        return new Counts(row.total(), row.openCount());
    }
    public record Counts(int total, int open) {}
}

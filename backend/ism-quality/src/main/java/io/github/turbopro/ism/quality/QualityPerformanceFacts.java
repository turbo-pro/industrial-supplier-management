package io.github.turbopro.ism.quality;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class QualityPerformanceFacts {
    private final QualityNcrMapper mapper;
    public QualityPerformanceFacts(QualityNcrMapper mapper) { this.mapper = mapper; }
    public Counts forSupplier(long supplierId, LocalDate start, LocalDate end) {
        var row = mapper.performanceFacts(TenantContext.require().tenantId(), supplierId, start, end);
        return new Counts(row.total(), row.openCount());
    }
    public record Counts(int total, int open) {}
}

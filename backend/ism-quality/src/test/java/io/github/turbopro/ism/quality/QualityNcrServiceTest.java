package io.github.turbopro.ism.quality;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualityNcrServiceTest {
    private final QualityNcrMapper mapper = mock(QualityNcrMapper.class);
    private final QualityNcrService service = new QualityNcrService(mapper,
            mock(OperationIdGenerator.class), mock(AuditService.class));

    @Test void defectiveQuantityCannotExceedInspectedQuantity() {
        when(mapper.activeProject(10, 40)).thenReturn(new QualityNcrModels.ProjectRef(20, 30));
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            assertThrows(ApiException.class, () -> service.create(command(new BigDecimal("10"), new BigDecimal("11"))));
        }
        verify(mapper, never()).activeFile(10, 88);
    }

    @Test void evidenceMustBelongToTenantAndBeActive() {
        when(mapper.activeProject(10, 40)).thenReturn(new QualityNcrModels.ProjectRef(20, 30));
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            assertThrows(ApiException.class, () -> service.create(command(new BigDecimal("10"), new BigDecimal("2"))));
        }
        verify(mapper).activeFile(10, 88);
    }

    private QualityNcrModels.Create command(BigDecimal inspected, BigDecimal defective) {
        return new QualityNcrModels.Create("NCR-1", "40", "焊缝缺陷", QualityNcrModels.Category.PROCESS,
                QualityNcrModels.Severity.HIGH, "检验不合格", LocalDate.now(), inspected, defective,
                "件", "88", LocalDate.now().plusDays(7), "7");
    }
    private AuthorizationContext.Scope auth() {
        return AuthorizationContext.open(new PermissionSnapshot(Set.of(),
                Map.of("quality:ncr", DataScope.all()), Set.of()));
    }
}

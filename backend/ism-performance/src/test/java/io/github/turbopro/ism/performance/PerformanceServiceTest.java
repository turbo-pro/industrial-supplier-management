package io.github.turbopro.ism.performance;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import io.github.turbopro.ism.supplier.SupplierReferenceService;
import io.github.turbopro.ism.resource.file.FileReferenceService;
import io.github.turbopro.ism.quality.QualityPerformanceFacts;
import io.github.turbopro.ism.safety.SafetyPerformanceFacts;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PerformanceServiceTest {
    private final PerformanceMapper mapper = mock(PerformanceMapper.class);
    private final OperationIdGenerator ids = mock(OperationIdGenerator.class);
    private final SupplierReferenceService suppliers = mock(SupplierReferenceService.class);
    private final FileReferenceService files = mock(FileReferenceService.class);
    private final QualityPerformanceFacts quality = mock(QualityPerformanceFacts.class);
    private final SafetyPerformanceFacts safety = mock(SafetyPerformanceFacts.class);
    private final PerformanceService service = new PerformanceService(mapper, ids, mock(AuditService.class),
            suppliers, files, quality, safety);

    @Test void defaultRuleAndInvalidWeights() {
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            assertEquals(35, service.rule().qualityWeight());
            assertFalse(service.rule().customized());
            assertThrows(ApiException.class, () -> service.saveRule(new PerformanceModels.SaveRule(40, 25, 25, 15, 0)));
        }
        verify(mapper, never()).insertRule(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong());
    }

    @Test void weightedTotalAndGradeAreComputedOnServer() {
        when(suppliers.active(40)).thenReturn(new SupplierReferenceService.Reference(20, "S-1", "供应商"));
        when(files.active(anyLong())).thenReturn(true);
        when(quality.forSupplier(eq(40L), any(), any())).thenReturn(new QualityPerformanceFacts.Counts(2, 1));
        when(safety.forSupplier(eq(40L), any(), any())).thenReturn(new SafetyPerformanceFacts.Counts(3, 1));
        when(ids.nextId()).thenReturn(100L, 101L);
        when(mapper.get(10, 100)).thenReturn(row(100, "DRAFT", 7));
        when(mapper.items(10, 100)).thenReturn(List.of());
        var command = command(List.of(item(PerformanceModels.Dimension.QUALITY, 90),
                item(PerformanceModels.Dimension.DELIVERY, 80),
                item(PerformanceModels.Dimension.SAFETY, 70),
                item(PerformanceModels.Dimension.SERVICE, 60)));
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            assertEquals("100", service.create(command).id());
        }
        verify(mapper).insert(eq(100L), eq(10L), eq(20L), eq(40L), eq("S-1"), eq("供应商"), any(), any(),
                eq(new BigDecimal("78.00")), eq("C"), eq(2), eq(1), eq(3), eq(1), eq(7L));
        verify(mapper, times(4)).insertItem(eq(100L), eq(10L), anyString(), anyInt(), any(), anyString(), eq(88L));
    }

    @Test void duplicateDimensionCannotBeScored() {
        when(suppliers.active(40)).thenReturn(new SupplierReferenceService.Reference(20, "S-1", "供应商"));
        var repeated = command(List.of(item(PerformanceModels.Dimension.QUALITY, 90),
                item(PerformanceModels.Dimension.QUALITY, 80),
                item(PerformanceModels.Dimension.SAFETY, 70),
                item(PerformanceModels.Dimension.SERVICE, 60)));
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            assertThrows(ApiException.class, () -> service.create(repeated));
        }
    }

    @Test void rejectedEvaluationKeepsOriginalWeightsWhenRevised() {
        when(mapper.get(10, 100)).thenReturn(row(100, "REJECTED", 7));
        when(mapper.items(10, 100)).thenReturn(List.of(
                new PerformanceModels.ItemRow("QUALITY", 50, BigDecimal.ZERO, "旧", 88),
                new PerformanceModels.ItemRow("DELIVERY", 20, BigDecimal.ZERO, "旧", 88),
                new PerformanceModels.ItemRow("SAFETY", 20, BigDecimal.ZERO, "旧", 88),
                new PerformanceModels.ItemRow("SERVICE", 10, BigDecimal.ZERO, "旧", 88)));
        when(files.active(88)).thenReturn(true);
        when(quality.forSupplier(eq(40L), any(), any())).thenReturn(new QualityPerformanceFacts.Counts(2, 1));
        when(safety.forSupplier(eq(40L), any(), any())).thenReturn(new SafetyPerformanceFacts.Counts(3, 1));
        when(mapper.update(eq(10L), eq(100L), any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), eq(7L), eq(0)))
                .thenReturn(1);
        var command = command(List.of(item(PerformanceModels.Dimension.QUALITY, 90),
                item(PerformanceModels.Dimension.DELIVERY, 80),
                item(PerformanceModels.Dimension.SAFETY, 70),
                item(PerformanceModels.Dimension.SERVICE, 60)));
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            service.update(100, command);
        }
        verify(mapper).update(eq(10L), eq(100L), eq(new BigDecimal("81.00")), eq("B"),
                eq(2), eq(1), eq(3), eq(1), eq(7L), eq(0));
    }

    @Test void creatorCannotApproveOwnEvaluation() {
        when(mapper.get(10, 100)).thenReturn(row(100, "SUBMITTED", 7));
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            assertThrows(ApiException.class, () -> service.review(100,
                    new PerformanceModels.Review(PerformanceModels.Decision.APPROVE, "通过", 0)));
        }
        verify(mapper, never()).review(anyLong(), anyLong(), anyString(), any(), anyLong(), anyInt());
    }

    private PerformanceModels.ScoreItem item(PerformanceModels.Dimension dimension, int score) {
        return new PerformanceModels.ScoreItem(dimension, BigDecimal.valueOf(score), "有据可查", "88");
    }
    private PerformanceModels.SaveEvaluation command(List<PerformanceModels.ScoreItem> items) {
        return new PerformanceModels.SaveEvaluation("40", LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(1), items, 0);
    }
    private PerformanceModels.Row row(long id, String status, long createdBy) {
        return new PerformanceModels.Row(id, 20, 40, "S-1", "供应商",
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(1), new BigDecimal("78.00"), "C",
                status, 2, 1, 3, 1, null, null, null, null,
                createdBy, 0, LocalDateTime.now(), LocalDateTime.now());
    }
    private AuthorizationContext.Scope auth() {
        return AuthorizationContext.open(new PermissionSnapshot(Set.of(),
                Map.of("performance:evaluation", DataScope.all()), Set.of()));
    }
}

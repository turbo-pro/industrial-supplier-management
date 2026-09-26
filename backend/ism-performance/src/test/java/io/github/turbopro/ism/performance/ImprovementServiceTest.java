package io.github.turbopro.ism.performance;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import io.github.turbopro.ism.resource.file.FileReferenceService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImprovementServiceTest {
    private final ImprovementMapper mapper=mock(ImprovementMapper.class);
    private final PerformanceService evaluations=mock(PerformanceService.class);
    private final FileReferenceService files=mock(FileReferenceService.class);
    private final OperationIdGenerator ids=mock(OperationIdGenerator.class);
    private final ImprovementService service=new ImprovementService(mapper,evaluations,files,ids,mock(AuditService.class));

    @Test void onlyApprovedEvaluationCanStartAndOnlyOnePlanIsAllowed() {
        when(evaluations.get(80)).thenReturn(evaluation(PerformanceModels.Status.DRAFT));
        try(var tenant=TenantContext.open(10,7)) {
            assertThrows(ApiException.class,()->service.create(80,new ImprovementModels.Create("根因","措施",LocalDate.now().plusDays(7))));
        }
        verify(mapper,never()).insert(anyLong(),anyLong(),anyLong(),anyString(),anyString(),any(),anyLong());
    }
    @Test void exitedSupplierCannotStartImprovement() {
        when(evaluations.get(80)).thenReturn(evaluation(PerformanceModels.Status.APPROVED));
        when(mapper.lockSupplier(10,40)).thenReturn(null);
        try(var tenant=TenantContext.open(10,7)) {
            assertThrows(ApiException.class,()->service.create(80,new ImprovementModels.Create("根因","措施",LocalDate.now().plusDays(7))));
        }
        verify(mapper).lockSupplier(10,40);
        verify(mapper,never()).insert(anyLong(),anyLong(),anyLong(),anyString(),anyString(),any(),anyLong());
    }
    @Test void submitRequiresEvidenceAndOpenState() {
        when(evaluations.get(80)).thenReturn(evaluation(PerformanceModels.Status.APPROVED));
        when(mapper.get(10,80)).thenReturn(plan("OPEN",7));
        try(var tenant=TenantContext.open(10,7)) {
            assertThrows(ApiException.class,()->service.submit(80,new ImprovementModels.Submit("已完成","88",0)));
        }
        verify(mapper,never()).submit(anyLong(),anyLong(),anyString(),anyLong(),anyLong(),anyInt());
    }
    @Test void creatorCannotAcceptOwnSubmission() {
        when(evaluations.get(80)).thenReturn(evaluation(PerformanceModels.Status.APPROVED));
        when(mapper.get(10,80)).thenReturn(plan("SUBMITTED",7));
        try(var tenant=TenantContext.open(10,7)) {
            assertThrows(ApiException.class,()->service.review(80,new ImprovementModels.Review(ImprovementModels.Decision.ACCEPT,"验收通过",0)));
        }
        verify(mapper,never()).review(anyLong(),anyLong(),anyString(),anyString(),anyLong(),anyInt());
    }
    @Test void rejectedResultCanBeResubmitted() {
        when(evaluations.get(80)).thenReturn(evaluation(PerformanceModels.Status.APPROVED));
        when(mapper.get(10,80)).thenReturn(plan("REWORK",7),plan("SUBMITTED",7));
        when(mapper.events(10,90)).thenReturn(List.of());
        when(files.active(88)).thenReturn(true);
        when(ids.nextId()).thenReturn(91L);
        when(mapper.submit(10,80,"再次完成",88,7,2)).thenReturn(1);
        try(var tenant=TenantContext.open(10,7)) {
            assertEquals(ImprovementModels.Status.SUBMITTED,service.submit(80,new ImprovementModels.Submit("再次完成","88",2)).status());
        }
        verify(mapper).event(91,10,90,"SUBMIT","REWORK","SUBMITTED","再次完成",7);
    }
    private PerformanceModels.View evaluation(PerformanceModels.Status status) {
        return new PerformanceModels.View("80","20","40","S-1","供应商",LocalDate.now().minusMonths(1),LocalDate.now(),
            java.math.BigDecimal.valueOf(70),PerformanceModels.Grade.C,status,0,0,0,0,null,null,null,null,"7",0,null,null,List.of());
    }
    private ImprovementModels.Row plan(String status,long creator) {
        return new ImprovementModels.Row(90,80,"根因","措施",LocalDate.now().plusDays(7),status,null,null,null,null,null,creator,2,null,null);
    }
}

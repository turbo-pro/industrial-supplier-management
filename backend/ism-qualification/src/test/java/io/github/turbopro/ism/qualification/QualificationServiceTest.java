package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualificationServiceTest {
    private final QualificationMapper mapper=mock(QualificationMapper.class);
    private final QualificationService service=new QualificationService(mapper,mock(OperationIdGenerator.class),mock(AuditService.class));
    @Test void validityRequiredTypeRejectsMissingExpiry(){when(mapper.supplierOrganization(10,30)).thenReturn(20L);when(mapper.type(10,40)).thenReturn(type(true));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:qualification",DataScope.all()),Set.of()))){assertThrows(ApiException.class,()->service.create(command(false,null)));}verify(mapper,never()).insert(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),any(),anyString(),any(),any(),any(),any(),anyBoolean(),anyInt(),anyLong());}
    @Test void expiringStatusIsCalculatedWithoutMutatingRecord(){when(mapper.find(10,99)).thenReturn(row("VALID",LocalDate.now().plusDays(10),30));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:qualification",DataScope.all()),Set.of()))){assertEquals(QualificationModels.Status.EXPIRING,service.get(99).status());}}
    @Test void cannotVerifyQualificationOutsideOrganizationScope(){when(mapper.find(10,99)).thenReturn(row("DRAFT",LocalDate.now().plusYears(1),30));try(var tenant=TenantContext.open(10,7);var auth=AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("supplier:qualification",DataScope.organizations(Set.of(21L))),Set.of()))){assertThrows(ApiException.class,()->service.verify(99,new QualificationModels.VerifyCommand(QualificationModels.Decision.APPROVE,"核验通过",0)));}}
    private QualificationModels.SaveQualification command(boolean permanent,LocalDate expiry){return new QualificationModels.SaveQualification("30","40","CERT-001",null,LocalDate.now(),LocalDate.now(),expiry,permanent,null,"50",0);}
    private QualificationModels.TypeRow type(boolean validity){return new QualificationModels.TypeRow(40,"LICENSE","营业执照","LEGAL",validity,30,null,"ACTIVE",0,LocalDateTime.now());}
    private QualificationModels.Row row(String status,LocalDate expiry,int warning){return new QualificationModels.Row(99,20,30,40,null,"SUP-001","测试供应商","LICENSE","营业执照","CERT-001",null,LocalDate.now(),LocalDate.now(),expiry,false,warning,50,status,null,null,null,null,7,0,LocalDateTime.now(),LocalDateTime.now());}
}

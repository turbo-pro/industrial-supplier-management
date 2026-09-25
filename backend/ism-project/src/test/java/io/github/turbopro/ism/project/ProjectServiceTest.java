package io.github.turbopro.ism.project;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProjectServiceTest {
    private final ProjectMapper mapper=mock(ProjectMapper.class);
    private final ProjectService service=new ProjectService(mapper,mock(OperationIdGenerator.class),mock(AuditService.class));
    @Test void contractDateRangeMustBeValid(){try(var tenant=TenantContext.open(10,7);var auth=auth()){assertThrows(ApiException.class,()->service.createContract(new ProjectModels.SaveContract("C-001","测试合同","30",ProjectModels.ContractType.SERVICE,BigDecimal.TEN,"CNY",LocalDate.now(),LocalDate.now().plusDays(2),LocalDate.now(),"7","50",0)));}verify(mapper,never()).insertContract(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyString(),anyString(),anyString(),any(),anyString(),any(),any(),any(),anyLong(),anyLong());}
    @Test void projectContractMustBelongToSameSupplier(){when(mapper.activeSupplierOrganization(10,30)).thenReturn(20L);when(mapper.activeContractSupplier(10,60)).thenReturn(31L);try(var tenant=TenantContext.open(10,7);var auth=auth()){assertThrows(ApiException.class,()->service.createProject(new ProjectModels.SaveProject("P-001","测试项目","30","60",ProjectModels.ProjectType.MAINTENANCE,null,LocalDate.now(),LocalDate.now().plusDays(2),"7",null,null,0)));}verify(mapper,never()).insertProject(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),any(),anyString(),anyString(),anyString(),any(),any(),any(),anyLong(),any(),any());}
    @Test void completedProjectCannotBeRestarted(){when(mapper.project(10,99)).thenReturn(row("COMPLETED"));try(var tenant=TenantContext.open(10,7);var auth=auth()){assertThrows(ApiException.class,()->service.changeProjectStatus(99,new ProjectModels.ChangeProjectStatus(ProjectModels.ProjectStatus.ACTIVE,"重启",1)));}verify(mapper,never()).changeProjectStatus(anyLong(),anyLong(),anyLong(),anyString(),any(),anyInt());}
    private AuthorizationContext.Scope auth(){return AuthorizationContext.open(new PermissionSnapshot(Set.of(),Map.of("contract",DataScope.all(),"project",DataScope.all()),Set.of()));}
    private ProjectModels.ProjectRow row(String status){var now=LocalDateTime.now();return new ProjectModels.ProjectRow(99,20,30,null,"SUP-001","测试供应商",null,"P-001","测试项目","MAINTENANCE",null,LocalDate.now(),LocalDate.now().plusDays(1),null,null,7,null,null,status,null,7,1,now,now);}
}

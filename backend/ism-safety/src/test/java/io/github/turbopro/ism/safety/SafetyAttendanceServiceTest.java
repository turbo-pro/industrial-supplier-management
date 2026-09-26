package io.github.turbopro.ism.safety;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SafetyAttendanceServiceTest {
    private final SafetyAttendanceMapper mapper = mock(SafetyAttendanceMapper.class);
    private final SafetyCredentialMapper credentials = mock(SafetyCredentialMapper.class);
    private final OperationIdGenerator ids = mock(OperationIdGenerator.class);
    private final AuditService audit = mock(AuditService.class);
    private final io.github.turbopro.ism.supplier.SupplierReferenceService suppliers=mock(io.github.turbopro.ism.supplier.SupplierReferenceService.class);
    private final SafetyAttendanceService service = new SafetyAttendanceService(mapper, credentials, ids, audit,suppliers);
    @org.junit.jupiter.api.BeforeEach void validSupplier(){
        when(suppliers.activeForNewBusiness(30)).thenReturn(new io.github.turbopro.ism.supplier.SupplierReferenceService.Reference(20,"SUP-001","供应商"));
        when(credentials.personForEntry(10,99)).thenReturn(person("ACTIVE"));
    }
    @Test void restrictionBlocksCheckInBeforeInsert(){
        when(credentials.person(10,99)).thenReturn(person("ACTIVE"));
        when(suppliers.activeForNewBusiness(30)).thenReturn(null);
        try(var tenant=TenantContext.open(10,7);var authorization=auth()){
            assertThrows(ApiException.class,()->service.checkIn(new SafetyAttendanceModels.CheckIn("99","厂区")));
        }
        verify(mapper,never()).checkIn(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyString(),anyLong());
    }
    @Test void currentExitedPersonBlocksEntryDespiteEarlierActiveRead(){
        when(credentials.person(10,99)).thenReturn(person("ACTIVE"));
        when(credentials.personForEntry(10,99)).thenReturn(person("EXITED"));
        when(mapper.activeProject(10,40,30)).thenReturn(1);
        when(credentials.validTraining(10,99)).thenReturn(1);
        try(var tenant=TenantContext.open(10,7);var authorization=auth()){
            assertThrows(ApiException.class,()->service.checkIn(new SafetyAttendanceModels.CheckIn("99","厂区")));
        }
        verify(mapper,never()).checkIn(anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyLong(),anyString(),anyLong());
    }

    @Test void expiredTrainingBlocksCheckIn() {
        when(credentials.person(10, 99)).thenReturn(person("ACTIVE"));
        when(mapper.activeProject(10, 40, 30)).thenReturn(1);
        when(credentials.validTraining(10, 99)).thenReturn(0);
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            assertThrows(ApiException.class, () -> service.checkIn(new SafetyAttendanceModels.CheckIn("99", "一号厂区")));
        }
        verify(mapper, never()).checkIn(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test void duplicateOpenVisitReturnsConflict() {
        when(credentials.person(10, 99)).thenReturn(person("ACTIVE"));
        when(mapper.activeProject(10, 40, 30)).thenReturn(1);
        when(credentials.validTraining(10, 99)).thenReturn(1);
        when(ids.nextId()).thenReturn(100L);
        when(mapper.checkIn(100, 10, 20, 40, 30, 99, "一号厂区", 7))
                .thenThrow(new DuplicateKeyException("already open"));
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            assertThrows(ApiException.class, () -> service.checkIn(new SafetyAttendanceModels.CheckIn("99", "一号厂区")));
        }
    }

    @Test void validPersonCanCheckIn() {
        when(credentials.person(10, 99)).thenReturn(person("ACTIVE"));
        when(mapper.activeProject(10, 40, 30)).thenReturn(1);
        when(credentials.validTraining(10, 99)).thenReturn(1);
        when(ids.nextId()).thenReturn(100L);
        when(mapper.get(10, 100)).thenReturn(new SafetyAttendanceModels.Row(100, 20, 40, 30, 99,
                "P-1", "张三", "供应商", "项目", "一号厂区", LocalDateTime.now(), null, 7, null, null, 7, 0));
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            assertEquals("100", service.checkIn(new SafetyAttendanceModels.CheckIn("99", " 一号厂区 ")).id());
        }
        verify(mapper).checkIn(100, 10, 20, 40, 30, 99, "一号厂区", 7);
    }

    @Test void staleCheckOutVersionReturnsConflict() {
        when(mapper.get(10, 100)).thenReturn(new SafetyAttendanceModels.Row(100, 20, 40, 30, 99,
                "P-1", "张三", "供应商", "项目", "一号厂区", LocalDateTime.now(), null, 7, null, null, 7, 1));
        try (var tenant = TenantContext.open(10, 7); var authorization = auth()) {
            assertThrows(ApiException.class, () -> service.checkOut(100, new SafetyAttendanceModels.CheckOut(null, 0)));
        }
    }

    private SafetyCredentialModels.PersonRef person(String status) {
        return new SafetyCredentialModels.PersonRef(99, 20, 30, 40L, "P-1", "张三", null, status, 7);
    }
    @Test void restrictionDoesNotBlockCheckOut(){
        when(suppliers.activeForNewBusiness(30)).thenReturn(null);
        when(mapper.get(10,100)).thenReturn(new SafetyAttendanceModels.Row(100,20,40,30,99,"P-1","张三","供应商","项目","厂区",LocalDateTime.now(),null,7,null,null,7,0));
        when(mapper.checkOut(10,100,7,"离场",0)).thenReturn(1);
        try(var tenant=TenantContext.open(10,7);var authorization=auth()){
            assertNotNull(service.checkOut(100,new SafetyAttendanceModels.CheckOut("离场",0)));
        }
        verify(suppliers,never()).activeForNewBusiness(anyLong());
    }
    private AuthorizationContext.Scope auth() {
        return AuthorizationContext.open(new PermissionSnapshot(Set.of(),
                Map.of("safety:attendance", DataScope.all()), Set.of()));
    }
}

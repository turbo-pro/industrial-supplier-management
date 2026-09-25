package io.github.turbopro.ism.safety;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafetyCredentialServiceTest {
    private final SafetyCredentialMapper mapper = mock(SafetyCredentialMapper.class);
    private final SafetyCredentialService service = new SafetyCredentialService(
            mapper, mock(OperationIdGenerator.class), mock(AuditService.class));

    @Test
    void mismatchedSpecialWorkTypeCannotBeRegistered() {
        when(mapper.person(10, 99)).thenReturn(person("WELDING", "PENDING"));
        try (var tenant = TenantContext.open(10, 7); var auth = auth()) {
            var command = new SafetyCredentialModels.Create("CERT-1", "99",
                    SafetyCredentialModels.Kind.SPECIAL_WORK, SafetyCredentialModels.WorkType.ELECTRICAL,
                    "电工证", null, null, LocalDate.now(), LocalDate.now().plusYears(1), "88");
            assertThrows(ApiException.class, () -> service.create(command));
        }
        verify(mapper, never()).insert(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
                any(), anyString(), anyString(), any(), anyString(), any(), any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void failedTrainingDoesNotSatisfyEntryRequirement() {
        when(mapper.person(10, 99)).thenReturn(person(null, "PENDING"));
        when(mapper.validTraining(10, 99)).thenReturn(0);
        try (var tenant = TenantContext.open(10, 7); var auth = auth()) {
            var eligibility = service.eligibility(99);
            assertFalse(eligibility.trainingValid());
            assertFalse(eligibility.eligible());
            assertTrue(eligibility.specialWorkValid());
        }
    }

    private SafetyCredentialModels.PersonRef person(String workType, String status) {
        return new SafetyCredentialModels.PersonRef(99, 20, 30, null,
                "P-1", "张三", workType, status, 7);
    }

    private AuthorizationContext.Scope auth() {
        return AuthorizationContext.open(new PermissionSnapshot(Set.of(),
                Map.of("safety:credential", DataScope.all()), Set.of()));
    }
}

package io.github.turbopro.ism.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.*;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyInputTest {
    @Test void invalidKeysNeverReachStorageIdGenerationOrBusinessAction() {
        var mapper = mock(TenantOperationMapper.class);
        var ids = mock(OperationIdGenerator.class);
        var sanitizer = mock(SensitivePayloadSanitizer.class);
        var service = new IdempotencyService(mapper, ids, sanitizer, new ObjectMapper());
        var executions = new AtomicInteger();
        try (var tenant = TenantContext.open(10, 11)) {
            for (String key : new String[]{null, "", " ", "\t", "x".repeat(129), "key\nvalue", "key\rvalue", "key" + (char) 0}) {
                assertThatThrownBy(() -> service.execute("INPUT_PROBE", key, "{}", Duration.ofHours(1), () -> {
                    executions.incrementAndGet(); return "must not execute";
                })).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
            }
        }
        assertThat(executions).hasValue(0);
        verifyNoInteractions(mapper, ids, sanitizer);
    }

    @Test void absentTenantContextCannotBeBypassedWithAnInvalidKey() {
        var mapper = mock(TenantOperationMapper.class);
        var service = new IdempotencyService(mapper, mock(OperationIdGenerator.class),
                mock(SensitivePayloadSanitizer.class), new ObjectMapper());
        assertThatThrownBy(() -> service.execute("INPUT_PROBE", "", "{}", Duration.ofHours(1), () -> "unused"))
                .isInstanceOf(TenantIsolationException.class);
        verifyNoInteractions(mapper);
    }
}

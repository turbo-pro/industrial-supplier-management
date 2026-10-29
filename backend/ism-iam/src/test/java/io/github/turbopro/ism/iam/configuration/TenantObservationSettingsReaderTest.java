package io.github.turbopro.ism.iam.configuration;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantObservationSettingsReaderTest {
    @Test void derivesTenantFromContextAndOnlyReadsTheFixedPolicyKey() {
        var mapper = mock(ConfigurationMapper.class);
        var reader = new TenantObservationSettingsReader(mapper);
        when(mapper.currentSettingValue(10, TenantObservationSettings.KEY)).thenReturn("30");
        when(mapper.currentSettingValue(20, TenantObservationSettings.KEY)).thenReturn("90");
        try (var context = TenantContext.open(10, 1)) { assertThat(reader.currentWatchPeriodDays()).isEqualTo("30"); }
        try (var context = TenantContext.open(20, 1)) { assertThat(reader.currentWatchPeriodDays()).isEqualTo("90"); }
        verify(mapper).currentSettingValue(10, TenantObservationSettings.KEY);
        verify(mapper).currentSettingValue(20, TenantObservationSettings.KEY);
        verifyNoMoreInteractions(mapper);
    }
    @Test void failsClosedWithoutTenantContext() {
        var mapper = mock(ConfigurationMapper.class);
        var reader = new TenantObservationSettingsReader(mapper);
        assertThatThrownBy(reader::currentWatchPeriodDays).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(mapper);
    }
}

package io.github.turbopro.ism.iam.configuration;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;

/** Keeps storage access and the current-read row lock inside the configuration owner. */
@Service
public class TenantObservationSettingsReader implements TenantObservationSettings {
    private final ConfigurationMapper mapper;
    public TenantObservationSettingsReader(ConfigurationMapper mapper) { this.mapper = mapper; }
    @Override public String currentWatchPeriodDays() {
        return mapper.currentSettingValue(TenantContext.require().tenantId(), KEY);
    }
}

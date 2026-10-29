package io.github.turbopro.ism.iam.configuration;

/** Narrow cross-module read contract. Tenant identity always comes from the server context. */
public interface TenantObservationSettings {
    String KEY = "restriction.watchPeriodDays";
    String currentWatchPeriodDays();
}

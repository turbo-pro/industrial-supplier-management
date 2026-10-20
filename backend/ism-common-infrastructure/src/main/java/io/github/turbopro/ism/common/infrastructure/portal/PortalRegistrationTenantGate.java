package io.github.turbopro.ism.common.infrastructure.portal;

/** Control-plane tenant lock must precede supplier/invitation/account locks. */
public interface PortalRegistrationTenantGate {
    void lockAvailable(long tenantId);
}

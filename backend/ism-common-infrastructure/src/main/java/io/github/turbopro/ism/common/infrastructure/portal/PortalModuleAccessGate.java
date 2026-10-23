package io.github.turbopro.ism.common.infrastructure.portal;

/** Control-plane entitlement check for a portal business capability, never a role grant. */
public interface PortalModuleAccessGate {
    void requireAvailable(String moduleCode);
}

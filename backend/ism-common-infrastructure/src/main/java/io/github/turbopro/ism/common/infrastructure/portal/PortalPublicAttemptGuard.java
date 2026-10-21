package io.github.turbopro.ism.common.infrastructure.portal;

/** Shared peer budget for anonymous portal registration and login. */
public interface PortalPublicAttemptGuard {
    void check(String transportPeer);
}

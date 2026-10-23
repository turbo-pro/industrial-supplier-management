package io.github.turbopro.ism.common.infrastructure.portal;

/** Joins the caller's write transaction; rechecks and locks the current portal session and its binding. */
public interface PortalWriteGuard { void lockCurrent(); }

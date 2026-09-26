package io.github.turbopro.ism.supplier;

import java.time.Duration;

/** Tenant policy is supplied by composition, without a supplier-to-IAM dependency. */
public interface ObservationPolicy {
    Duration period();
}

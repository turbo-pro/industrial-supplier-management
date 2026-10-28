package io.github.turbopro.ism.operation;

import java.time.Duration;
import java.util.UUID;

/** A distinct ownership token fences a previous execution even when a node name is reused. */
final class DispatchLease {
    private DispatchLease() {}

    static String owner(String nodeId, Duration duration) {
        validateDuration(duration);
        if (nodeId == null || nodeId.isBlank() || nodeId.length() > 63)
            throw new IllegalArgumentException("nodeId must contain 1 to 63 characters");
        return nodeId + ":" + UUID.randomUUID();
    }

    static void validateDuration(Duration duration) {
        if (duration == null || duration.compareTo(Duration.ofMillis(1)) < 0
                || duration.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Lease duration must be between 1 millisecond and 1 day");
    }
}

package io.github.turbopro.ism.bootstrap.schema;

import java.time.LocalDateTime;

public record SchemaMarker(Long id, String markerCode, LocalDateTime createdAt) {
}

package io.github.turbopro.ism.common.api;

import java.util.Objects;

public record FieldError(String field, String code, String message) {

    public FieldError {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}

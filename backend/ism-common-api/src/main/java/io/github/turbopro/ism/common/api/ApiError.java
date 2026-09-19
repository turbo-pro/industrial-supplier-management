package io.github.turbopro.ism.common.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ApiError(
        String code,
        String message,
        List<FieldError> fieldErrors,
        Map<String, Object> details,
        boolean retryable
) {

    public ApiError {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}

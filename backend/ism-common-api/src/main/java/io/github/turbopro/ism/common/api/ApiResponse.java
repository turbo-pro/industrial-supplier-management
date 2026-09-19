package io.github.turbopro.ism.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        String traceId,
        OffsetDateTime timestamp
) {

    public ApiResponse {
        if (success == (error != null)) {
            throw new IllegalArgumentException("A response must contain either data or error according to its success state");
        }
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static <T> ApiResponse<T> success(T data, String traceId, OffsetDateTime timestamp) {
        return new ApiResponse<>(true, data, null, traceId, timestamp);
    }

    public static ApiResponse<Void> failure(ApiError error, String traceId, OffsetDateTime timestamp) {
        return new ApiResponse<>(false, null, Objects.requireNonNull(error), traceId, timestamp);
    }
}

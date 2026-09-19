package io.github.turbopro.ism.common.infrastructure.web;

import io.github.turbopro.ism.common.api.ApiError;
import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.api.FieldError;
import io.github.turbopro.ism.common.api.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ApiResponseFactory {

    private final Clock clock;

    public ApiResponseFactory() {
        this(Clock.systemDefaultZone());
    }

    ApiResponseFactory(Clock clock) {
        this.clock = clock;
    }

    public <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, TraceIdContext.currentTraceId(), OffsetDateTime.now(clock));
    }

    public ApiResponse<Void> failure(ErrorCode code, String message) {
        return failure(code, message, List.of(), Map.of());
    }

    public ApiResponse<Void> failure(
            ErrorCode code,
            String message,
            List<FieldError> fieldErrors,
            Map<String, Object> details
    ) {
        ApiError error = new ApiError(code.code(), message, fieldErrors, details, code.retryable());
        return ApiResponse.failure(error, TraceIdContext.currentTraceId(), OffsetDateTime.now(clock));
    }
}

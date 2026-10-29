package io.github.turbopro.ism.common.infrastructure.web;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.api.FieldError;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.api.error.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    private final ApiResponseFactory responseFactory;

    public GlobalApiExceptionHandler(ApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        ErrorCode code = exception.errorCode();
        return ResponseEntity.status(code.httpStatus())
                .body(responseFactory.failure(code, exception.getMessage(), List.of(), exception.details()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleBindingException(Exception exception) {
        org.springframework.validation.BindingResult bindingResult = exception instanceof MethodArgumentNotValidException method
                ? method.getBindingResult()
                : ((BindException) exception).getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors().stream()
                .map(error -> new FieldError(
                        error.getField(),
                        error.getCode() == null ? "Invalid" : error.getCode(),
                        error.getDefaultMessage() == null ? "字段值不正确" : error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldError::field))
                .toList();
        return failure(CommonErrorCode.VALIDATION_FAILED, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        if (exception.getConstraintViolations().stream().anyMatch(violation ->
                java.util.stream.StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
                        .anyMatch(node -> node.getKind() == jakarta.validation.ElementKind.RETURN_VALUE)))
            return handleUnexpectedException(exception);
        List<FieldError> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldError(
                        violation.getPropertyPath().toString(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()))
                .sorted(Comparator.comparing(FieldError::field))
                .toList();
        return failure(CommonErrorCode.VALIDATION_FAILED, fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMvcMethodValidation(HandlerMethodValidationException exception) {
        if (exception.isForReturnValue()) return handleUnexpectedException(exception);
        List<FieldError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> {
                    String[] codes = error.getCodes();
                    String code = codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
                    // Do not serialize the rejected argument, framework exception text or method signature.
                    return new FieldError(parameterName(result.getMethodParameter()), code, "参数值不符合约束");
                }))
                .sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::code)).toList();
        return failure(CommonErrorCode.VALIDATION_FAILED, errors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException exception) {
        return required(exception.getHeaderName());
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingCookie(MissingRequestCookieException exception) {
        return required(exception.getCookieName());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException exception) {
        return required(exception.getRequestPartName());
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPathVariable(MissingPathVariableException exception) {
        // An annotation referring to a variable absent from its server route is not a client error.
        return handleUnexpectedException(exception);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestBinding() {
        return failure(CommonErrorCode.VALIDATION_FAILED, List.of());
    }

    private ResponseEntity<ApiResponse<Void>> required(String field) {
        return failure(CommonErrorCode.VALIDATION_FAILED, List.of(new FieldError(field, "Required", "缺少必要请求参数")));
    }

    private String parameterName(MethodParameter parameter) {
        var query = parameter.getParameterAnnotation(RequestParam.class);
        if (query != null && !query.name().isBlank()) return query.name();
        if (query != null && !query.value().isBlank()) return query.value();
        var path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null && !path.name().isBlank()) return path.name();
        if (path != null && !path.value().isBlank()) return path.value();
        var header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.name().isBlank()) return header.name();
        if (header != null && !header.value().isBlank()) return header.value();
        return parameter.getParameterName() == null ? "argument" + parameter.getParameterIndex() : parameter.getParameterName();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage() {
        return failure(CommonErrorCode.MALFORMED_REQUEST, List.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleRequestParameterException() {
        return failure(CommonErrorCode.VALIDATION_FAILED, List.of());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleRouteNotFound() {
        return failure(CommonErrorCode.NOT_FOUND, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported() {
        return failure(CommonErrorCode.METHOD_NOT_ALLOWED, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported() {
        return failure(CommonErrorCode.MEDIA_TYPE_NOT_SUPPORTED, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error(
                "Unhandled API exception type={}, traceId={}",
                exception.getClass().getName(),
                TraceIdContext.currentTraceId());
        return failure(CommonErrorCode.INTERNAL_ERROR, List.of());
    }

    private ResponseEntity<ApiResponse<Void>> failure(ErrorCode code, List<FieldError> fieldErrors) {
        ApiResponse<Void> response = responseFactory.failure(
                code,
                code.defaultMessage(),
                fieldErrors,
                java.util.Map.of());
        return ResponseEntity.status(HttpStatus.valueOf(code.httpStatus())).body(response);
    }
}

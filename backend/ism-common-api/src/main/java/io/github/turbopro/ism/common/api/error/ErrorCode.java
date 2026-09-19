package io.github.turbopro.ism.common.api.error;

public interface ErrorCode {

    String code();

    String defaultMessage();

    int httpStatus();

    boolean retryable();
}

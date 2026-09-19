package io.github.turbopro.ism.common.infrastructure.web;

import org.slf4j.MDC;

public final class TraceIdContext {

    private TraceIdContext() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        return traceId == null ? "unavailable" : traceId;
    }
}

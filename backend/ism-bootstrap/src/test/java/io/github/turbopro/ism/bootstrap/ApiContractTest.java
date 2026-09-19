package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.common.api.ApiResponse;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import io.github.turbopro.ism.common.infrastructure.web.GlobalApiExceptionHandler;
import io.github.turbopro.ism.common.infrastructure.web.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ApiContractTest.ContractController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@Import({
        ApiResponseFactory.class,
        GlobalApiExceptionHandler.class,
        TraceIdFilter.class,
        ApiContractTest.ContractController.class
})
class ApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnStableSuccessEnvelopeAndReuseTrustedTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/contract/success")
                        .header(TraceIdFilter.TRACE_ID_HEADER, "client-trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, "client-trace-123"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.value").value("ok"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.traceId").value("client-trace-123"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void shouldRejectInvalidInputWithFieldErrorsAndGeneratedTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/contract/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(TraceIdFilter.TRACE_ID_HEADER, "invalid trace id with spaces")
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        TraceIdFilter.TRACE_ID_HEADER,
                        matchesPattern("[a-f0-9]{32}")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.error.retryable").value(false))
                .andExpect(jsonPath("$.traceId", matchesPattern("[a-f0-9]{32}")));
    }

    @Test
    void shouldMapBusinessExceptionToDeclaredStatusAndCode() throws Exception {
        mockMvc.perform(get("/api/v1/contract/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMMON_STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("测试状态冲突"))
                .andExpect(jsonPath("$.error.details.action").value("submit"));
    }

    @Test
    void shouldNotExposeInternalExceptionDetails() throws Exception {
        mockMvc.perform(get("/api/v1/contract/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message", not(containsString("jdbc:mysql"))))
                .andExpect(jsonPath("$.error.details").isEmpty());
    }

    @Test
    void shouldReturnContractErrorForUnknownApiRoute() throws Exception {
        mockMvc.perform(get("/api/v1/not-existing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @RestController
    @RequestMapping("/api/v1/contract")
    public static class ContractController {

        private final ApiResponseFactory responseFactory;

        public ContractController(ApiResponseFactory responseFactory) {
            this.responseFactory = responseFactory;
        }

        @GetMapping("/success")
        ApiResponse<Map<String, String>> success() {
            return responseFactory.success(Map.of("value", "ok"));
        }

        @PostMapping("/validate")
        ApiResponse<ValidationParam> validate(@Valid @RequestBody ValidationParam param) {
            return responseFactory.success(param);
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ApiException(
                    CommonErrorCode.CONFLICT,
                    "测试状态冲突",
                    Map.of("action", "submit"));
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("jdbc:mysql://secret-host/db?password=secret");
        }
    }

    record ValidationParam(@NotBlank(message = "名称不能为空") String name) {
    }
}

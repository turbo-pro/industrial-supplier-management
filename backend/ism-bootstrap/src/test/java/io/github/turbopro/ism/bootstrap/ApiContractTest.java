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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import io.github.turbopro.ism.iam.auth.AuthService;
import io.github.turbopro.ism.iam.auth.JwtTokenService;
import io.github.turbopro.ism.iam.console.ConsoleAuthService;
import io.github.turbopro.ism.iam.console.ConsoleJwtTokenService;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationGrantLoader;

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
    @MockBean
    private io.github.turbopro.ism.iam.portal.PortalAuthService portalAuthService;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private AuthService authService;

    @MockBean
    private AuthorizationGrantLoader authorizationGrantLoader;

    @MockBean
    private ConsoleJwtTokenService consoleJwtTokenService;

    @MockBean
    private ConsoleAuthService consoleAuthService;

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

    @Test
    void missingRequiredHeaderReturnsInputErrorInsteadOfServerFailure() throws Exception {
        mockMvc.perform(get("/api/v1/contract/header").header(TraceIdFilter.TRACE_ID_HEADER, "missing-key-trace"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.traceId").value("missing-key-trace"));
    }

    @Test
    void nativeMvcMethodValidationReturnsInputError() throws Exception {
        mockMvc.perform(get("/api/v1/contract/query").param("pageSize", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("pageSize"));
    }

    @Test
    void missingCookieAndMultipartPartReturnStableRequiredFields() throws Exception {
        mockMvc.perform(get("/api/v1/contract/cookie"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("device"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/contract/part"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("attachment"));
    }

    @Test
    void validNativeParametersContinueToWorkAndOutOfRangePathDoesNotExecute() throws Exception {
        for (String size : java.util.List.of("1", "100")) {
            mockMvc.perform(get("/api/v1/contract/query").param("pageSize", size))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(Integer.parseInt(size)));
        }
        mockMvc.perform(get("/api/v1/contract/query").param("pageSize", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].code").value("Max"));
        mockMvc.perform(get("/api/v1/contract/positive/0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("id"));
    }

    @Test
    void invalidServerReturnValueRemainsInternalFailureWithoutRejectedData() throws Exception {
        mockMvc.perform(get("/api/v1/contract/invalid-result"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.error.details").isEmpty());
    }

    @Test
    void malformedBodyMissingQueryAndWrongTypeUseInputContractWithoutEchoingValues() throws Exception {
        mockMvc.perform(post("/api/v1/contract/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sensitive-private-value\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_MALFORMED_REQUEST"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("sensitive-private-value"))));
        mockMvc.perform(get("/api/v1/contract/query"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/contract/query").param("pageSize", "sensitive-private-value"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("sensitive-private-value"))));
    }

    @Test
    void methodAndContentTypeErrorsPreserveTraceAndDeclaredCodes() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/contract/success")
                        .header(TraceIdFilter.TRACE_ID_HEADER, "method-error-trace"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("COMMON_METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId").value("method-error-trace"));
        mockMvc.perform(post("/api/v1/contract/validate").contentType(MediaType.TEXT_PLAIN).content("private"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("COMMON_MEDIA_TYPE_NOT_SUPPORTED"));
    }

    @Test
    void routeDefinitionAndExecutableReturnConstraintsRemainServerErrors() throws Exception {
        for (String route : java.util.List.of("/api/v1/contract/broken-route/1", "/api/v1/contract/executable-result")) {
            mockMvc.perform(get(route))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("COMMON_INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.error.fieldErrors").isEmpty())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("expectedId"))));
        }
    }

    @RestController
    @RequestMapping("/api/v1/contract")
    public static class ContractController {

        private final ApiResponseFactory responseFactory;

        public ContractController(ApiResponseFactory responseFactory) {
            this.responseFactory = responseFactory;
        }

        @GetMapping("/header")
        ApiResponse<String> header(@org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String key) {
            return responseFactory.success("accepted");
        }

        @GetMapping("/query")
        ApiResponse<Integer> query(@org.springframework.web.bind.annotation.RequestParam("pageSize")
                                  @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {
            return responseFactory.success(size);
        }

        @GetMapping("/cookie")
        ApiResponse<String> cookie(@org.springframework.web.bind.annotation.CookieValue("device") String device) {
            return responseFactory.success("accepted");
        }
        @PostMapping("/part")
        ApiResponse<String> part(@org.springframework.web.bind.annotation.RequestPart("attachment") org.springframework.web.multipart.MultipartFile file) {
            return responseFactory.success("accepted");
        }
        @GetMapping("/positive/{id}")
        ApiResponse<Long> positive(@org.springframework.web.bind.annotation.PathVariable("id") @jakarta.validation.constraints.Positive long id) {
            return responseFactory.success(id);
        }
        @GetMapping("/invalid-result") @jakarta.validation.constraints.Positive
        int invalidResult() { return 0; }

        @GetMapping("/broken-route/{wrongId}")
        ApiResponse<Long> brokenRoute(@org.springframework.web.bind.annotation.PathVariable("expectedId") long id) {
            return responseFactory.success(id);
        }
        @GetMapping("/executable-result")
        void executableResult() throws Exception {
            try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                var sample = new InvalidReturn();
                var violations = factory.getValidator().forExecutables()
                        .validateReturnValue(sample, InvalidReturn.class.getDeclaredMethod("value"), -99);
                if (violations.isEmpty()) throw new IllegalStateException("Expected return constraint violation");
                throw new jakarta.validation.ConstraintViolationException(violations);
            }
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

    public static class InvalidReturn {
        @jakarta.validation.constraints.Positive public int value() { return -99; }
    }

    record ValidationParam(@NotBlank(message = "名称不能为空") String name) {
    }
}

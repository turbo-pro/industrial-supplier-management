package io.github.turbopro.ism.operation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final TenantOperationMapper mapper;
    private final OperationIdGenerator ids;
    private final SensitivePayloadSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    public IdempotencyService(TenantOperationMapper mapper, OperationIdGenerator ids,
                              SensitivePayloadSanitizer sanitizer, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.ids = ids;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String execute(String operationCode, String idempotencyKey, String requestBody,
                          Duration retention, Supplier<String> action) {
        TenantContext.Identity identity = TenantContext.require();
        String requestHash = sha256(requestBody);
        long recordId = ids.nextId();
        try {
            mapper.insertIdempotency(recordId, identity.tenantId(), identity.actorId(), operationCode,
                    idempotencyKey, requestHash, utcNow().plus(retention));
        } catch (DuplicateKeyException duplicate) {
            OperationModels.IdempotencyRecord existing = mapper.findIdempotency(
                    identity.tenantId(), identity.actorId(), operationCode, idempotencyKey);
            if (existing == null || !existing.requestHash().equals(requestHash)) {
                throw new ApiException(CommonErrorCode.CONFLICT, "幂等键已被不同请求使用");
            }
            if (!"COMPLETED".equals(existing.status())) {
                throw new ApiException(CommonErrorCode.CONFLICT, "相同请求正在处理中");
            }
            return decodeResponse(existing.responseBody());
        }

        String result = action.get();
        mapper.completeIdempotency(recordId, identity.tenantId(), 200, sanitizer.sanitize(result));
        return result;
    }

    private String decodeResponse(String json) {
        try {
            return objectMapper.readValue(json, String.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored idempotency response is invalid", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}

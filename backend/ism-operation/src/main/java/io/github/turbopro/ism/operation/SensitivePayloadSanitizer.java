package io.github.turbopro.ism.operation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
public class SensitivePayloadSanitizer {
    private static final Set<String> FORBIDDEN_PARTS = Set.of(
            "password", "secret", "token", "credential", "authorization", "bankaccount", "identitynumber");
    private final ObjectMapper objectMapper;

    public SensitivePayloadSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String sanitize(Object value) {
        JsonNode root = objectMapper.valueToTree(value == null ? java.util.Map.of() : value);
        sanitizeNode(root);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Payload cannot be serialized", exception);
        }
    }

    private void sanitizeNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<String> names = object.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (isForbidden(name)) {
                    object.put(name, "[REDACTED]");
                } else {
                    sanitizeNode(object.get(name));
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::sanitizeNode);
        }
    }

    private boolean isForbidden(String name) {
        String normalized = name.replace("_", "").toLowerCase(Locale.ROOT);
        return FORBIDDEN_PARTS.stream().anyMatch(normalized::contains);
    }
}

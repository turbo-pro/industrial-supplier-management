package io.github.turbopro.ism.operation;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class OperationIdGenerator {
    private final SecureRandom random = new SecureRandom();

    public long nextId() {
        return random.nextLong(Long.MAX_VALUE - 1) + 1;
    }
}

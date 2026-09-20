package io.github.turbopro.ism.common.infrastructure.authorization;

import org.springframework.stereotype.Component;

@Component
public class SensitiveDataMasker {
    private final PermissionGuard guard;

    public SensitiveDataMasker(PermissionGuard guard) {
        this.guard = guard;
    }

    public String protect(String fieldPermission, String value, Kind kind) {
        if (value == null || guard.canViewField(fieldPermission)) {
            return value;
        }
        return switch (kind) {
            case PHONE -> keepEnds(value, 3, 4);
            case EMAIL -> maskEmail(value);
            case IDENTITY -> keepEnds(value, 3, 4);
            case BANK_ACCOUNT -> keepEnds(value, 4, 4);
            case SECRET -> null;
        };
    }

    private String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return value.substring(0, 1) + "***" + value.substring(at);
    }

    private String keepEnds(String value, int prefix, int suffix) {
        if (value.length() <= prefix + suffix) {
            return "*".repeat(value.length());
        }
        return value.substring(0, prefix) + "*".repeat(value.length() - prefix - suffix)
                + value.substring(value.length() - suffix);
    }

    public enum Kind {
        PHONE,
        EMAIL,
        IDENTITY,
        BANK_ACCOUNT,
        SECRET
    }
}
